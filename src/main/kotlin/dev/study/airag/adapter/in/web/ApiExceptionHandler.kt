package dev.study.airag.adapter.`in`.web

import dev.study.airag.adapter.`in`.web.response.ApiErrorResponse
import dev.study.airag.application.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.domain.exception.InvalidDocumentStateTransitionException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/** Web 경계에서 발생한 업무·요청 예외를 일관된 HTTP 상태와 오류 본문으로 변환한다. */
@RestControllerAdvice
class ApiExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 요청한 지식 문서가 기준 저장소에 없음을 `404 Not Found`로 변환한다. */
    @ExceptionHandler(KnowledgeDocumentNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun knowledgeDocumentNotFound(exception: KnowledgeDocumentNotFoundException) =
        ApiErrorResponse(exception.message ?: "지식 문서를 찾을 수 없습니다.")

    /** 현재 문서 상태와 충돌하는 업무 요청을 `409 Conflict`로 변환한다. */
    @ExceptionHandler(InvalidDocumentStateTransitionException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun invalidDocumentState(exception: InvalidDocumentStateTransitionException) =
        ApiErrorResponse(exception.message ?: "문서 상태가 요청과 충돌합니다.")

    /** Application/Domain에서 거부한 잘못된 값을 `400 Bad Request`로 변환한다. */
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidArgument(exception: IllegalArgumentException) = ApiErrorResponse(exception.message ?: "잘못된 요청입니다.")

    /** 요청 본문의 Bean Validation 실패 필드를 안정적인 오류 메시지로 반환한다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidRequestBody(exception: MethodArgumentNotValidException): ApiErrorResponse {
        val message =
            exception.bindingResult.fieldErrors
                .sortedBy { it.field }
                .joinToString("; ") { "${it.field}: ${it.defaultMessage ?: "유효하지 않은 값입니다."}" }
                .ifBlank { "요청 본문 검증에 실패했습니다." }
        return ApiErrorResponse(message)
    }

    /** Controller 메서드 파라미터의 Bean Validation 실패를 `400 Bad Request`로 변환한다. */
    @ExceptionHandler(HandlerMethodValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidMethodParameter(exception: HandlerMethodValidationException): ApiErrorResponse {
        logger.debug("컨트롤러 메서드 파라미터 검증에 실패했습니다.", exception)
        return ApiErrorResponse("요청 파라미터 검증에 실패했습니다.")
    }

    /** Constraint Validation 실패 내용을 `400 Bad Request`로 변환한다. */
    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun constraintViolation(exception: ConstraintViolationException): ApiErrorResponse {
        val message =
            exception.constraintViolations
                .map { "${it.propertyPath}: ${it.message}" }
                .sorted()
                .joinToString("; ")
                .ifBlank { "요청 제약 조건 검증에 실패했습니다." }
        return ApiErrorResponse(message)
    }

    /** 읽을 수 없는 JSON 요청 본문을 `400 Bad Request`로 변환한다. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun malformedRequestBody(exception: HttpMessageNotReadableException): ApiErrorResponse {
        logger.debug("요청 본문 형식이 올바르지 않습니다.", exception)
        return ApiErrorResponse("JSON 요청 본문 형식이 올바르지 않습니다.")
    }

    /** 필수 Query Parameter 누락을 `400 Bad Request`로 변환한다. */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun missingRequestParameter(exception: MissingServletRequestParameterException) =
        ApiErrorResponse("필수 요청 파라미터가 누락되었습니다: ${exception.parameterName}")

    /** 요청 파라미터 타입 변환 실패를 `400 Bad Request`로 변환한다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun requestParameterTypeMismatch(exception: MethodArgumentTypeMismatchException): ApiErrorResponse {
        val expectedType = exception.requiredType?.simpleName ?: "필수 타입"
        return ApiErrorResponse("요청 파라미터 '${exception.name}'의 값은 $expectedType 형식이어야 합니다.")
    }

    /** 존재하지 않는 API 경로를 `404 Not Found`로 변환한다. */
    @ExceptionHandler(NoResourceFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun resourceNotFound(exception: NoResourceFoundException): ApiErrorResponse {
        logger.debug("요청한 리소스를 찾을 수 없습니다.", exception)
        return ApiErrorResponse("요청한 리소스를 찾을 수 없습니다.")
    }

    /** 지원하지 않는 HTTP 메서드를 `405 Method Not Allowed`로 변환한다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    fun methodNotAllowed(exception: HttpRequestMethodNotSupportedException) =
        ApiErrorResponse("지원하지 않는 HTTP 메서드입니다: ${exception.method}")

    /** 지원하지 않는 Content-Type을 `415 Unsupported Media Type`으로 변환한다. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    fun unsupportedMediaType(exception: HttpMediaTypeNotSupportedException): ApiErrorResponse {
        val mediaType = exception.contentType?.let { "${it.type}/${it.subtype}" } ?: "알 수 없음"
        return ApiErrorResponse("지원하지 않는 Content-Type입니다: $mediaType")
    }

    /** 예상하지 못한 서버 오류는 내부 정보를 노출하지 않고 `500 Internal Server Error`로 반환한다. */
    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun unexpectedFailure(exception: Exception): ApiErrorResponse {
        logger.error("예상하지 못한 REST API 오류가 발생했습니다.", exception)
        return ApiErrorResponse("서버 내부 오류가 발생했습니다.")
    }
}
