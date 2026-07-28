package dev.study.airag.application.knowledge.dto.result

import java.util.UUID

data class KnowledgeDocumentEventDeliveryFailure(
    val eventId: UUID,
    val reason: String,
)
