package dev.study.airag.adapter.out.ai.ollama

import dev.study.airag.application.dto.result.KnowledgeSearchHit
import dev.study.airag.application.port.out.GenerateKnowledgeAnswerPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.stereotype.Component

/** 검색된 문서 근거 밖의 내용을 사실처럼 답하지 않도록 제한해 답변을 생성한다. */
@Component
class OllamaKnowledgeAnswerAdapter(
    chatModel: ChatModel,
) : GenerateKnowledgeAnswerPort {
    private val chatClient =
        ChatClient
            .builder(chatModel)
            .defaultSystem(
                """
                You are a careful knowledge-base assistant.
                Answer in the same language as the question.
                Use only the supplied context for factual claims.
                If the context is insufficient, clearly say that you do not know.
                """.trimIndent(),
            ).build()

    /**
     * 근거가 없으면 모델을 호출하지 않고 근거 부족을 알린다.
     */
    override fun generate(
        question: String,
        sources: List<KnowledgeSearchHit>,
    ): String {
        if (sources.isEmpty()) return "저장된 지식에서 답변의 근거를 찾지 못했습니다."
        val context =
            sources.joinToString("\n\n") {
                "[${it.documentId}/${it.chunkId}] ${it.content}"
            }
        return chatClient
            .prompt()
            .user("Context:\n$context\n\nQuestion:\n$question")
            .call()
            .content()
            .orEmpty()
    }
}
