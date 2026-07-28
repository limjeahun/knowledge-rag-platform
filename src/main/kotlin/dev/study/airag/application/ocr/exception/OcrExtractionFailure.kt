package dev.study.airag.application.ocr.exception

enum class OcrExtractionFailure(
    val message: String,
) {
    OUTPUT_TRUNCATED("OCR 모델이 응답 길이 한도 내에서 텍스트 추출을 완료하지 못했습니다."),
    EMPTY_RESPONSE("OCR 모델이 비어 있는 텍스트를 반환했습니다."),
    PROVIDER_CALL_FAILED("OCR 모델 공급자 호출에 실패했습니다."),
}
