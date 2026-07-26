package dev.study.airag.application.port.out.dto

data class OcrExtractionResult(
    val model: String,
    val extractedText: String,
    val durationMillis: Long,
) {
    init {
        require(model.isNotBlank()) { "OCR 실행 모델명은 비어 있을 수 없습니다." }
        require(extractedText.isNotBlank()) { "OCR 추출 결과는 비어 있을 수 없습니다." }
        require(durationMillis >= 0) { "OCR 실행 시간은 음수일 수 없습니다." }
    }
}
