package dev.study.airag.application.ocr.exception

import dev.study.airag.application.ocr.dto.result.OcrModelAttemptResult

class OcrEvaluationUnavailableException(
    attempts: List<OcrModelAttemptResult>,
) : RuntimeException("사용 가능한 OCR 모델로 텍스트를 추출하지 못했습니다.") {
    val attempts: List<OcrModelAttemptResult> = attempts.toList()

    init {
        require(attempts.isNotEmpty()) { "실패한 OCR 모델 시도가 하나 이상 필요합니다." }
    }
}
