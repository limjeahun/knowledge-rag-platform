package dev.study.airag.adapter.`in`.web.ocr.mapper

import dev.study.airag.adapter.`in`.web.ocr.response.OcrEvaluationResponse
import dev.study.airag.adapter.`in`.web.ocr.response.OcrModelAttemptResponse
import dev.study.airag.application.ocr.dto.result.OcrEvaluationResult

fun OcrEvaluationResult.toResponse() =
    OcrEvaluationResponse(
        requestedModel = requestedModel,
        executedModel = executedModel,
        fallbackUsed = fallbackUsed,
        extractedText = extractedText,
        characterErrorRate = characterErrorRate,
        wordErrorRate = wordErrorRate,
        characterAccuracy = characterAccuracy,
        wordAccuracy = wordAccuracy,
        normalizedExactMatch = normalizedExactMatch,
        modelDurationMillis = modelDurationMillis,
        totalDurationMillis = totalDurationMillis,
        attempts =
            attempts.map {
                OcrModelAttemptResponse(
                    model = it.model,
                    status = it.status.name,
                    durationMillis = it.durationMillis,
                    failure = it.failure?.name,
                )
            },
    )
