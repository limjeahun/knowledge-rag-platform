package dev.study.airag.adapter.`in`.web.knowledge.controller

import dev.study.airag.adapter.`in`.web.knowledge.mapper.toResponse
import dev.study.airag.adapter.`in`.web.knowledge.request.RegisterKnowledgeDocumentRequest
import dev.study.airag.adapter.`in`.web.knowledge.response.KnowledgeDocumentResponse
import dev.study.airag.adapter.`in`.web.knowledge.response.RegisteredKnowledgeDocumentResponse
import dev.study.airag.application.dto.command.DeleteKnowledgeDocumentCommand
import dev.study.airag.application.dto.command.RetryKnowledgeDocumentIndexingCommand
import dev.study.airag.application.port.`in`.DeleteKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.GetKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.ListKnowledgeDocumentsUseCase
import dev.study.airag.application.port.`in`.RegisterKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.RetryKnowledgeDocumentIndexingUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 지식 문서의 등록·조회·재색인·삭제 API를 제공한다. */
@RestController
@RequestMapping("/api/documents")
class KnowledgeDocumentController(
    private val registerUseCase: RegisterKnowledgeDocumentUseCase,
    private val getUseCase: GetKnowledgeDocumentUseCase,
    private val listUseCase: ListKnowledgeDocumentsUseCase,
    private val retryUseCase: RetryKnowledgeDocumentIndexingUseCase,
    private val deleteUseCase: DeleteKnowledgeDocumentUseCase,
) : KnowledgeDocumentSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    override fun register(
        @Valid @RequestBody request: RegisterKnowledgeDocumentRequest,
    ): RegisteredKnowledgeDocumentResponse =
        registerUseCase
            .register(
                request.toCommand(),
            ).toResponse()

    @GetMapping("/{documentId}")
    override fun get(
        @PathVariable documentId: String,
    ): KnowledgeDocumentResponse = getUseCase.get(documentId).toResponse()

    @GetMapping
    override fun list(): List<KnowledgeDocumentResponse> = listUseCase.list().map { it.toResponse() }

    @PostMapping("/{documentId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    override fun retry(
        @PathVariable documentId: String,
    ): RegisteredKnowledgeDocumentResponse =
        retryUseCase.retry(RetryKnowledgeDocumentIndexingCommand(documentId)).toResponse()

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun delete(
        @PathVariable documentId: String,
    ) = deleteUseCase.delete(DeleteKnowledgeDocumentCommand(documentId))
}
