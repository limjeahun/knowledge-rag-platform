package dev.study.airag.application.ocr.dto.result

import dev.study.airag.application.ocr.exception.OcrExtractionFailure

data class OcrModelAttemptResult(
    val model: String,
    val status: OcrModelAttemptStatus,
    val durationMillis: Long,
    val failure: OcrExtractionFailure? = null,
) {
    init {
        require(model.isNotBlank()) { "OCR 시도 모델명은 비어 있을 수 없습니다." }
        require(durationMillis >= 0) { "OCR 시도 시간은 음수일 수 없습니다." }
        require((status == OcrModelAttemptStatus.FAILED) == (failure != null)) {
            "실패한 OCR 시도에만 실패 원인이 있어야 합니다."
        }
    }
}
