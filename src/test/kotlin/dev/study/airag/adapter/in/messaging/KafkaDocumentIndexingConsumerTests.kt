package dev.study.airag.adapter.`in`.messaging

import dev.study.airag.application.dto.command.IndexKnowledgeDocumentCommand
import dev.study.airag.application.port.`in`.IndexKnowledgeDocumentUseCase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KafkaDocumentIndexingConsumerTests {
    @Test
    fun `consumer maps the wire message to an application command`() {
        var command: IndexKnowledgeDocumentCommand? = null
        val meterRegistry = SimpleMeterRegistry()
        val consumer =
            KafkaDocumentIndexingConsumer(
                IndexKnowledgeDocumentUseCase { command = it },
                meterRegistry,
            )
        val message = message()

        consumer.consume(message)

        assertEquals(
            IndexKnowledgeDocumentCommand(message.eventId, message.documentId, message.documentVersion),
            command,
        )
        assertEquals(1.0, meterRegistry.counter("knowledge.indexing.consumed").count())
    }

    @Test
    fun `consumer propagates indexing failure for Kafka redelivery`() {
        val meterRegistry = SimpleMeterRegistry()
        val consumer =
            KafkaDocumentIndexingConsumer(
                IndexKnowledgeDocumentUseCase { error("indexing failed") },
                meterRegistry,
            )

        assertFailsWith<IllegalStateException> { consumer.consume(message()) }
        assertEquals(0.0, meterRegistry.counter("knowledge.indexing.consumed").count())
    }

    private fun message() =
        DocumentIndexingMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-07-18T00:00:00Z"),
            UUID.randomUUID().toString(),
            1,
        )
}
