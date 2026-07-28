package dev.study.airag.application.ocr.port.`in`

import dev.study.airag.application.ocr.dto.command.EvaluateOcrCommand
import dev.study.airag.application.ocr.dto.result.OcrEvaluationResult

fun interface EvaluateOcrUseCase {
    fun evaluate(command: EvaluateOcrCommand): OcrEvaluationResult
}
