package dev.study.airag.adapter.`in`.messaging

import java.time.Instant
import java.util.UUID

/** Kafka에서 수신한 문서 버전별 색인 요청의 Inbound wire contract다. */
data class DocumentIndexingMessage(
    val eventId: UUID,
    val correlationId: UUID,
    val occurredAt: Instant,
    val documentId: String,
    val documentVersion: Long,
)
