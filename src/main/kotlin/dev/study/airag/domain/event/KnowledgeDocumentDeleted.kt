package dev.study.airag.domain.event

import dev.study.airag.domain.vo.DocumentId
import java.time.Instant

/** 지식 문서가 삭제 상태로 확정되어 더 이상 검색 대상으로 사용할 수 없다는 업무 사실이다. */
data class KnowledgeDocumentDeleted(
    override val occurredAt: Instant,
    override val documentId: DocumentId,
    override val documentVersion: Long,
) : KnowledgeDocumentEvent
