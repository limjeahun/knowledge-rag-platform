package dev.study.airag.adapter.out.persistence.postgres.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** 발행 대기 중인 문서 이벤트와 전달 시도 결과를 영구 보존한다. */
@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @field:Id
    @field:Column(name = "event_id", nullable = false, updatable = false)
    var eventId: UUID,
    @field:Column(name = "event_type", nullable = false, updatable = false, length = 120)
    var eventType: String,
    @field:Column(name = "aggregate_id", nullable = false, updatable = false)
    var aggregateId: UUID,
    @field:Column(name = "correlation_id", nullable = false, updatable = false)
    var correlationId: UUID,
    @field:Column(name = "document_version", nullable = false, updatable = false)
    var documentVersion: Long,
    @field:Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant,
    @field:Column(name = "published_at")
    var publishedAt: Instant? = null,
    @field:Column(name = "publish_attempts", nullable = false)
    var publishAttempts: Int = 0,
    @field:Column(name = "last_error", length = 2_000)
    var lastError: String? = null,
)
