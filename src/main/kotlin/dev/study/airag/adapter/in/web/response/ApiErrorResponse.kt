package dev.study.airag.adapter.`in`.web.response

import io.swagger.v3.oas.annotations.media.Schema

/** 요청을 완료하지 못한 이유를 반환한다. */
data class ApiErrorResponse(
    @field:Schema(description = "요청을 완료하지 못한 이유", example = "지식 문서를 찾을 수 없습니다: document-id")
    val error: String,
)
