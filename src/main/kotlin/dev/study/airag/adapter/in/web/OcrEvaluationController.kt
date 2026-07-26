package dev.study.airag.adapter.`in`.web

import dev.study.airag.adapter.`in`.web.mapper.toResponse
import dev.study.airag.adapter.`in`.web.response.OcrEvaluationResponse
import dev.study.airag.application.dto.command.EvaluateOcrCommand
import dev.study.airag.application.port.`in`.EvaluateOcrUseCase
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Validated
@RestController
@RequestMapping("/api/ocr/evaluations")
class OcrEvaluationController(
    private val evaluateOcrUseCase: EvaluateOcrUseCase,
) : OcrEvaluationSpec {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun evaluate(
        @RequestPart("image") image: MultipartFile,
        @RequestParam("groundTruth") groundTruth: String,
        @RequestParam("model", required = false) model: String?,
    ): OcrEvaluationResponse {
        require(!image.isEmpty) { "OCR 평가 이미지는 비어 있을 수 없습니다." }
        val mediaType = requireSupportedMediaType(image.contentType)
        return evaluateOcrUseCase
            .evaluate(
                EvaluateOcrCommand(
                    imageBytes = image.bytes,
                    mediaType = mediaType,
                    groundTruth = groundTruth,
                    requestedModel = model?.trim(),
                ),
            ).toResponse()
    }

    private fun requireSupportedMediaType(contentType: String?): String {
        val normalizedMediaType =
            contentType
                ?.runCatching(MediaType::parseMediaType)
                ?.getOrNull()
                ?.let { "${it.type}/${it.subtype}".lowercase() }
                ?: throw UnsupportedOcrImageTypeException(contentType)
        if (normalizedMediaType !in SUPPORTED_MEDIA_TYPES) {
            throw UnsupportedOcrImageTypeException(contentType)
        }
        return normalizedMediaType
    }

    private companion object {
        val SUPPORTED_MEDIA_TYPES =
            setOf(
                MediaType.IMAGE_PNG_VALUE,
                MediaType.IMAGE_JPEG_VALUE,
                "image/webp",
            )
    }
}
