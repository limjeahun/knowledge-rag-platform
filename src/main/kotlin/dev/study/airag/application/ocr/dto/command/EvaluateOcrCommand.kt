package dev.study.airag.application.ocr.dto.command

/**
 * 이미지 한 장에 대한 OCR 정확도 평가 요청이다.
 *
 * 이미지 바이트는 호출자가 보유한 배열의 후속 변경으로부터 보호하기 위해 복사한다.
 */
class EvaluateOcrCommand(
    imageBytes: ByteArray,
    val mediaType: String,
    val groundTruth: String,
    val requestedModel: String? = null,
) {
    val imageBytes: ByteArray = imageBytes.copyOf()

    init {
        require(imageBytes.isNotEmpty()) { "OCR 평가 이미지는 비어 있을 수 없습니다." }
        require(mediaType.isNotBlank()) { "OCR 평가 이미지의 미디어 타입이 필요합니다." }
        require(groundTruth.isNotBlank()) { "OCR 평가 정답 텍스트는 비어 있을 수 없습니다." }
        require(requestedModel == null || requestedModel.isNotBlank()) {
            "OCR 모델명은 비어 있을 수 없습니다."
        }
    }
}
