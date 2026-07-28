package dev.study.airag.adapter.out.ai.ollama.knowledge

import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit
import dev.study.airag.application.knowledge.exception.KnowledgeAnswerGenerationException
import dev.study.airag.application.knowledge.exception.KnowledgeAnswerGenerationFailure
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OllamaKnowledgeAnswerAdapterTests {
    @Test
    fun `returns generated content when provider completes the answer`() {
        val chatModel = chatModel()
        Mockito
            .`when`(chatModel.call(Mockito.any(Prompt::class.java)))
            .thenReturn(chatResponse(content = "React는 컴포넌트부터 학습하세요."))
        val adapter = OllamaKnowledgeAnswerAdapter(chatModel)

        val result = adapter.generate("React 입문 순서는?", listOf(source()))

        assertEquals("React는 컴포넌트부터 학습하세요.", result)
    }

    @Test
    fun `does not expose internal source identifiers to the model`() {
        val chatModel = chatModel()
        Mockito
            .`when`(chatModel.call(Mockito.any(Prompt::class.java)))
            .thenReturn(chatResponse(content = "React는 컴포넌트를 조합해 UI를 만듭니다."))
        val adapter = OllamaKnowledgeAnswerAdapter(chatModel)

        adapter.generate("React란 무엇인가요?", listOf(source()))

        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        Mockito.verify(chatModel).call(promptCaptor.capture())
        val promptText = promptCaptor.value.instructions.joinToString("\n") { it.text.orEmpty() }

        assertTrue(promptText.contains("React 입문"))
        assertTrue(promptText.contains("React는 컴포넌트를 조합해 사용자 인터페이스를 만든다."))
        assertFalse(promptText.contains("document-1"))
        assertFalse(promptText.contains("chunk-1"))
    }

    @Test
    fun `returns grounded message without calling provider when sources are empty`() {
        val chatModel = chatModel()
        val adapter = OllamaKnowledgeAnswerAdapter(chatModel)

        val result = adapter.generate("등록되지 않은 내용은?", emptyList())

        assertEquals("저장된 지식에서 답변의 근거를 찾지 못했습니다.", result)
        Mockito.verify(chatModel, Mockito.never()).call(Mockito.any(Prompt::class.java))
    }

    @Test
    fun `rejects answer truncated by output length`() {
        val chatModel = chatModel()
        Mockito
            .`when`(chatModel.call(Mockito.any(Prompt::class.java)))
            .thenReturn(
                chatResponse(
                    content = "",
                    finishReason = "length",
                    thinking = "unfinished reasoning",
                ),
            )
        val adapter = OllamaKnowledgeAnswerAdapter(chatModel)

        val exception =
            assertFailsWith<KnowledgeAnswerGenerationException> {
                adapter.generate("React 입문 순서는?", listOf(source()))
            }

        assertEquals(KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED, exception.failure)
    }

    @Test
    fun `rejects blank answer returned as successful provider response`() {
        val chatModel = chatModel()
        Mockito
            .`when`(chatModel.call(Mockito.any(Prompt::class.java)))
            .thenReturn(chatResponse(content = " "))
        val adapter = OllamaKnowledgeAnswerAdapter(chatModel)

        val exception =
            assertFailsWith<KnowledgeAnswerGenerationException> {
                adapter.generate("React 입문 순서는?", listOf(source()))
            }

        assertEquals(KnowledgeAnswerGenerationFailure.EMPTY_RESPONSE, exception.failure)
    }

    @Test
    fun `preserves original provider exception as cause`() {
        val chatModel = chatModel()
        val providerFailure = IllegalStateException("HTTP 500 from Ollama")
        Mockito
            .`when`(chatModel.call(Mockito.any(Prompt::class.java)))
            .thenThrow(providerFailure)
        val adapter = OllamaKnowledgeAnswerAdapter(chatModel)

        val exception =
            assertFailsWith<KnowledgeAnswerGenerationException> {
                adapter.generate("React 입문 순서는?", listOf(source()))
            }

        assertEquals(KnowledgeAnswerGenerationFailure.PROVIDER_CALL_FAILED, exception.failure)
        assertSame(providerFailure, exception.cause)
    }

    private fun chatResponse(
        content: String,
        finishReason: String = "stop",
        thinking: String? = null,
    ): ChatResponse {
        val generationMetadata =
            ChatGenerationMetadata
                .builder()
                .finishReason(finishReason)
                .apply {
                    if (thinking != null) {
                        metadata("thinking", thinking)
                    }
                }.build()
        val message =
            AssistantMessage
                .builder()
                .content(content)
                .apply {
                    if (thinking != null) {
                        properties(mapOf("thinking" to thinking))
                    }
                }.build()
        val responseMetadata =
            ChatResponseMetadata
                .builder()
                .model("qwen3.6:27b")
                .usage(DefaultUsage(1_761, 512, 2_273))
                .build()
        return ChatResponse(listOf(Generation(message, generationMetadata)), responseMetadata)
    }

    private fun chatModel(): ChatModel =
        Mockito.mock(ChatModel::class.java).also {
            Mockito
                .`when`(it.options)
                .thenReturn(ChatOptions.builder().model("qwen3.6:27b").build())
        }

    private fun source() =
        KnowledgeSearchHit(
            chunkId = "chunk-1",
            documentId = "document-1",
            documentVersion = 1,
            chunkIndex = 0,
            title = "React 입문",
            content = "React는 컴포넌트를 조합해 사용자 인터페이스를 만든다.",
            score = 0.9,
            metadata = mapOf("technology" to "react"),
        )
}
