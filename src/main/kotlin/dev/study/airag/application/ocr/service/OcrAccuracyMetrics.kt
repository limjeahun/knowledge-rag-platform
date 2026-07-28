package dev.study.airag.application.ocr.service

internal data class OcrAccuracyMetrics(
    val characterErrorRate: Double,
    val wordErrorRate: Double,
    val characterAccuracy: Double,
    val wordAccuracy: Double,
    val normalizedExactMatch: Boolean,
)
