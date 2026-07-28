package dev.study.airag.adapter.`in`.web.ocr.mapper

import dev.study.airag.adapter.`in`.web.ocr.exception.UnsupportedOcrImageTypeException
import dev.study.airag.adapter.`in`.web.ocr.request.OcrEvaluationRequest
import dev.study.airag.application.ocr.dto.command.EvaluateOcrCommand
import org.springframework.http.MediaType
import org.springframework.web.multipart.support.MissingServletRequestPartException
import java.util.Locale

/** multipart OCR 평가 요청을 Application Command로 변환한다. */
fun OcrEvaluationRequest.toCommand(): EvaluateOcrCommand {
    val imagePart = image ?: throw MissingServletRequestPartException("image")
    require(!imagePart.isEmpty) { "OCR 평가 이미지는 비어 있을 수 없습니다." }

    return EvaluateOcrCommand(
        imageBytes = imagePart.bytes,
        mediaType = requireSupportedMediaType(imagePart.contentType),
        groundTruth = groundTruth,
        requestedModel = model?.trim(),
    )
}

private fun requireSupportedMediaType(contentType: String?): String {
    val normalizedMediaType =
        contentType
            ?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() }
            ?.let { "${it.type}/${it.subtype}".lowercase(Locale.ROOT) }
            ?: throw UnsupportedOcrImageTypeException(contentType)
    if (normalizedMediaType !in SUPPORTED_MEDIA_TYPES) {
        throw UnsupportedOcrImageTypeException(contentType)
    }
    return normalizedMediaType
}

private val SUPPORTED_MEDIA_TYPES =
    setOf(
        MediaType.IMAGE_PNG_VALUE,
        MediaType.IMAGE_JPEG_VALUE,
        "image/webp",
    )
