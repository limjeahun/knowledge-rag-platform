package dev.study.airag.adapter.`in`.scheduling

import dev.study.airag.application.port.`in`.DeliverPendingKnowledgeDocumentEventsUseCase
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 설정된 주기마다 저장된 문서 이벤트 전달 Use Case를 시작하고 운영 지표를 기록한다. */
@Component
class KnowledgeDocumentEventDeliveryScheduler(
    private val deliverEventsUseCase: DeliverPendingKnowledgeDocumentEventsUseCase,
    private val meterRegistry: MeterRegistry,
    @Value("\${app.knowledge.outbox.batch-size}") private val batchSize: Int,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.knowledge.outbox.fixed-delay}")
    fun deliverPendingEvents() {
        val result = deliverEventsUseCase.deliverPending(batchSize)
        result.deliveredEventIds.forEach {
            meterRegistry.counter("knowledge.outbox.delivered").increment()
        }
        result.failures.forEach { failure ->
            meterRegistry.counter("knowledge.outbox.delivery.failed").increment()
            logger.warn("Outbox 이벤트 전달에 실패했습니다. eventId={}: {}", failure.eventId, failure.reason)
        }
    }
}
