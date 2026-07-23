package dev.study.airag.application.dto.result

import dev.study.airag.domain.model.DocumentIndexingStatus
import java.time.Instant

/**
 * 등록된 문서의 현재 색인 진행 상황이다.
 *
 * 원본 본문은 포함하지 않으며, 실패한 경우에만 [failureReason]이 제공된다.
 */
data class KnowledgeDocumentResult(
    val documentId: String,
    val title: String,
    val version: Long,
    val status: DocumentIndexingStatus,
    val failureReason: String?,
    val registeredAt: Instant,
    val indexedAt: Instant?,
)
