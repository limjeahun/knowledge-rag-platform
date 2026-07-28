package dev.study.airag.adapter.`in`.web.ocr.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "OCR 추출 결과와 정답 기반 정확도 평가")
data class OcrEvaluationResponse(
    @field:Schema(nullable = true, example = "qwen3.6:27b")
    val requestedModel: String?,
    @field:Schema(example = "qwen3.6:27b")
    val executedModel: String,
    val fallbackUsed: Boolean,
    val extractedText: String,
    @field:Schema(description = "문자 오류율. 0이 가장 정확하며 삽입이 많으면 1보다 클 수 있습니다.")
    val characterErrorRate: Double,
    @field:Schema(description = "단어 오류율. 0이 가장 정확하며 삽입이 많으면 1보다 클 수 있습니다.")
    val wordErrorRate: Double,
    @field:Schema(description = "0에서 1 사이로 제한한 문자 정확도")
    val characterAccuracy: Double,
    @field:Schema(description = "0에서 1 사이로 제한한 단어 정확도")
    val wordAccuracy: Double,
    val normalizedExactMatch: Boolean,
    @field:Schema(description = "성공한 모델 호출에 걸린 시간")
    val modelDurationMillis: Long,
    @field:Schema(description = "실패한 폴백 시도를 포함한 전체 모델 호출 시간")
    val totalDurationMillis: Long,
    val attempts: List<OcrModelAttemptResponse>,
)
