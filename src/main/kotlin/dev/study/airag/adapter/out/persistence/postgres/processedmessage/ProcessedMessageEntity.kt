package dev.study.airag.adapter.out.persistence.postgres.processedmessage

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

/** Consumer가 업무 처리를 완료한 메시지를 영구 멱등성 기준으로 보존한다. */
@Entity
@Table(name = "processed_messages")
class ProcessedMessageEntity(
    @field:EmbeddedId
    var id: ProcessedMessageId,
    @field:Column(name = "processed_at", nullable = false)
    var processedAt: Instant,
)
