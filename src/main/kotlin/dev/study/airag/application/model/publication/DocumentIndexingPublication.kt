package dev.study.airag.application.model.publication

import dev.study.airag.domain.vo.DocumentId
import java.time.Instant
import java.util.UUID

/** 색인 요청 Domain Event를 외부 비동기 채널로 전달하기 위한 Application 출력 모델이다. */
data class DocumentIndexingPublication(
    val eventId: UUID,
    val correlationId: UUID,
    val occurredAt: Instant,
    val documentId: DocumentId,
    val documentVersion: Long,
)
