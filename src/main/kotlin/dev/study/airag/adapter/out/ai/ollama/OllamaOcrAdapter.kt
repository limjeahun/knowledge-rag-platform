package dev.study.airag.adapter.out.ai.ollama

import dev.study.airag.application.exception.OcrExtractionException
import dev.study.airag.application.exception.OcrExtractionFailure
import dev.study.airag.application.port.out.ExtractTextFromImagePort
import dev.study.airag.application.port.out.dto.OcrExtractionRequest
import dev.study.airag.application.port.out.dto.OcrExtractionResult
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.Media
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeTypeUtils
import java.time.Duration

/** Ollama Vision 모델에 이미지를 전달하고 OCR 원문과 모델 호출 시간을 반환한다. */
@Component
class OllamaOcrAdapter(
    private val chatModel: ChatModel,
) : ExtractTextFromImagePort {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun extract(request: OcrExtractionRequest): OcrExtractionResult {
        val startedAt = System.nanoTime()
        return try {
            val image =
                Media(
                    MimeTypeUtils.parseMimeType(request.mediaType),
                    ByteArrayResource(request.imageBytes),
                )
            val userMessage =
                UserMessage
                    .builder()
                    .text(OCR_PROMPT)
                    .media(image)
                    .build()
            val options =
                OllamaChatOptions
                    .builder()
                    .model(request.model)
                    .temperature(0.0)
                    .disableThinking()
                    .build()
            val response = chatModel.call(Prompt(userMessage, options))
            requireCompletedExtraction(
                response = response,
                model = request.model,
                durationMillis = elapsedMillis(startedAt),
            )
        } catch (exception: OcrExtractionException) {
            throw exception
        } catch (exception: Exception) {
            val durationMillis = elapsedMillis(startedAt)
            logger.error(
                "OCR 모델 호출에 실패했습니다. model={}, durationMillis={}",
                request.model,
                durationMillis,
                exception,
            )
            throw OcrExtractionException(
                model = request.model,
                failure = OcrExtractionFailure.PROVIDER_CALL_FAILED,
                durationMillis = durationMillis,
                cause = exception,
            )
        }
    }

    private fun requireCompletedExtraction(
        response: ChatResponse?,
        model: String,
        durationMillis: Long,
    ): OcrExtractionResult {
        val generation = response?.result
        val finishReason = generation?.metadata?.finishReason
        val extractedText = generation?.output?.text

        if (finishReason.equals(LENGTH_FINISH_REASON, ignoreCase = true)) {
            logInvalidResponse(model, finishReason, durationMillis, OcrExtractionFailure.OUTPUT_TRUNCATED)
            throw OcrExtractionException(
                model = model,
                failure = OcrExtractionFailure.OUTPUT_TRUNCATED,
                durationMillis = durationMillis,
            )
        }
        if (extractedText.isNullOrBlank()) {
            logInvalidResponse(model, finishReason, durationMillis, OcrExtractionFailure.EMPTY_RESPONSE)
            throw OcrExtractionException(
                model = model,
                failure = OcrExtractionFailure.EMPTY_RESPONSE,
                durationMillis = durationMillis,
            )
        }

        logger.debug(
            "OCR 텍스트 추출에 성공했습니다. model={}, finishReason={}, durationMillis={}",
            model,
            finishReason,
            durationMillis,
        )
        return OcrExtractionResult(
            model = model,
            extractedText = extractedText,
            durationMillis = durationMillis,
        )
    }

    private fun logInvalidResponse(
        model: String,
        finishReason: String?,
        durationMillis: Long,
        failure: OcrExtractionFailure,
    ) {
        logger.warn(
            "OCR 모델이 유효하지 않은 응답을 반환했습니다. model={}, failure={}, finishReason={}, durationMillis={}",
            model,
            failure,
            finishReason,
            durationMillis,
        )
    }

    private fun elapsedMillis(startedAt: Long): Long = Duration.ofNanos(System.nanoTime() - startedAt).toMillis()

    private companion object {
        const val LENGTH_FINISH_REASON = "length"
        const val OCR_PROMPT =
            """
            Perform OCR on the supplied image.
            Return only the text that is visibly present in the image.
            Preserve the natural reading order and line breaks.
            Do not add explanations, Markdown fences, labels, or inferred content.
            """
    }
}
