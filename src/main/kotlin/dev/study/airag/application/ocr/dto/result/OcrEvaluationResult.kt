package dev.study.airag.application.ocr.dto.result

data class OcrEvaluationResult(
    val requestedModel: String?,
    val executedModel: String,
    val fallbackUsed: Boolean,
    val extractedText: String,
    val characterErrorRate: Double,
    val wordErrorRate: Double,
    val characterAccuracy: Double,
    val wordAccuracy: Double,
    val normalizedExactMatch: Boolean,
    val modelDurationMillis: Long,
    val totalDurationMillis: Long,
    val attempts: List<OcrModelAttemptResult>,
)
