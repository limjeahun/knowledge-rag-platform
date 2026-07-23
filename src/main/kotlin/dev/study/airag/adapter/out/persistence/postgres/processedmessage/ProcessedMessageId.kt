package dev.study.airag.adapter.out.persistence.postgres.processedmessage

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

@Embeddable
data class ProcessedMessageId(
    @field:Column(name = "consumer_name", nullable = false, length = 200)
    var consumerName: String,
    @field:Column(name = "event_id", nullable = false)
    var eventId: UUID,
) : Serializable
