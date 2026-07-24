package dev.study.airag.application.dto.result

import java.util.UUID

/** 한 번의 Outbox 전달 주기에서 완료된 이벤트와 재시도할 실패를 반환한다. */
data class KnowledgeDocumentEventDeliveryResult(
    val deliveredEventIds: List<UUID>,
    val failures: List<KnowledgeDocumentEventDeliveryFailure>,
)
