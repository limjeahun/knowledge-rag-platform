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
     * vector source만 사용하는 기존 호출을 Hybrid request 형태로 위임한다.
     *
     * @param question 사용자의 자연어 질문
     * @param sources 검색된 문서 청크 근거
     * @return 근거 제한 답변 또는 근거 부족 안내
     */
    override fun generate(
        question: String,
        sources: List<KnowledgeSearchHit>,
    ): String = generate(KnowledgeAnswerGenerationRequest(question, sources, emptyList()))

    /**
     * 동일한 vector source와 graph fact를 모델 context로 사용해 Hybrid GraphRAG 답변을 생성한다.
     *
     * vector source가 없고 graph fact만 있으면 모델 입력 형식을 통일하기 위한 임시 source로
     * 변환한다. 응답 Result의 실제 근거는 Application이 원본 fact를 별도로 보존하므로 임시
     * 식별자는 사용자에게 노출되지 않는다. 두 근거가 모두 없으면 모델을 호출하지 않는다.
     *
     * @param request 질문, vector source와 assertion kind가 보존된 graph fact
     * @return 모델이 정상 완료한 비어 있지 않은 자연어 답변
     * @throws KnowledgeAnswerGenerationException 출력 잘림, 빈 응답 또는 공급자 호출 실패
     */
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

    /**
     * provider 응답이 길이 제한 없이 완료되었고 실제 답변 텍스트를 포함하는지 검사한다.
     *
     * finish reason이 `length`이면 Application이 source 축소 재시도를 선택할 수 있도록
     * OUTPUT_TRUNCATED로 분류한다. 빈 응답은 EMPTY_RESPONSE로 구분하고 성공 시에만 사용량을
     * debug 로그로 남긴다.
     *
     * @param response Spring AI가 반환한 nullable provider 응답
     * @param sourceCount 이번 prompt에 포함된 context 항목 수
     * @return 공백이 아닌 답변 텍스트
     * @throws KnowledgeAnswerGenerationException 응답이 잘렸거나 비어 있는 경우
     */
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

    /**
     * 답변 본문이나 원문 context를 노출하지 않고 provider 진단 metadata만 기록한다.
     *
     * @param failure Application이 구분할 실패 종류
     * @param response 모델명과 token usage를 읽을 nullable provider 응답
     * @param finishReason provider 종료 사유
     * @param thinkingLength 내부 thinking 텍스트의 내용이 아닌 길이
     * @param sourceCount prompt context 항목 수
     */
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
