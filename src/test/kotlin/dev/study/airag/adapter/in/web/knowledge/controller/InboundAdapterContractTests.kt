package dev.study.airag.adapter.`in`.web.knowledge.controller

import dev.study.airag.adapter.`in`.mcp.KnowledgeMcpTools
import dev.study.airag.adapter.`in`.web.knowledge.request.AskKnowledgeRequest
import dev.study.airag.adapter.`in`.web.knowledge.request.RegisterKnowledgeDocumentRequest
import dev.study.airag.application.dto.command.DeleteKnowledgeDocumentCommand
import dev.study.airag.application.dto.command.RegisterKnowledgeDocumentCommand
import dev.study.airag.application.dto.command.RetryKnowledgeDocumentIndexingCommand
import dev.study.airag.application.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.dto.result.KnowledgeAnswerResult
import dev.study.airag.application.dto.result.KnowledgeDocumentResult
import dev.study.airag.application.dto.result.KnowledgeSearchHit
import dev.study.airag.application.dto.result.RegisteredKnowledgeDocumentResult
import dev.study.airag.application.port.`in`.AnswerKnowledgeQuestionUseCase
import dev.study.airag.application.port.`in`.DeleteKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.GetKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.ListKnowledgeDocumentsUseCase
import dev.study.airag.application.port.`in`.RegisterKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.RetryKnowledgeDocumentIndexingUseCase
import dev.study.airag.application.port.`in`.SearchKnowledgeUseCase
import dev.study.airag.domain.model.DocumentIndexingStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InboundAdapterContractTests {
    private val instant = Instant.parse("2026-07-20T00:00:00Z")
    private val hit = KnowledgeSearchHit("chunk-1", "document-1", 2, 0, "RAG", "content", 0.9, mapOf("team" to "ai"))

    @Test
    fun `controller translates every HTTP contract through inbound ports`() {
        var registeredCommand: RegisterKnowledgeDocumentCommand? = null
        var retryCommand: RetryKnowledgeDocumentIndexingCommand? = null
        var deleteCommand: DeleteKnowledgeDocumentCommand? = null
        var searchQuery: SearchKnowledgeQuery? = null
        var answerQuery: AnswerKnowledgeQuestionQuery? = null
        val documentController =
            KnowledgeDocumentController(
                RegisterKnowledgeDocumentUseCase { command ->
                    registeredCommand = command
                    RegisteredKnowledgeDocumentResult("document-1", DocumentIndexingStatus.PENDING)
                },
                GetKnowledgeDocumentUseCase {
                    KnowledgeDocumentResult(
                        it,
                        "RAG",
                        2,
                        DocumentIndexingStatus.FAILED,
                        "embedding unavailable",
                        instant,
                        null,
                    )
                },
                ListKnowledgeDocumentsUseCase { emptyList() },
                RetryKnowledgeDocumentIndexingUseCase {
                    retryCommand = it
                    RegisteredKnowledgeDocumentResult(it.documentId, DocumentIndexingStatus.PENDING)
                },
                DeleteKnowledgeDocumentUseCase { deleteCommand = it },
            )
        val retrievalController =
            KnowledgeRetrievalController(
                SearchKnowledgeUseCase {
                    searchQuery = it
                    listOf(hit)
                },
                AnswerKnowledgeQuestionUseCase {
                    answerQuery = it
                    KnowledgeAnswerResult(it.question, "answer", listOf(hit))
                },
            )

        val registered =
            documentController.register(RegisterKnowledgeDocumentRequest("RAG", "content", mapOf("team" to "ai")))
        val document = documentController.get("document-1")
        val retried = documentController.retry("document-1")
        documentController.delete("document-1")
        val searched = retrievalController.search("query", 3, 0.7)
        val answered = retrievalController.chat(AskKnowledgeRequest("question", 4, 0.8))

        assertEquals("RAG", registeredCommand?.title)
        assertEquals("content", registeredCommand?.content)
        assertEquals(mapOf("team" to "ai"), registeredCommand?.metadata)
        assertEquals("PENDING", registered.status)
        assertEquals("FAILED", document.status)
        assertEquals("embedding unavailable", document.failureReason)
        assertNull(document.indexedAt)
        assertEquals("document-1", retryCommand?.documentId)
        assertEquals("PENDING", retried.status)
        assertEquals("document-1", deleteCommand?.documentId)
        assertEquals(SearchKnowledgeQuery("query", 3, 0.7), searchQuery)
        assertEquals("chunk-1", searched.single().chunkId)
        assertEquals(AnswerKnowledgeQuestionQuery("question", 4, 0.8), answerQuery)
        assertEquals("answer", answered.answer)
        assertEquals("document-1", answered.sources.single().documentId)
    }

    @Test
    fun `MCP tools translate tool parameters through the same inbound ports`() {
        var searchQuery: SearchKnowledgeQuery? = null
        var answerQuery: AnswerKnowledgeQuestionQuery? = null
        val tools =
            KnowledgeMcpTools(
                SearchKnowledgeUseCase {
                    searchQuery = it
                    listOf(hit)
                },
                AnswerKnowledgeQuestionUseCase {
                    answerQuery = it
                    KnowledgeAnswerResult(it.question, "answer", listOf(hit))
                },
            )

        assertEquals(listOf(hit), tools.search("query", 7).hits)
        assertEquals("answer", tools.ask("question").answer)
        assertEquals(SearchKnowledgeQuery("query", 7), searchQuery)
        assertEquals(AnswerKnowledgeQuestionQuery("question"), answerQuery)
    }
}
