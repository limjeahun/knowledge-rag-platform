package dev.study.airag.messaging

import dev.study.airag.adapter.`in`.messaging.DocumentIndexingMessage
import dev.study.airag.adapter.out.messaging.kafka.DocumentIndexingPublishMessage
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class DocumentIndexingMessageCompatibilityTests {
    @Test
    fun `outbound wire JSON is compatible with the inbound message contract`() {
        val outbound =
            DocumentIndexingPublishMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-07-20T00:00:00Z"),
                UUID.randomUUID().toString(),
                3,
            )
        val serializer = JacksonJsonSerializer<DocumentIndexingPublishMessage>().noTypeInfo()
        val deserializer = JacksonJsonDeserializer(DocumentIndexingMessage::class.java, false)

        val inbound =
            requireNotNull(
                deserializer.deserialize("indexing-topic", serializer.serialize("indexing-topic", outbound)),
            )

        assertEquals(outbound.eventId, inbound.eventId)
        assertEquals(outbound.correlationId, inbound.correlationId)
        assertEquals(outbound.occurredAt, inbound.occurredAt)
        assertEquals(outbound.documentId, inbound.documentId)
        assertEquals(outbound.documentVersion, inbound.documentVersion)
    }
}
