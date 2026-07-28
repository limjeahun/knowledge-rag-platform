package dev.study.airag.application.ocr.service

import dev.study.airag.application.ocr.dto.command.EvaluateOcrCommand
import dev.study.airag.application.ocr.dto.result.OcrEvaluationResult
import dev.study.airag.application.ocr.dto.result.OcrModelAttemptResult
import dev.study.airag.application.ocr.dto.result.OcrModelAttemptStatus
import dev.study.airag.application.ocr.exception.OcrEvaluationUnavailableException
import dev.study.airag.application.ocr.exception.OcrExtractionException
import dev.study.airag.application.ocr.port.`in`.EvaluateOcrUseCase
import dev.study.airag.application.ocr.port.out.ExtractTextFromImagePort
import dev.study.airag.application.ocr.port.out.dto.OcrExtractionRequest

class EvaluateOcrService(
    private val extractTextFromImagePort: ExtractTextFromImagePort,
    prioritizedModels: List<String>,
) : EvaluateOcrUseCase {
    private val prioritizedModels =
        prioritizedModels
            .map(String::trim)
            .also {
                require(it.isNotEmpty()) { "OCR 모델을 하나 이상 설정해야 합니다." }
                require(it.none(String::isBlank)) { "OCR 모델명은 비어 있을 수 없습니다." }
                require(it.distinct().size == it.size) { "OCR 모델 우선순위에 중복 모델이 있습니다." }
            }

    override fun evaluate(command: EvaluateOcrCommand): OcrEvaluationResult {
        val candidates = selectCandidates(command.requestedModel)
        val attempts = mutableListOf<OcrModelAttemptResult>()

        candidates.forEachIndexed { candidateIndex, model ->
            try {
                val extraction =
                    extractTextFromImagePort.extract(
                        OcrExtractionRequest(
                            model = model,
                            imageBytes = command.imageBytes,
                            mediaType = command.mediaType,
                        ),
                    )
                val metrics = OcrTextMetrics.evaluate(command.groundTruth, extraction.extractedText)
                val successfulAttempt =
                    OcrModelAttemptResult(
                        model = extraction.model,
                        status = OcrModelAttemptStatus.SUCCEEDED,
                        durationMillis = extraction.durationMillis,
                    )
                val completedAttempts = attempts + successfulAttempt
                return OcrEvaluationResult(
                    requestedModel = command.requestedModel,
                    executedModel = extraction.model,
                    fallbackUsed = command.requestedModel == null && candidateIndex > 0,
                    extractedText = extraction.extractedText,
                    characterErrorRate = metrics.characterErrorRate,
                    wordErrorRate = metrics.wordErrorRate,
                    characterAccuracy = metrics.characterAccuracy,
                    wordAccuracy = metrics.wordAccuracy,
                    normalizedExactMatch = metrics.normalizedExactMatch,
                    modelDurationMillis = extraction.durationMillis,
                    totalDurationMillis = completedAttempts.sumOf(OcrModelAttemptResult::durationMillis),
                    attempts = completedAttempts,
                )
            } catch (exception: OcrExtractionException) {
                attempts +=
                    OcrModelAttemptResult(
                        model = model,
                        status = OcrModelAttemptStatus.FAILED,
                        durationMillis = exception.durationMillis,
                        failure = exception.failure,
                    )
            }
        }

        throw OcrEvaluationUnavailableException(attempts)
    }

    private fun selectCandidates(requestedModel: String?): List<String> {
        if (requestedModel == null) return prioritizedModels
        require(requestedModel in prioritizedModels) {
            "지원하지 않는 OCR 모델입니다: $requestedModel"
        }
        return listOf(requestedModel)
    }
}
