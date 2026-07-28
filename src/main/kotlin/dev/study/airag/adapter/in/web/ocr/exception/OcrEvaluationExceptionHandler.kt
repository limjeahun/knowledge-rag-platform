package dev.study.airag.adapter.`in`.web.ocr.exception

import dev.study.airag.adapter.`in`.web.common.response.ApiErrorResponse
import dev.study.airag.adapter.`in`.web.ocr.controller.OcrEvaluationController
import dev.study.airag.application.ocr.exception.OcrEvaluationUnavailableException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [OcrEvaluationController::class])
class OcrEvaluationExceptionHandler {
    @ExceptionHandler(OcrEvaluationUnavailableException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun ocrModelsUnavailable(exception: OcrEvaluationUnavailableException) =
        ApiErrorResponse(
            error = exception.message ?: "OCR 모델 호출에 실패했습니다.",
            errorCode = "OCR_MODELS_UNAVAILABLE",
        )

    @ExceptionHandler(UnsupportedOcrImageTypeException::class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    fun unsupportedOcrImage(exception: UnsupportedOcrImageTypeException) =
        ApiErrorResponse(
            error = exception.message ?: "지원하지 않는 OCR 이미지 형식입니다.",
            errorCode = "OCR_IMAGE_TYPE_UNSUPPORTED",
        )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    fun imageTooLarge(exception: MaxUploadSizeExceededException): ApiErrorResponse =
        ApiErrorResponse(
            error = "OCR 평가 이미지가 허용 크기를 초과했습니다.",
            errorCode = "OCR_IMAGE_TOO_LARGE",
        )

    @ExceptionHandler(MissingServletRequestPartException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun missingImage(exception: MissingServletRequestPartException) =
        ApiErrorResponse(
            error = "필수 multipart 항목이 누락되었습니다: ${exception.requestPartName}",
            errorCode = "OCR_IMAGE_REQUIRED",
        )
}
