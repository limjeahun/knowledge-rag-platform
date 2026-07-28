package dev.study.airag.adapter.out.persistence.postgres.outbox

import dev.study.airag.application.knowledge.outbox.OutboxEnvelope
import dev.study.airag.domain.event.DocumentIndexingRequested
import dev.study.airag.domain.event.KnowledgeDocumentDeleted
import dev.study.airag.domain.vo.DocumentId
import org.springframework.stereotype.Component

/** Outbox 영속 모델과 서비스 내부 이벤트 봉투를 순수하게 변환한다. */
@Component
class OutboxEventMapper {
    fun toEntity(envelope: OutboxEnvelope): OutboxEventEntity {
        val event = envelope.event
        return OutboxEventEntity(
            eventId = envelope.eventId,
            eventType =
                when (event) {
                    is DocumentIndexingRequested -> INDEXING_REQUESTED
                    is KnowledgeDocumentDeleted -> DOCUMENT_DELETED
                },
            aggregateId = event.documentId.value,
            correlationId = envelope.correlationId,
            documentVersion = event.documentVersion,
            occurredAt = event.occurredAt,
        )
    }

    fun toEnvelope(entity: OutboxEventEntity): OutboxEnvelope {
        val documentId = DocumentId(entity.aggregateId)
        val event =
            when (entity.eventType) {
                INDEXING_REQUESTED -> {
                    DocumentIndexingRequested(entity.occurredAt, documentId, entity.documentVersion)
                }

                DOCUMENT_DELETED -> {
                    KnowledgeDocumentDeleted(entity.occurredAt, documentId, entity.documentVersion)
                }

                else -> {
                    error("지원하지 않는 지식 문서 이벤트 유형입니다: ${entity.eventType}")
                }
            }
        return OutboxEnvelope(entity.eventId, entity.correlationId, event)
    }

    private companion object {
        const val INDEXING_REQUESTED = "knowledge-document.indexing-requested.v1"
        const val DOCUMENT_DELETED = "knowledge-document.deleted.v1"
    }
}
