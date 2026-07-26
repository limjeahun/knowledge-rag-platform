package dev.study.airag.application.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OcrTextMetricsTests {
    @Test
    fun `normalizes Unicode compatibility characters and whitespace before comparison`() {
        val metrics = OcrTextMetrics.evaluate("ＡＢＣ\r\n테스트", "ABC   테스트")

        assertEquals(0.0, metrics.characterErrorRate)
        assertEquals(0.0, metrics.wordErrorRate)
        assertEquals(1.0, metrics.characterAccuracy)
        assertEquals(1.0, metrics.wordAccuracy)
        assertTrue(metrics.normalizedExactMatch)
    }

    @Test
    fun `calculates Korean character and word deletion errors`() {
        val metrics = OcrTextMetrics.evaluate("안녕하세요 세계", "안녕하세요")

        assertEquals(0.375, metrics.characterErrorRate, 0.000_001)
        assertEquals(0.5, metrics.wordErrorRate, 0.000_001)
        assertEquals(0.625, metrics.characterAccuracy, 0.000_001)
        assertEquals(0.5, metrics.wordAccuracy, 0.000_001)
        assertFalse(metrics.normalizedExactMatch)
    }

    @Test
    fun `allows error rate above one while clamping accuracy to zero`() {
        val metrics = OcrTextMetrics.evaluate("가", "가나다")

        assertEquals(2.0, metrics.characterErrorRate, 0.000_001)
        assertEquals(0.0, metrics.characterAccuracy)
    }

    @Test
    fun `rejects ground truth that becomes empty after normalization`() {
        assertFailsWith<IllegalArgumentException> {
            OcrTextMetrics.evaluate(" \r\n\t", "텍스트")
        }
    }
}
