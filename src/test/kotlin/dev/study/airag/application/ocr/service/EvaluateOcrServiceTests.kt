package dev.study.airag.application.ocr.service

import dev.study.airag.application.ocr.dto.command.EvaluateOcrCommand
import dev.study.airag.application.ocr.exception.OcrEvaluationUnavailableException
import dev.study.airag.application.ocr.exception.OcrExtractionException
import dev.study.airag.application.ocr.exception.OcrExtractionFailure
import dev.study.airag.application.ocr.port.out.ExtractTextFromImagePort
import dev.study.airag.application.ocr.port.out.dto.OcrExtractionResult
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvaluateOcrServiceTests {
    private val models = listOf(PRIMARY_MODEL, FALLBACK_MODEL)

    @Test
    fun `uses primary model when model is omitted`() {
        var receivedBytes = byteArrayOf()
        val service =
            EvaluateOcrService(
                ExtractTextFromImagePort { request ->
                    receivedBytes = request.imageBytes
                    OcrExtractionResult(request.model, "문서 번호 123", 80)
                },
                models,
            )

        val result = service.evaluate(command(groundTruth = "문서 번호 123"))

        assertNull(result.requestedModel)
        assertEquals(PRIMARY_MODEL, result.executedModel)
        assertFalse(result.fallbackUsed)
        assertTrue(result.normalizedExactMatch)
        assertEquals(80, result.modelDurationMillis)
        assertEquals(80, result.totalDurationMillis)
        assertEquals(1, result.attempts.size)
        assertContentEquals(IMAGE_BYTES, receivedBytes)
    }

    @Test
    fun `falls back in configured order and keeps every attempt duration`() {
        val calledModels = mutableListOf<String>()
        val service =
            EvaluateOcrService(
                ExtractTextFromImagePort { request ->
                    calledModels += request.model
                    if (request.model == PRIMARY_MODEL) {
                        throw OcrExtractionException(
                            model = request.model,
                            failure = OcrExtractionFailure.PROVIDER_CALL_FAILED,
                            durationMillis = 30,
                        )
                    }
                    OcrExtractionResult(request.model, "정답", 70)
                },
                models,
            )

        val result = service.evaluate(command(groundTruth = "정답"))

        assertEquals(models, calledModels)
        assertEquals(FALLBACK_MODEL, result.executedModel)
        assertTrue(result.fallbackUsed)
        assertEquals(70, result.modelDurationMillis)
        assertEquals(100, result.totalDurationMillis)
        assertEquals(OcrExtractionFailure.PROVIDER_CALL_FAILED, result.attempts.first().failure)
        assertNull(result.attempts.last().failure)
    }

    @Test
    fun `explicit model runs alone without fallback`() {
        val calledModels = mutableListOf<String>()
        val service =
            EvaluateOcrService(
                ExtractTextFromImagePort { request ->
                    calledModels += request.model
                    OcrExtractionResult(request.model, "결과", 15)
                },
                models,
            )

        val result = service.evaluate(command(groundTruth = "결과", requestedModel = FALLBACK_MODEL))

        assertEquals(listOf(FALLBACK_MODEL), calledModels)
        assertEquals(FALLBACK_MODEL, result.requestedModel)
        assertEquals(FALLBACK_MODEL, result.executedModel)
        assertFalse(result.fallbackUsed)
    }

    @Test
    fun `explicit failed model does not silently use another model`() {
        val calledModels = mutableListOf<String>()
        val service =
            EvaluateOcrService(
                ExtractTextFromImagePort { request ->
                    calledModels += request.model
                    throw OcrExtractionException(
                        model = request.model,
                        failure = OcrExtractionFailure.EMPTY_RESPONSE,
                        durationMillis = 20,
                    )
                },
                models,
            )

        val exception =
            assertFailsWith<OcrEvaluationUnavailableException> {
                service.evaluate(command(requestedModel = FALLBACK_MODEL))
            }

        assertEquals(listOf(FALLBACK_MODEL), calledModels)
        assertEquals(1, exception.attempts.size)
        assertEquals(OcrExtractionFailure.EMPTY_RESPONSE, exception.attempts.single().failure)
    }

    @Test
    fun `rejects model outside configured catalog before calling provider`() {
        var called = false
        val service =
            EvaluateOcrService(
                ExtractTextFromImagePort {
                    called = true
                    OcrExtractionResult(it.model, "결과", 1)
                },
                models,
            )

        val exception =
            assertFailsWith<IllegalArgumentException> {
                service.evaluate(command(requestedModel = "unknown:latest"))
            }

        assertEquals("지원하지 않는 OCR 모델입니다: unknown:latest", exception.message)
        assertFalse(called)
    }

    @Test
    fun `command protects image content from caller mutation`() {
        val source = byteArrayOf(1, 2, 3)
        val command = EvaluateOcrCommand(source, "image/png", "정답")

        source[0] = 9

        assertContentEquals(byteArrayOf(1, 2, 3), command.imageBytes)
    }

    private fun command(
        groundTruth: String = "정답",
        requestedModel: String? = null,
    ) = EvaluateOcrCommand(
        imageBytes = IMAGE_BYTES,
        mediaType = "image/png",
        groundTruth = groundTruth,
        requestedModel = requestedModel,
    )

    private companion object {
        const val PRIMARY_MODEL = "qwen3.6:27b"
        const val FALLBACK_MODEL = "gemma4:31b"
        val IMAGE_BYTES = byteArrayOf(1, 2, 3)
    }
}
