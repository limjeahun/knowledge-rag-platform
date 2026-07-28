package dev.study.airag.application.knowledge.port.out

import java.util.UUID

/** 색인 처리 권한의 이벤트와 소유자를 함께 보존하는 불변 값이다. */
data class DocumentIndexingLease(
    val eventId: UUID,
    val ownerToken: String,
)
