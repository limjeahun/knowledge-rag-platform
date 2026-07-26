package dev.study.airag.adapter.out.ai.ollama

import dev.study.airag.application.exception.OcrExtractionException
import dev.study.airag.application.exception.OcrExtractionFailure
import dev.study.airag.application.port.out.dto.OcrExtractionRequest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.ai.ollama.api.ThinkOption
import org.springframework.util.MimeTypeUtils
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OllamaOcrAdapterTests {
    @Test
    fun `sends image with deterministic options to requested vision model`() {
        val chatModel = chatModel(chatResponse("문서 번호 123"))
        val adapter = OllamaOcrAdapter(chatModel)

        val result = adapter.extract(request())

        assertEquals(MODEL, result.model)
        assertEquals("문서 번호 123", result.extractedText)
        assertTrue(result.durationMillis >= 0)

        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        Mockito.verify(chatModel).call(promptCaptor.capture())
        val prompt = promptCaptor.value
        val options = prompt.options as OllamaChatOptions
        val userMessage = prompt.instructions.single() as UserMessage
        val media = userMessage.media.single()

        assertEquals(MODEL, options.model)
        assertEquals(0.0, options.temperature)
        assertEquals(ThinkOption.ThinkBoolean.DISABLED, options.thinkOption)
        assertEquals(MimeTypeUtils.IMAGE_PNG, media.mimeType)
        assertContentEquals(IMAGE_BYTES, media.dataAsByteArray)
        assertTrue(userMessage.text.orEmpty().contains("Return only the text"))
    }

    @Test
    fun `rejects output truncated by model length`() {
        val adapter = OllamaOcrAdapter(chatModel(chatResponse("", finishReason = "length")))

        val exception =
            assertFailsWith<OcrExtractionException> {
                adapter.extract(request())
            }

        assertEquals(OcrExtractionFailure.OUTPUT_TRUNCATED, exception.failure)
        assertEquals(MODEL, exception.model)
    }

    @Test
    fun `rejects blank successful response`() {
        val adapter = OllamaOcrAdapter(chatModel(chatResponse(" ")))

        val exception =
            assertFailsWith<OcrExtractionException> {
                adapter.extract(request())
            }

        assertEquals(OcrExtractionFailure.EMPTY_RESPONSE, exception.failure)
    }

    @Test
    fun `wraps provider failure and preserves cause`() {
        val providerFailure = IllegalStateException("Ollama unavailable")
        val chatModel = Mockito.mock(ChatModel::class.java)
        Mockito
            .`when`(chatModel.call(Mockito.any(Prompt::class.java)))
            .thenThrow(providerFailure)
        val adapter = OllamaOcrAdapter(chatModel)

        val exception =
            assertFailsWith<OcrExtractionException> {
                adapter.extract(request())
            }

        assertEquals(OcrExtractionFailure.PROVIDER_CALL_FAILED, exception.failure)
        assertSame(providerFailure, exception.cause)
    }

    private fun request() =
        OcrExtractionRequest(
            model = MODEL,
            imageBytes = IMAGE_BYTES,
            mediaType = "image/png",
        )

    private fun chatModel(response: ChatResponse): ChatModel =
        Mockito.mock(ChatModel::class.java).also {
            Mockito
                .`when`(it.call(Mockito.any(Prompt::class.java)))
                .thenReturn(response)
        }

    private fun chatResponse(
        content: String,
        finishReason: String = "stop",
    ): ChatResponse {
        val generationMetadata =
            ChatGenerationMetadata
                .builder()
                .finishReason(finishReason)
                .build()
        val message =
            AssistantMessage
                .builder()
                .content(content)
                .build()
        val responseMetadata =
            ChatResponseMetadata
                .builder()
                .model(MODEL)
                .build()
        return ChatResponse(listOf(Generation(message, generationMetadata)), responseMetadata)
    }

    private companion object {
        const val MODEL = "qwen3.6:27b"
        val IMAGE_BYTES = byteArrayOf(1, 2, 3)
    }
}
