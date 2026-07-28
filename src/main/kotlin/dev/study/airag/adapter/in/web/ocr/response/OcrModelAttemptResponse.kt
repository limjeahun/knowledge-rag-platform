package dev.study.airag.adapter.`in`.web.ocr.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "단일 OCR 모델 호출 시도")
data class OcrModelAttemptResponse(
    @field:Schema(example = "qwen3.6:27b")
    val model: String,
    @field:Schema(allowableValues = ["SUCCEEDED", "FAILED"])
    val status: String,
    @field:Schema(example = "1842")
    val durationMillis: Long,
    @field:Schema(
        nullable = true,
        allowableValues = ["OUTPUT_TRUNCATED", "EMPTY_RESPONSE", "PROVIDER_CALL_FAILED"],
    )
    val failure: String?,
)
