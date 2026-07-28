package dev.study.airag.application.knowledge.service

import dev.study.airag.application.graph.port.out.KnowledgeGraphIndexPort
import dev.study.airag.application.knowledge.dto.result.KnowledgeDocumentEventDeliveryFailure
import dev.study.airag.application.knowledge.dto.result.KnowledgeDocumentEventDeliveryResult
import dev.study.airag.application.knowledge.outbox.OutboxEnvelope
import dev.study.airag.application.knowledge.port.`in`.DeliverPendingKnowledgeDocumentEventsUseCase
import dev.study.airag.application.knowledge.port.out.KnowledgeIndexPort
import dev.study.airag.application.knowledge.port.out.OutboxEventPort
import dev.study.airag.application.knowledge.port.out.PublishDocumentIndexingPort
import dev.study.airag.application.knowledge.port.out.dto.DocumentIndexingPublication
import dev.study.airag.domain.event.DocumentIndexingRequested
import dev.study.airag.domain.event.KnowledgeDocumentDeleted
import org.springframework.stereotype.Service
import java.time.Clock

/** 저장된 문서 이벤트를 유형에 맞는 외부 능력으로 전달하고 이벤트별 완료 여부를 기록한다. */
@Service
class DeliverPendingKnowledgeDocumentEventsService(
    private val outboxEventPort: OutboxEventPort,
    private val publishDocumentIndexingPort: PublishDocumentIndexingPort,
    private val knowledgeIndexPort: KnowledgeIndexPort,
    private val knowledgeGraphIndexPort: KnowledgeGraphIndexPort,
    private val clock: Clock,
) : DeliverPendingKnowledgeDocumentEventsUseCase {
    override fun deliverPending(limit: Int): KnowledgeDocumentEventDeliveryResult {
        require(limit > 0) { "Outbox 전달 건수 제한은 0보다 커야 합니다." }
        val delivered = mutableListOf<java.util.UUID>()
        val failures = mutableListOf<KnowledgeDocumentEventDeliveryFailure>()

        outboxEventPort.findPending(limit).forEach { envelope ->
            try {
                deliver(envelope)
                outboxEventPort.markDelivered(envelope.eventId, clock.instant())
                delivered += envelope.eventId
            } catch (exception: Exception) {
                val reason = exception.message ?: exception.javaClass.simpleName
                outboxEventPort.markFailed(envelope.eventId, reason)
                failures += KnowledgeDocumentEventDeliveryFailure(envelope.eventId, reason)
            }
        }
        return KnowledgeDocumentEventDeliveryResult(delivered, failures)
    }

    private fun deliver(envelope: OutboxEnvelope) {
        when (val event = envelope.event) {
            is DocumentIndexingRequested -> {
                publishDocumentIndexingPort.publish(
                    DocumentIndexingPublication(
                        eventId = envelope.eventId,
                        correlationId = envelope.correlationId,
                        occurredAt = event.occurredAt,
                        documentId = event.documentId,
                        documentVersion = event.documentVersion,
                    ),
                )
            }

            is KnowledgeDocumentDeleted -> {
                /*
                 * 두 저장소는 모두 삭제된 문서의 파생 인덱스다. 호출이 중간에 실패해도
                 * Outbox가 미완료로 남아 재시도하므로 각 remove는 멱등적으로 구현한다.
                 */
                knowledgeIndexPort.remove(event.documentId)
                knowledgeGraphIndexPort.remove(event.documentId)
            }
        }
    }
}
