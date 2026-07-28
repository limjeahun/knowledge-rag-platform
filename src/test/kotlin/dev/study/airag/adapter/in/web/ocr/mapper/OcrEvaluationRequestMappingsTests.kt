package dev.study.airag.adapter.`in`.web.ocr.mapper

import dev.study.airag.adapter.`in`.web.ocr.exception.UnsupportedOcrImageTypeException
import dev.study.airag.adapter.`in`.web.ocr.request.OcrEvaluationRequest
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.support.MissingServletRequestPartException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OcrEvaluationRequestMappingsTests {
    @Test
    fun `converts multipart request to application command`() {
        val imageBytes = byteArrayOf(1, 2, 3)
        val request =
            OcrEvaluationRequest(
                image =
                    MockMultipartFile(
                        "image",
                        "ocr.png",
                        "IMAGE/PNG; charset=UTF-8",
                        imageBytes,
                    ),
                groundTruth = "문서 번호 123",
                model = "  qwen3.6:27b  ",
            )

        val command = request.toCommand()

        assertContentEquals(imageBytes, command.imageBytes)
        assertEquals("image/png", command.mediaType)
        assertEquals("문서 번호 123", command.groundTruth)
        assertEquals("qwen3.6:27b", command.requestedModel)
    }

    @Test
    fun `rejects missing image part`() {
        val exception =
            assertFailsWith<MissingServletRequestPartException> {
                OcrEvaluationRequest(groundTruth = "text").toCommand()
            }

        assertEquals("image", exception.requestPartName)
    }

    @Test
    fun `rejects empty image`() {
        val request =
            OcrEvaluationRequest(
                image = MockMultipartFile("image", "empty.png", "image/png", byteArrayOf()),
                groundTruth = "text",
            )

        val exception =
            assertFailsWith<IllegalArgumentException> {
                request.toCommand()
            }

        assertEquals("OCR 평가 이미지는 비어 있을 수 없습니다.", exception.message)
    }

    @Test
    fun `rejects unsupported image media type`() {
        val request =
            OcrEvaluationRequest(
                image = MockMultipartFile("image", "ocr.txt", "text/plain", byteArrayOf(1)),
                groundTruth = "text",
            )

        assertFailsWith<UnsupportedOcrImageTypeException> {
            request.toCommand()
        }
    }
}
