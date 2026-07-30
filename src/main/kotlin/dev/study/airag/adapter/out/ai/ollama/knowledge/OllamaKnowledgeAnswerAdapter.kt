package dev.study.airag.adapter.out.ai.ollama.knowledge

import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit
import dev.study.airag.application.knowledge.exception.KnowledgeAnswerGenerationException
import dev.study.airag.application.knowledge.exception.KnowledgeAnswerGenerationFailure
import dev.study.airag.application.knowledge.port.out.GenerateKnowledgeAnswerPort
import dev.study.airag.application.knowledge.port.out.dto.KnowledgeAnswerGenerationRequest
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.stereotype.Component

/**
 * 검색된 문서·그래프 근거 밖의 내용을 사실처럼 답하지 않도록 제한해 답변을 생성한다.
 *
 * 문서 source가 없고 graph fact만 있으면 사실을 임시 context 항목으로 변환한다. prompt에는
 * asserted/inferred 표시를 유지하지만 내부 document/chunk 식별자는 자연어 답변에 노출하지
 * 않는다. provider 오류와 출력 길이 초과는 Application이 구분해 재시도할 수 있도록 번역한다.
 */
@Component
class OllamaKnowledgeAnswerAdapter(
    chatModel: ChatModel,
) : GenerateKnowledgeAnswerPort {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val chatClient =
        ChatClient
            .builder(chatModel)
            .defaultSystem(
                """
                You are a careful knowledge-base assistant.
                Answer in the same language as the question.
                Use only the supplied context for factual claims.
                If the context is insufficient, clearly say that you do not know.
                Return only the natural-language answer.
                Do not include document IDs, chunk IDs, UUIDs, source labels, or citations.
                The application returns source information separately.
                """.trimIndent(),
            ).build()

    /**
     * 근거가 없으면 모델을 호출하지 않고 근거 부족을 알린다.
     */
    override fun generate(
        question: String,
        sources: List<KnowledgeSearchHit>,
    ): String = generate(KnowledgeAnswerGenerationRequest(question, sources, emptyList()))

    /** 동일한 검색 결과를 context와 응답 근거에 재사용하는 Hybrid GraphRAG 모델 호출이다. */
    override fun generate(request: KnowledgeAnswerGenerationRequest): String {
        val sources =
            request.sources.ifEmpty {
                request.graphFacts.mapIndexed { index, fact ->
                    KnowledgeSearchHit(
                        chunkId = "graph-fact-$index",
                        documentId = "ontology-graph",
                        documentVersion = 1,
                        chunkIndex = index,
                        title = "Ontology graph",
                        content =
                            "[${fact.assertionKind}] ${fact.sourceName} --${fact.type}--> ${fact.targetName}",
                        score = null,
                        metadata = emptyMap(),
                    )
                }
            }
        val question = request.question
        if (sources.isEmpty()) return "저장된 지식에서 답변의 근거를 찾지 못했습니다."
        val context =
            sources.joinToString("\n\n") {
                """
                Title: ${it.title}
                Content:
                ${it.content}
                """.trimIndent()
            } +
                request.graphFacts
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(
                        separator = "\n",
                        prefix = "\n\nOntology graph facts:\n",
                    ) { fact ->
                        "- [${fact.assertionKind}] ${fact.sourceName} --${fact.type}--> ${fact.targetName}"
                    }.orEmpty()
        return try {
            val response =
                chatClient
                    .prompt()
                    .user("Context:\n$context\n\nQuestion:\n$question")
                    .call()
                    .chatResponse()
            requireCompletedAnswer(response, sources.size)
        } catch (exception: KnowledgeAnswerGenerationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(
                "AI 답변 공급자 호출에 실패했습니다. sourceCount={}",
                sources.size,
                exception,
            )
            throw KnowledgeAnswerGenerationException(
                KnowledgeAnswerGenerationFailure.PROVIDER_CALL_FAILED,
                exception,
            )
        }
    }

    private fun requireCompletedAnswer(
        response: ChatResponse?,
        sourceCount: Int,
    ): String {
        val generation = response?.result
        val finishReason = generation?.metadata?.finishReason
        val answer = generation?.output?.text
        val usage = response?.metadata?.usage
        val thinking: String? = generation?.metadata?.get(THINKING_METADATA_KEY)

        if (finishReason.equals(LENGTH_FINISH_REASON, ignoreCase = true)) {
            logInvalidResponse(
                failure = KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED,
                response = response,
                finishReason = finishReason,
                thinkingLength = thinking?.length ?: 0,
                sourceCount = sourceCount,
            )
            throw KnowledgeAnswerGenerationException(KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED)
        }
        if (answer.isNullOrBlank()) {
            logInvalidResponse(
                failure = KnowledgeAnswerGenerationFailure.EMPTY_RESPONSE,
                response = response,
                finishReason = finishReason,
                thinkingLength = thinking?.length ?: 0,
                sourceCount = sourceCount,
            )
            throw KnowledgeAnswerGenerationException(KnowledgeAnswerGenerationFailure.EMPTY_RESPONSE)
        }
        logger.debug(
            "AI 답변 생성에 성공했습니다. model={}, finishReason={}, promptTokens={}, completionTokens={}, " +
                "totalTokens={}, sourceCount={}",
            response.metadata.model,
            finishReason,
            usage?.promptTokens,
            usage?.completionTokens,
            usage?.totalTokens,
            sourceCount,
        )
        return answer
    }

    private fun logInvalidResponse(
        failure: KnowledgeAnswerGenerationFailure,
        response: ChatResponse?,
        finishReason: String?,
        thinkingLength: Int,
        sourceCount: Int,
    ) {
        val usage = response?.metadata?.usage
        logger.warn(
            "AI 답변 공급자가 유효하지 않은 응답을 반환했습니다. failure={}, model={}, finishReason={}, " +
                "promptTokens={}, completionTokens={}, totalTokens={}, thinkingLength={}, sourceCount={}",
            failure,
            response?.metadata?.model,
            finishReason,
            usage?.promptTokens,
            usage?.completionTokens,
            usage?.totalTokens,
            thinkingLength,
            sourceCount,
        )
    }

    private companion object {
        const val THINKING_METADATA_KEY = "thinking"
        const val LENGTH_FINISH_REASON = "length"
    }
}
