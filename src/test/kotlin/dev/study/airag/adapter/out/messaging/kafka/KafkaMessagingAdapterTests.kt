package dev.study.airag.adapter.out.messaging.kafka

import dev.study.airag.application.model.publication.DocumentIndexingPublication
import dev.study.airag.domain.vo.DocumentId
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFails

class KafkaMessagingAdapterTests {
    private val instant = Instant.parse("2026-07-20T00:00:00Z")

    @Test
    fun `Kafka publisher uses document id as key and preserves wire metadata`() {
        @Suppress("UNCHECKED_CAST")
        val kafkaTemplate = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<Any, Any>
        val event = event()
        val future = CompletableFuture.completedFuture<SendResult<Any, Any>>(null)
        Mockito
            .`when`(
                kafkaTemplate.send(
                    ArgumentMatchers.eq("indexing-topic"),
                    ArgumentMatchers.eq(event.documentId.toString()),
                    ArgumentMatchers.any(),
                ),
            ).thenReturn(future)

        KafkaDocumentIndexingPublisher(kafkaTemplate, "indexing-topic").publish(event)

        val messageCaptor = ArgumentCaptor.forClass(Any::class.java)
        Mockito.verify(kafkaTemplate).send(
            ArgumentMatchers.eq("indexing-topic"),
            ArgumentMatchers.eq(event.documentId.toString()),
            messageCaptor.capture(),
        )
        val message = messageCaptor.value as DocumentIndexingPublishMessage
        assertEquals(event.eventId, message.eventId)
        assertEquals(event.correlationId, message.correlationId)
        assertEquals(event.occurredAt, message.occurredAt)
        assertEquals(event.documentVersion, message.documentVersion)
    }

    @Test
    fun `Kafka publisher propagates broker failure to the outbox workflow`() {
        @Suppress("UNCHECKED_CAST")
        val kafkaTemplate = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<Any, Any>
        val failed = CompletableFuture<SendResult<Any, Any>>()
        failed.completeExceptionally(IllegalStateException("broker unavailable"))
        Mockito
            .`when`(
                kafkaTemplate.send(
                    ArgumentMatchers.eq("indexing-topic"),
                    ArgumentMatchers.anyString(),
                    ArgumentMatchers.any(),
                ),
            ).thenReturn(failed)

        assertFails { KafkaDocumentIndexingPublisher(kafkaTemplate, "indexing-topic").publish(event()) }
    }

    private fun event() =
        DocumentIndexingPublication(
            UUID.randomUUID(),
            UUID.randomUUID(),
            instant,
            DocumentId.newId(),
            1,
        )
}
