package dev.study.airag.application.dto.command

import java.util.UUID

/**
 * 접수된 이벤트가 가리키는 문서 버전을 색인한다.
 *
 * [eventId]는 같은 요청의 중복 완료를 방지하기 위한 Application 경계 식별자다.
 */
data class IndexKnowledgeDocumentCommand(
    val eventId: UUID,
    val documentId: String,
    val documentVersion: Long,
)
