package dev.study.airag.application.model.outbox

import dev.study.airag.domain.event.KnowledgeDocumentEvent
import java.util.UUID

/** Domain Event에 영구 Outbox 식별자와 요청 추적 식별자를 결합한 전달 단위다. */
data class OutboxEnvelope(
    val eventId: UUID,
    val correlationId: UUID,
    val event: KnowledgeDocumentEvent,
)
