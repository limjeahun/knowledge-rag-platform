package dev.study.airag.domain.event

import dev.study.airag.domain.vo.DocumentId
import java.time.Instant

/** 지식 문서 Aggregate의 상태 변경으로 발생한 순수한 업무 사실이다. */
sealed interface KnowledgeDocumentEvent {
    val occurredAt: Instant
    val documentId: DocumentId
    val documentVersion: Long
}
