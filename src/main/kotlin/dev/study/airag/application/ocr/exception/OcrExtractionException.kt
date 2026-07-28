package dev.study.airag.application.ocr.exception

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
