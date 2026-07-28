package dev.study.airag.adapter.`in`.web.ocr.controller

import dev.study.airag.application.dto.command.EvaluateOcrCommand
import dev.study.airag.application.dto.result.OcrEvaluationResult
import dev.study.airag.application.dto.result.OcrModelAttemptResult
import dev.study.airag.application.dto.result.OcrModelAttemptStatus
import dev.study.airag.application.exception.OcrEvaluationUnavailableException
import dev.study.airag.application.exception.OcrExtractionFailure
import dev.study.airag.application.port.`in`.EvaluateOcrUseCase
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

@WebMvcTest(OcrEvaluationController::class)
class OcrEvaluationControllerTests(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var evaluateOcrUseCase: EvaluateOcrUseCase

    @Test
    fun `multipart request is translated through OCR inbound port`() {
        var receivedCommand: EvaluateOcrCommand? = null
        Mockito
            .`when`(
                evaluateOcrUseCase.evaluate(
                    Mockito.any(EvaluateOcrCommand::class.java)
                        ?: EvaluateOcrCommand(byteArrayOf(0), "image/png", "fallback"),
                ),
            ).thenAnswer { invocation ->
                receivedCommand = invocation.getArgument(0)
                successfulResult()
            }

        mockMvc
            .perform(
                multipart("/api/ocr/evaluations")
                    .file(image())
                    .param("groundTruth", "문서 번호 123"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.requestedModel").doesNotExist())
            .andExpect(jsonPath("$.executedModel").value(PRIMARY_MODEL))
            .andExpect(jsonPath("$.fallbackUsed").value(false))
            .andExpect(jsonPath("$.characterErrorRate").value(0.0))
            .andExpect(jsonPath("$.attempts[0].status").value("SUCCEEDED"))

        assertContentEquals(IMAGE_BYTES, receivedCommand?.imageBytes)
        assertEquals("image/png", receivedCommand?.mediaType)
        assertEquals("문서 번호 123", receivedCommand?.groundTruth)
        assertNull(receivedCommand?.requestedModel)
    }

    @Test
    fun `explicit model is trimmed before application command`() {
        var receivedCommand: EvaluateOcrCommand? = null
        Mockito
            .`when`(
                evaluateOcrUseCase.evaluate(
                    Mockito.any(EvaluateOcrCommand::class.java)
                        ?: EvaluateOcrCommand(byteArrayOf(0), "image/png", "fallback"),
                ),
            ).thenAnswer { invocation ->
                receivedCommand = invocation.getArgument(0)
                successfulResult(requestedModel = FALLBACK_MODEL, executedModel = FALLBACK_MODEL)
            }

        mockMvc
            .perform(
                multipart("/api/ocr/evaluations")
                    .file(image())
                    .param("groundTruth", "문서 번호 123")
                    .param("model", "  $FALLBACK_MODEL  "),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.requestedModel").value(FALLBACK_MODEL))
            .andExpect(jsonPath("$.executedModel").value(FALLBACK_MODEL))

        assertEquals(FALLBACK_MODEL, receivedCommand?.requestedModel)
    }

    @Test
    fun `unsupported image media type returns 415 without calling use case`() {
        val textFile = MockMultipartFile("image", "ocr.txt", MediaType.TEXT_PLAIN_VALUE, "text".toByteArray())

        mockMvc
            .perform(
                multipart("/api/ocr/evaluations")
                    .file(textFile)
                    .param("groundTruth", "text"),
            ).andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.errorCode").value("OCR_IMAGE_TYPE_UNSUPPORTED"))

        Mockito.verifyNoInteractions(evaluateOcrUseCase)
    }

    @Test
    fun `empty image returns 400 without calling use case`() {
        val emptyImage = MockMultipartFile("image", "empty.png", MediaType.IMAGE_PNG_VALUE, byteArrayOf())

        mockMvc
            .perform(
                multipart("/api/ocr/evaluations")
                    .file(emptyImage)
                    .param("groundTruth", "text"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("OCR 평가 이미지는 비어 있을 수 없습니다."))

        Mockito.verifyNoInteractions(evaluateOcrUseCase)
    }

    @Test
    fun `missing image part returns stable 400 error`() {
        mockMvc
            .perform(
                multipart("/api/ocr/evaluations")
                    .param("groundTruth", "text"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("OCR_IMAGE_REQUIRED"))
    }

    @Test
    fun `missing ground truth is rejected through request DTO validation`() {
        mockMvc
            .perform(
                multipart("/api/ocr/evaluations")
                    .file(image()),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("groundTruth: 정답 텍스트는 비어 있을 수 없습니다."))

        Mockito.verifyNoInteractions(evaluateOcrUseCase)
    }

    @Test
    fun `unavailable OCR models return safe 502 error`() {
        val attempts =
            listOf(
                OcrModelAttemptResult(
                    model = PRIMARY_MODEL,
                    status = OcrModelAttemptStatus.FAILED,
                    durationMillis = 10,
                    failure = OcrExtractionFailure.PROVIDER_CALL_FAILED,
                ),
            )
        Mockito
            .`when`(
                evaluateOcrUseCase.evaluate(
                    Mockito.any(EvaluateOcrCommand::class.java)
                        ?: EvaluateOcrCommand(byteArrayOf(0), "image/png", "fallback"),
                ),
            ).thenThrow(OcrEvaluationUnavailableException(attempts))

        mockMvc
            .perform(
                multipart("/api/ocr/evaluations")
                    .file(image())
                    .param("groundTruth", "문서 번호 123"),
            ).andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.errorCode").value("OCR_MODELS_UNAVAILABLE"))
            .andExpect(jsonPath("$.error").value("사용 가능한 OCR 모델로 텍스트를 추출하지 못했습니다."))
    }

    private fun image() =
        MockMultipartFile(
            "image",
            "ocr.png",
            MediaType.IMAGE_PNG_VALUE,
            IMAGE_BYTES,
        )

    private fun successfulResult(
        requestedModel: String? = null,
        executedModel: String = PRIMARY_MODEL,
    ) = OcrEvaluationResult(
        requestedModel = requestedModel,
        executedModel = executedModel,
        fallbackUsed = false,
        extractedText = "문서 번호 123",
        characterErrorRate = 0.0,
        wordErrorRate = 0.0,
        characterAccuracy = 1.0,
        wordAccuracy = 1.0,
        normalizedExactMatch = true,
        modelDurationMillis = 80,
        totalDurationMillis = 80,
        attempts =
            listOf(
                OcrModelAttemptResult(
                    model = executedModel,
                    status = OcrModelAttemptStatus.SUCCEEDED,
                    durationMillis = 80,
                ),
            ),
    )

    private companion object {
        const val PRIMARY_MODEL = "qwen3.6:27b"
        const val FALLBACK_MODEL = "gemma4:31b"
        val IMAGE_BYTES = byteArrayOf(1, 2, 3)
    }
}
