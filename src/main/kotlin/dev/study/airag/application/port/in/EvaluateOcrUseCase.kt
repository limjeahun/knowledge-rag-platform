package dev.study.airag.application.port.`in`

import dev.study.airag.application.dto.command.EvaluateOcrCommand
import dev.study.airag.application.dto.result.OcrEvaluationResult

fun interface EvaluateOcrUseCase {
    fun evaluate(command: EvaluateOcrCommand): OcrEvaluationResult
}
