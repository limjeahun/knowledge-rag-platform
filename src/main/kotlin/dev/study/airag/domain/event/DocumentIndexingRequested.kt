package dev.study.airag.domain.event

import dev.study.airag.domain.vo.DocumentId
import java.time.Instant

/**
 * 등록 또는 재시도된 문서의 특정 버전을 검색 가능하게 만들도록 요청한 업무 사실이다.
 *
 * 요청한 문서 버전이 현재 버전과 다르면 오래된 요청으로 처리한다.
 */
data class DocumentIndexingRequested(
    override val occurredAt: Instant,
    override val documentId: DocumentId,
    override val documentVersion: Long,
) : KnowledgeDocumentEvent
