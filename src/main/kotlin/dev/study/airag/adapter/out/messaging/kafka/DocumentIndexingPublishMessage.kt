package dev.study.airag.adapter.out.messaging.kafka

import java.time.Instant
import java.util.UUID

/** Kafka로 발행할 문서 버전별 색인 요청의 Outbound wire contract다. */
data class DocumentIndexingPublishMessage(
    val eventId: UUID,
    val correlationId: UUID,
    val occurredAt: Instant,
    val documentId: String,
    val documentVersion: Long,
)
