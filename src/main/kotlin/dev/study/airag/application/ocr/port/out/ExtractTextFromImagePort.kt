package dev.study.airag.application.ocr.port.out

import dev.study.airag.application.ocr.exception.OcrExtractionException
import dev.study.airag.application.ocr.port.out.dto.OcrExtractionRequest
import dev.study.airag.application.ocr.port.out.dto.OcrExtractionResult

fun interface ExtractTextFromImagePort {
    /**
     * 지정한 모델로 이미지의 가시 텍스트를 추출한다.
     *
     * 공급자 호출 또는 응답 검증에 실패하면 모델과 소요 시간을 포함한
     * [OcrExtractionException]을 던진다.
     */
    fun extract(request: OcrExtractionRequest): OcrExtractionResult
}
