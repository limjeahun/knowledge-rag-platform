package dev.study.airag.adapter.`in`.web.response

import io.swagger.v3.oas.annotations.media.Schema

/** 문서 등록 또는 재색인이 접수된 직후의 식별자와 색인 상태다. */
data class RegisteredKnowledgeDocumentResponse(
    @field:Schema(description = "등록된 문서의 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    val documentId: String,
    @field:Schema(description = "현재 색인 상태", example = "PENDING", allowableValues = ["PENDING"])
    val status: String,
)
