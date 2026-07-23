package dev.study.airag.adapter.`in`.scheduling

import dev.study.airag.application.dto.result.KnowledgeDocumentEventDeliveryFailure
import dev.study.airag.application.dto.result.KnowledgeDocumentEventDeliveryResult
import dev.study.airag.application.port.`in`.DeliverPendingKnowledgeDocumentEventsUseCase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class KnowledgeDocumentEventDeliverySchedulerTests {
    @Test
    fun `scheduler invokes the inbound use case and records returned outcomes`() {
        val deliveredId = UUID.randomUUID()
        val failedId = UUID.randomUUID()
        val metrics = SimpleMeterRegistry()
        var requestedLimit: Int? = null
        val scheduler =
            KnowledgeDocumentEventDeliveryScheduler(
                DeliverPendingKnowledgeDocumentEventsUseCase { limit ->
                    requestedLimit = limit
                    KnowledgeDocumentEventDeliveryResult(
                        listOf(deliveredId),
                        listOf(KnowledgeDocumentEventDeliveryFailure(failedId, "unavailable")),
                    )
                },
                metrics,
                25,
            )

        scheduler.deliverPendingEvents()

        assertEquals(25, requestedLimit)
        assertEquals(1.0, metrics.counter("knowledge.outbox.delivered").count())
        assertEquals(1.0, metrics.counter("knowledge.outbox.delivery.failed").count())
    }
}
