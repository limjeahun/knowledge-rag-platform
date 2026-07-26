package dev.study.airag.adapter.`in`.web

import dev.study.airag.adapter.`in`.web.mapper.toResponse
import dev.study.airag.adapter.`in`.web.request.AskKnowledgeRequest
import dev.study.airag.adapter.`in`.web.request.RegisterKnowledgeDocumentRequest
import dev.study.airag.adapter.`in`.web.response.KnowledgeAnswerResponse
import dev.study.airag.adapter.`in`.web.response.KnowledgeDocumentResponse
import dev.study.airag.adapter.`in`.web.response.KnowledgeSearchHitResponse
import dev.study.airag.adapter.`in`.web.response.RegisteredKnowledgeDocumentResponse
import dev.study.airag.application.dto.command.DeleteKnowledgeDocumentCommand
import dev.study.airag.application.dto.command.RetryKnowledgeDocumentIndexingCommand
import dev.study.airag.application.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.port.`in`.AnswerKnowledgeQuestionUseCase
import dev.study.airag.application.port.`in`.DeleteKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.GetKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.ListKnowledgeDocumentsUseCase
import dev.study.airag.application.port.`in`.RegisterKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.RetryKnowledgeDocumentIndexingUseCase
import dev.study.airag.application.port.`in`.SearchKnowledgeUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 지식 문서의 등록·조회·재색인·삭제와 지식 검색·질문 API를 제공한다. */
@RestController
@RequestMapping("/api")
class KnowledgeController(
    private val registerUseCase:RegisterKnowledgeDocumentUseCase,
    private val getUseCase:     GetKnowledgeDocumentUseCase,
    private val listUseCase:    ListKnowledgeDocumentsUseCase,
    private val retryUseCase:   RetryKnowledgeDocumentIndexingUseCase,
    private val deleteUseCase:  DeleteKnowledgeDocumentUseCase,
    private val searchUseCase:  SearchKnowledgeUseCase,
    private val answerUseCase:  AnswerKnowledgeQuestionUseCase,
) : KnowledgeSpec {
    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.ACCEPTED)
    override fun register(
        @Valid @RequestBody request: RegisterKnowledgeDocumentRequest,
    ): RegisteredKnowledgeDocumentResponse =
        registerUseCase
            .register(
                request.toCommand(),
            ).toResponse()

    @GetMapping("/documents/{documentId}")
    override fun get(
        @PathVariable documentId: String,
    ): KnowledgeDocumentResponse = getUseCase.get(documentId).toResponse()

    @GetMapping("/documents")
    override fun list(): List<KnowledgeDocumentResponse> = listUseCase.list().map { it.toResponse() }

    @PostMapping("/documents/{documentId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    override fun retry(
        @PathVariable documentId: String,
    ): RegisteredKnowledgeDocumentResponse =
        retryUseCase.retry(RetryKnowledgeDocumentIndexingCommand(documentId)).toResponse()

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun delete(
        @PathVariable documentId: String,
    ) = deleteUseCase.delete(DeleteKnowledgeDocumentCommand(documentId))

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
