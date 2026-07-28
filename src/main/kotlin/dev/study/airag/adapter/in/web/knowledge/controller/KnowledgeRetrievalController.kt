package dev.study.airag.adapter.`in`.web.knowledge.controller

import dev.study.airag.adapter.`in`.web.knowledge.mapper.toResponse
import dev.study.airag.adapter.`in`.web.knowledge.request.AskKnowledgeRequest
import dev.study.airag.adapter.`in`.web.knowledge.response.KnowledgeAnswerResponse
import dev.study.airag.adapter.`in`.web.knowledge.response.KnowledgeSearchHitResponse
import dev.study.airag.application.knowledge.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.knowledge.port.`in`.AnswerKnowledgeQuestionUseCase
import dev.study.airag.application.knowledge.port.`in`.SearchKnowledgeUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 저장된 지식의 검색과 근거 기반 질문 API를 제공한다. */
@RestController
@RequestMapping("/api")
class KnowledgeRetrievalController(
    private val searchUseCase: SearchKnowledgeUseCase,
    private val answerUseCase: AnswerKnowledgeQuestionUseCase,
) : KnowledgeRetrievalSpec {
    @GetMapping("/search")
    override fun search(
        @RequestParam query: String,
        @RequestParam(defaultValue = "5") topK: Int,
        @RequestParam(defaultValue = "0.5") similarityThreshold: Double,
    ): List<KnowledgeSearchHitResponse> =
        searchUseCase
            .search(
                SearchKnowledgeQuery(query, topK, similarityThreshold),
            ).map { it.toResponse() }

    @PostMapping("/chat")
    override fun chat(
        @Valid @RequestBody request: AskKnowledgeRequest,
    ): KnowledgeAnswerResponse =
        answerUseCase
            .answer(
                request.toQuery(),
            ).toResponse()
}
