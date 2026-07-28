package dev.study.airag.application.knowledge.port.`in`

import dev.study.airag.application.knowledge.dto.result.KnowledgeDocumentEventDeliveryResult

fun interface DeliverPendingKnowledgeDocumentEventsUseCase {
    /** 오래된 미완료 이벤트를 최대 [limit]개 전달하고 이벤트별 결과를 반환한다. */
    fun deliverPending(limit: Int): KnowledgeDocumentEventDeliveryResult
}
