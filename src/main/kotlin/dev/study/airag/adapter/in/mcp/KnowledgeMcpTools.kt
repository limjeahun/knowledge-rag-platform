package dev.study.airag.adapter.`in`.mcp

import dev.study.airag.application.knowledge.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.knowledge.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.knowledge.dto.result.KnowledgeAnswerResult
import dev.study.airag.application.knowledge.port.`in`.AnswerKnowledgeQuestionUseCase
import dev.study.airag.application.knowledge.port.`in`.SearchKnowledgeUseCase
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

/** MCP 사용자가 로컬 지식을 검색하고 출처가 포함된 답변을 받을 수 있게 한다. */
@Component
class KnowledgeMcpTools(
    private val searchUseCase: SearchKnowledgeUseCase,
    private val answerUseCase: AnswerKnowledgeQuestionUseCase,
) {
    /**
     * 저장된 지식에서 질의문과 의미가 가까운 근거를 찾는다.
     *
     * topK는 1 이상 20 이하여야 한다.
     */
    @McpTool(
        name = "knowledge_search",
        title = "Search local knowledge",
        description = "Search the local Milvus knowledge index and return relevant passages.",
        generateOutputSchema = true,
        annotations =
            McpTool.McpAnnotations(
                title = "Search local knowledge",
                readOnlyHint = true,
                destructiveHint = false,
                idempotentHint = true,
                openWorldHint = false,
            ),
    )
    fun search(
        @McpToolParam(description = "Natural-language search query", required = true) query: String,
        @McpToolParam(description = "Number of passages to return, from 1 to 20", required = true) topK: Int,
    ): KnowledgeSearchToolResult =
        KnowledgeSearchToolResult(
            hits = searchUseCase.search(SearchKnowledgeQuery(query, topK)),
        )

    /**
     * 저장된 지식만을 사용해 질문에 답하고 실제 사용한 근거를 함께 반환한다.
     */
    @McpTool(
        name = "knowledge_ask",
        title = "Ask local knowledge",
        description =
            "Answer using local vector sources and asserted/inferred ontology graph facts, " +
                "and return both evidence forms.",
        generateOutputSchema = true,
        annotations =
            McpTool.McpAnnotations(
                title = "Ask local knowledge",
                readOnlyHint = true,
                destructiveHint = false,
                idempotentHint = true,
                openWorldHint = false,
            ),
    )
    fun ask(
        @McpToolParam(description = "Question to answer from the local knowledge base", required = true)
        question: String,
    ): KnowledgeAnswerResult = answerUseCase.answer(AnswerKnowledgeQuestionQuery(question))
}
