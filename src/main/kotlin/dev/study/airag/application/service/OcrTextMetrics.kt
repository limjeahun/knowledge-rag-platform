package dev.study.airag.application.service

import java.text.Normalizer

internal data class OcrAccuracyMetrics(
    val characterErrorRate: Double,
    val wordErrorRate: Double,
    val characterAccuracy: Double,
    val wordAccuracy: Double,
    val normalizedExactMatch: Boolean,
)

internal object OcrTextMetrics {
    private val whitespace = Regex("\\s+")

    fun evaluate(
        groundTruth: String,
        extractedText: String,
    ): OcrAccuracyMetrics {
        val normalizedGroundTruth = normalize(groundTruth)
        val normalizedExtractedText = normalize(extractedText)
        require(normalizedGroundTruth.isNotEmpty()) {
            "정규화한 OCR 평가 정답 텍스트는 비어 있을 수 없습니다."
        }

        val expectedCharacters = normalizedGroundTruth.codePoints().toArray().toList()
        val actualCharacters = normalizedExtractedText.codePoints().toArray().toList()
        val expectedWords = normalizedGroundTruth.split(" ")
        val actualWords = normalizedExtractedText.takeIf { it.isNotEmpty() }?.split(" ") ?: emptyList()

        val characterErrorRate =
            levenshteinDistance(expectedCharacters, actualCharacters).toDouble() / expectedCharacters.size
        val wordErrorRate = levenshteinDistance(expectedWords, actualWords).toDouble() / expectedWords.size

        return OcrAccuracyMetrics(
            characterErrorRate = characterErrorRate,
            wordErrorRate = wordErrorRate,
            characterAccuracy = (1.0 - characterErrorRate).coerceIn(0.0, 1.0),
            wordAccuracy = (1.0 - wordErrorRate).coerceIn(0.0, 1.0),
            normalizedExactMatch = normalizedGroundTruth == normalizedExtractedText,
        )
    }

    private fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFKC)
            .replace(whitespace, " ")
            .trim()

    private fun <T> levenshteinDistance(
        expected: List<T>,
        actual: List<T>,
    ): Int {
        var previous = IntArray(actual.size + 1) { it }
        var current = IntArray(actual.size + 1)

        expected.forEachIndexed { expectedIndex, expectedValue ->
            current[0] = expectedIndex + 1
            actual.forEachIndexed { actualIndex, actualValue ->
                val substitutionCost = if (expectedValue == actualValue) 0 else 1
                current[actualIndex + 1] =
                    minOf(
                        current[actualIndex] + 1,
                        previous[actualIndex + 1] + 1,
                        previous[actualIndex] + substitutionCost,
                    )
            }
            val swap = previous
            previous = current
            current = swap
        }

        return previous[actual.size]
    }
}
