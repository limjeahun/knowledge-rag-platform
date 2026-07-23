package dev.study.airag.domain.model

import dev.study.airag.domain.event.DocumentIndexingRequested
import dev.study.airag.domain.event.KnowledgeDocumentDeleted
import dev.study.airag.domain.event.KnowledgeDocumentEvent
import dev.study.airag.domain.exception.InvalidDocumentStateTransitionException
import dev.study.airag.domain.vo.DocumentId
import java.time.Instant

/**
 * 등록된 원본 지식과 검색 가능 상태를 함께 관리한다.
 *
 * 색인 상태는 정해진 업무 행위를 통해서만 변경되며, 완료 시각과 실패 사유는 그 상태와 함께 갱신된다.
 */
class KnowledgeDocument private constructor(
    val id: DocumentId,
    val title: String,
    val originalContent: String,
    val metadata: Map<String, String>,
    val version: Long,
    status: DocumentIndexingStatus,
    failureReason: String?,
    val registeredAt: Instant,
    indexedAt: Instant?,
    updatedAt: Instant,
) {
    private val recordedEvents = mutableListOf<KnowledgeDocumentEvent>()

    var status: DocumentIndexingStatus = status
        private set
    var failureReason: String? = failureReason
        private set
    var indexedAt: Instant? = indexedAt
        private set
    var updatedAt: Instant = updatedAt
        private set

    /**
     * 접수되었거나 이전 시도에 실패한 문서의 색인을 시작한다.
     *
     * [DocumentIndexingStatus.PENDING] 또는 [DocumentIndexingStatus.FAILED] 상태에서만 시작할 수 있다.
     */
    fun startIndexing(now: Instant) {
        if (status != DocumentIndexingStatus.PENDING && status != DocumentIndexingStatus.FAILED) {
            throw InvalidDocumentStateTransitionException(
                "PENDING 또는 FAILED 상태의 문서만 색인을 시작할 수 있습니다. 현재 상태: $status",
            )
        }
        status = DocumentIndexingStatus.INDEXING
        failureReason = null
        updatedAt = now
    }

    /**
     * 현재 문서 버전이 검색 가능해졌음을 확정하고 완료 시각을 기록한다.
     *
     * [DocumentIndexingStatus.INDEXING] 상태에서만 완료할 수 있다.
     */
    fun completeIndexing(now: Instant) {
        ensureStatus(DocumentIndexingStatus.INDEXING, "색인 완료")
        status = DocumentIndexingStatus.INDEXED
        failureReason = null
        indexedAt = now
        updatedAt = now
    }

    /**
     * 색인 중 발생한 오류를 기록하여 문서를 재시도 가능한 상태로 전환한다.
     *
     * 색인 중인 문서에만 적용할 수 있으며 실패 사유는 공백일 수 없고 최대 2,000자로 보존한다.
     */
    fun failIndexing(
        reason: String,
        now: Instant,
    ) {
        ensureStatus(DocumentIndexingStatus.INDEXING, "색인 실패 처리")
        require(reason.isNotBlank()) { "색인 실패 사유는 비어 있을 수 없습니다." }
        status = DocumentIndexingStatus.FAILED
        failureReason = reason.take(2_000)
        updatedAt = now
    }

    /** 색인 도중 발생한 실패만 기록하며 이미 다른 상태로 확정된 문서는 변경하지 않는다. */
    fun failIndexingIfInProgress(
        reason: String,
        now: Instant,
    ): Boolean {
        if (status != DocumentIndexingStatus.INDEXING) return false
        failIndexing(reason, now)
        return true
    }

    /** 요청 버전과 현재 상태를 함께 판단해 색인 실행 여부를 결정한다. */
    fun decideIndexing(requestedVersion: Long): DocumentIndexingDecision =
        when {
            status == DocumentIndexingStatus.DELETED -> DocumentIndexingDecision.DOCUMENT_DELETED
            version != requestedVersion -> DocumentIndexingDecision.VERSION_MISMATCH
            status == DocumentIndexingStatus.INDEXED -> DocumentIndexingDecision.ALREADY_INDEXED
            else -> DocumentIndexingDecision.INDEX
        }

    /**
     * 실패한 문서의 재색인을 접수할 수 있도록 대기 상태로 되돌린다.
     *
     * [DocumentIndexingStatus.FAILED] 상태에서만 재시도할 수 있다.
     */
    fun requestRetry(now: Instant) {
        ensureStatus(DocumentIndexingStatus.FAILED, "색인 재시도")
        status = DocumentIndexingStatus.PENDING
        failureReason = null
        updatedAt = now
        record(DocumentIndexingRequested(now, id, version))
    }

    /**
     * 문서를 더 이상 검색하거나 색인할 수 없는 상태로 전환한다.
     *
     * 이미 삭제된 문서에 다시 호출해도 상태를 변경하지 않는 멱등 연산이다.
     */
    fun markDeleted(now: Instant): Boolean {
        if (status == DocumentIndexingStatus.DELETED) return false
        status = DocumentIndexingStatus.DELETED
        updatedAt = now
        record(KnowledgeDocumentDeleted(now, id, version))
        return true
    }

    /** 지금까지 기록한 Domain Event의 스냅샷을 반환하고 Aggregate 내부 대기 목록을 비운다. */
    fun pullDomainEvents(): List<KnowledgeDocumentEvent> = recordedEvents.toList().also { recordedEvents.clear() }

    private fun record(event: KnowledgeDocumentEvent) {
        recordedEvents += event
    }

    private fun ensureStatus(
        expected: DocumentIndexingStatus,
        operation: String,
    ) {
        if (status != expected) {
            throw InvalidDocumentStateTransitionException(
                "$operation 작업은 문서 상태가 ${expected}일 때만 수행할 수 있습니다. 현재 상태: $status",
            )
        }
    }

    companion object {
        /**
         * 새 원본 문서를 버전 1과 [DocumentIndexingStatus.PENDING] 상태로 등록한다.
         *
         * 제목과 본문은 공백일 수 없으며 제목의 앞뒤 공백은 제거한다.
         */
        fun register(
            id: DocumentId,
            title: String,
            originalContent: String,
            metadata: Map<String, String>,
            now: Instant,
        ): KnowledgeDocument {
            require(title.isNotBlank()) { "문서 제목은 비어 있을 수 없습니다." }
            require(originalContent.isNotBlank()) { "문서 본문은 비어 있을 수 없습니다." }
            val document =
                KnowledgeDocument(
                    id = id,
                    title = title.trim(),
                    originalContent = originalContent,
                    metadata = metadata.toMap(),
                    version = 1,
                    status = DocumentIndexingStatus.PENDING,
                    failureReason = null,
                    registeredAt = now,
                    indexedAt = null,
                    updatedAt = now,
                )
            document.record(DocumentIndexingRequested(now, document.id, document.version))
            return document
        }

        /** 이미 확정된 문서 이력을 신규 등록이나 상태 전이 없이 복원한다. */
        fun reconstitute(
            id: DocumentId,
            title: String,
            originalContent: String,
            metadata: Map<String, String>,
            version: Long,
            status: DocumentIndexingStatus,
            failureReason: String?,
            registeredAt: Instant,
            indexedAt: Instant?,
            updatedAt: Instant,
        ): KnowledgeDocument =
            KnowledgeDocument(
                id,
                title,
                originalContent,
                metadata.toMap(),
                version,
                status,
                failureReason,
                registeredAt,
                indexedAt,
                updatedAt,
            )
    }
}
