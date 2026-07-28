package dev.study.airag.application.ocr.port.out.dto

class OcrExtractionRequest(
    val model: String,
    imageBytes: ByteArray,
    val mediaType: String,
) {
    val imageBytes: ByteArray = imageBytes.copyOf()

    init {
        require(model.isNotBlank()) { "OCR 추출 모델명은 비어 있을 수 없습니다." }
        require(imageBytes.isNotEmpty()) { "OCR 추출 이미지는 비어 있을 수 없습니다." }
        require(mediaType.isNotBlank()) { "OCR 추출 이미지의 미디어 타입이 필요합니다." }
    }
}
