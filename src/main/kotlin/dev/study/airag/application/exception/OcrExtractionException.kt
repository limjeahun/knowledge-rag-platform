package dev.study.airag.application.exception

enum class OcrExtractionFailure(
    val message: String,
) {
    OUTPUT_TRUNCATED("OCR 모델이 응답 길이 한도 내에서 텍스트 추출을 완료하지 못했습니다."),
    EMPTY_RESPONSE("OCR 모델이 비어 있는 텍스트를 반환했습니다."),
    PROVIDER_CALL_FAILED("OCR 모델 공급자 호출에 실패했습니다."),
}

class OcrExtractionException(
    val model: String,
    val failure: OcrExtractionFailure,
    val durationMillis: Long,
    cause: Throwable? = null,
) : RuntimeException(failure.message, cause) {
    init {
        require(model.isNotBlank()) { "실패한 OCR 모델명은 비어 있을 수 없습니다." }
        require(durationMillis >= 0) { "실패한 OCR 실행 시간은 음수일 수 없습니다." }
    }
}
