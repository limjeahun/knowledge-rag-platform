package dev.study.airag.application.knowledge.service

import dev.study.airag.application.graph.policy.KnowledgeGraphProjectionPolicy
import dev.study.airag.application.graph.port.`in`.ProjectKnowledgeGraphUseCase
import dev.study.airag.application.graph.port.out.ExtractKnowledgeGraphPort
import dev.study.airag.application.graph.port.out.KnowledgeGraphIndexPort
import dev.study.airag.application.graph.port.out.KnowledgeOntologyPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology
import dev.study.airag.application.graph.port.out.dto.OntologyEntityType
import dev.study.airag.application.graph.service.ProjectKnowledgeGraphService
import dev.study.airag.application.graph.validation.KnowledgeGraphExtractionValidator
import dev.study.airag.application.knowledge.dto.command.DeleteKnowledgeDocumentCommand
import dev.study.airag.application.knowledge.dto.command.IndexKnowledgeDocumentCommand
import dev.study.airag.application.knowledge.dto.command.RegisterKnowledgeDocumentCommand
import dev.study.airag.application.knowledge.dto.command.RetryKnowledgeDocumentIndexingCommand
import dev.study.airag.application.knowledge.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.knowledge.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit
import dev.study.airag.application.knowledge.exception.DocumentIndexingAlreadyInProgressException
import dev.study.airag.application.knowledge.exception.DocumentIndexingFailedException
import dev.study.airag.application.knowledge.exception.KnowledgeAnswerGenerationException
import dev.study.airag.application.knowledge.exception.KnowledgeAnswerGenerationFailure
import dev.study.airag.application.knowledge.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.application.knowledge.outbox.OutboxEnvelope
import dev.study.airag.application.knowledge.port.out.ChunkKnowledgeDocumentPort
import dev.study.airag.application.knowledge.port.out.CorrelationIdGenerator
import dev.study.airag.application.knowledge.port.out.DocumentIndexingCompletionPort
import dev.study.airag.application.knowledge.port.out.DocumentIndexingLease
import dev.study.airag.application.knowledge.port.out.DocumentIndexingLockPort
import dev.study.airag.application.knowledge.port.out.EventIdGenerator
import dev.study.airag.application.knowledge.port.out.GenerateKnowledgeAnswerPort
import dev.study.airag.application.knowledge.port.out.KnowledgeDocumentPort
import dev.study.airag.application.knowledge.port.out.KnowledgeIndexPort
import dev.study.airag.application.knowledge.port.out.OutboxEventPort
import dev.study.airag.application.knowledge.port.out.dto.KnowledgeIndexReplacement
import dev.study.airag.domain.event.KnowledgeDocumentDeleted
import dev.study.airag.domain.exception.InvalidDocumentStateTransitionException
import dev.study.airag.domain.model.DocumentIndexingStatus
import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.model.KnowledgeDocument
import dev.study.airag.domain.vo.DocumentId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KnowledgeApplicationServiceTests {
    private val instant = Instant.parse("2026-07-18T00:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)
    private val correlationId = UUID.fromString("10000000-0000-0000-0000-000000000001")

    @Test
    fun `registration stores source document and outbox event`() {
        val documents = InMemoryDocumentPort()
        val outbox = InMemoryOutboxPort()
        val service = registerService(documents, outbox)

        val result =
            service.register(
                RegisterKnowledgeDocumentCommand(
                    "RAG",
                    "retrieval augmented generation",
                    mapOf("team" to "ai"),
                ),
            )

        assertEquals(DocumentIndexingStatus.PENDING, result.status)
        assertEquals(
            mapOf("team" to "ai"),
            documents.values.values
                .single()
                .metadata,
        )
        assertEquals(
            result.documentId,
            outbox.envelopes
                .single()
                .event
                .documentId
                .toString(),
        )
        assertEquals(correlationId, outbox.envelopes.single().correlationId)
    }

    @Test
    fun `retry changes a failed document to pending and creates a new event`() {
        val documents = InMemoryDocumentPort()
        val document = registeredDocument()
        document.startIndexing(instant)
        document.failIndexing("embedding unavailable", instant)
        documents.save(document)
        val outbox = InMemoryOutboxPort()

        val result =
            registerService(documents, outbox).retry(
                RetryKnowledgeDocumentIndexingCommand(document.id.toString()),
            )

        assertEquals(DocumentIndexingStatus.PENDING, result.status)
        assertNull(document.failureReason)
        assertEquals(
            document.id,
            outbox.envelopes
                .single()
                .event.documentId,
        )
    }

    @Test
    fun `retry rejects missing and non-failed documents`() {
        val documents = InMemoryDocumentPort()
        val service = registerService(documents, InMemoryOutboxPort())

        assertFailsWith<KnowledgeDocumentNotFoundException> {
            service.retry(RetryKnowledgeDocumentIndexingCommand(UUID.randomUUID().toString()))
        }

        val pending = registeredDocument()
        documents.save(pending)
        assertFailsWith<InvalidDocumentStateTransitionException> {
            service.retry(RetryKnowledgeDocumentIndexingCommand(pending.id.toString()))
        }
    }

    @Test
    fun `delete stores deleted state and removal request together`() {
        val documents = InMemoryDocumentPort()
        val document = registeredDocument()
        documents.save(document)
        val outbox = InMemoryOutboxPort()

        val service = deleteService(documents, outbox)
        service.delete(DeleteKnowledgeDocumentCommand(document.id.toString()))

        assertEquals(DocumentIndexingStatus.DELETED, document.status)
        val removal = outbox.envelopes.single().event as KnowledgeDocumentDeleted
        assertEquals(document.id, removal.documentId)
        assertEquals(document.version, removal.documentVersion)

        service.delete(DeleteKnowledgeDocumentCommand(document.id.toString()))
        assertEquals(1, outbox.envelopes.size)
    }

    @Test
    fun `delete rejects an unknown document`() {
        val outbox = InMemoryOutboxPort()
        val service = deleteService(InMemoryDocumentPort(), outbox)

        assertFailsWith<KnowledgeDocumentNotFoundException> {
            service.delete(DeleteKnowledgeDocumentCommand(UUID.randomUUID().toString()))
        }
    }

    @Test
    fun `get returns document state without exposing original content`() {
        val documents = InMemoryDocumentPort()
        val document = registeredDocument()
        documents.save(document)
        val service = queryService(documents)

        val result = service.get(document.id.toString())

        assertEquals(document.id.toString(), result.documentId)
        assertEquals(document.title, result.title)
        assertEquals(DocumentIndexingStatus.PENDING, result.status)
    }

    @Test
    fun `get rejects an unknown document`() {
        assertFailsWith<KnowledgeDocumentNotFoundException> {
            queryService().get(UUID.randomUUID().toString())
        }
    }

    @Test
    fun `search passes validated boundary values to the knowledge index`() {
        val index = InMemoryKnowledgeIndexPort()
        val expected = searchHit()
        index.searchResults = listOf(expected)
        val service = queryService(index = index)

        assertEquals(listOf(expected), service.search(SearchKnowledgeQuery("rag", 1, 0.0)))
        service.search(SearchKnowledgeQuery("rag", 20, 1.0))

        assertEquals(SearchKnowledgeQuery("rag", 20, 1.0), index.lastSearchQuery)
    }

    @Test
    fun `search query rejects blank text and values outside its contract`() {
        val invalidQueries =
            listOf(
                { SearchKnowledgeQuery(" ", 5, 0.5) } to "검색어는 비어 있을 수 없습니다.",
                { SearchKnowledgeQuery("rag", 0, 0.5) } to "topK는 1 이상 20 이하이어야 합니다.",
                { SearchKnowledgeQuery("rag", 21, 0.5) } to "topK는 1 이상 20 이하이어야 합니다.",
                { SearchKnowledgeQuery("rag", 5, -0.1) } to
                    "similarityThreshold는 0.0 이상 1.0 이하이어야 합니다.",
                { SearchKnowledgeQuery("rag", 5, 1.1) } to
                    "similarityThreshold는 0.0 이상 1.0 이하이어야 합니다.",
                { SearchKnowledgeQuery("rag", 5, Double.NaN) } to
                    "similarityThreshold는 0.0 이상 1.0 이하이어야 합니다.",
            )

        invalidQueries.forEach { (createQuery, expectedMessage) ->
            val exception = assertFailsWith<IllegalArgumentException> { createQuery() }
            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun `answer reuses returned sources when generating the response`() {
        val index = InMemoryKnowledgeIndexPort()
        val expected = listOf(searchHit())
        index.searchResults = expected
        var generatedSources: List<KnowledgeSearchHit>? = null
        val service =
            QueryKnowledgeService(
                InMemoryDocumentPort(),
                index,
                GenerateKnowledgeAnswerPort { _, sources ->
                    generatedSources = sources
                    "grounded answer"
                },
            )

        val result = service.answer(AnswerKnowledgeQuestionQuery("What is RAG?", 3, 0.7))

        assertEquals("grounded answer", result.answer)
        assertEquals(expected, result.sources)
        assertTrue(result.sources === generatedSources)
    }

    @Test
    fun `answer retries once with the most relevant half when generation is truncated`() {
        val index = InMemoryKnowledgeIndexPort()
        val sources =
            listOf(
                searchHit().copy(chunkId = "chunk-1", score = 0.9),
                searchHit().copy(chunkId = "chunk-2", score = 0.8),
                searchHit().copy(chunkId = "chunk-3", score = 0.7),
            )
        index.searchResults = sources
        val generatedSources = mutableListOf<List<KnowledgeSearchHit>>()
        val service =
            QueryKnowledgeService(
                InMemoryDocumentPort(),
                index,
                GenerateKnowledgeAnswerPort { _, attemptSources ->
                    generatedSources += attemptSources
                    if (generatedSources.size == 1) {
                        throw KnowledgeAnswerGenerationException(KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED)
                    }
                    "concise grounded answer"
                },
            )

        val result = service.answer(AnswerKnowledgeQuestionQuery("What is RAG?", 3, 0.7))

        assertEquals(2, generatedSources.size)
        assertEquals(sources, generatedSources[0])
        assertEquals(sources.take(2), generatedSources[1])
        assertEquals("concise grounded answer", result.answer)
        assertEquals(sources.take(2), result.sources)
    }

    @Test
    fun `answer halves a single source content before retrying`() {
        val index = InMemoryKnowledgeIndexPort()
        val source = searchHit().copy(content = "1234567890")
        index.searchResults = listOf(source)
        val generatedSources = mutableListOf<List<KnowledgeSearchHit>>()
        val service =
            QueryKnowledgeService(
                InMemoryDocumentPort(),
                index,
                GenerateKnowledgeAnswerPort { _, attemptSources ->
                    generatedSources += attemptSources
                    if (generatedSources.size == 1) {
                        throw KnowledgeAnswerGenerationException(KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED)
                    }
                    "answer"
                },
            )

        val result = service.answer(AnswerKnowledgeQuestionQuery("question"))

        assertEquals(2, generatedSources.size)
        assertEquals("12345", generatedSources[1].single().content)
        assertEquals(generatedSources[1], result.sources)
    }

    @Test
    fun `answer does not retry provider failures`() {
        val index = InMemoryKnowledgeIndexPort()
        index.searchResults = listOf(searchHit())
        var attempts = 0
        val expected =
            KnowledgeAnswerGenerationException(
                KnowledgeAnswerGenerationFailure.PROVIDER_CALL_FAILED,
                IllegalStateException("Ollama unavailable"),
            )
        val service =
            QueryKnowledgeService(
                InMemoryDocumentPort(),
                index,
                GenerateKnowledgeAnswerPort { _, _ ->
                    attempts += 1
                    throw expected
                },
            )

        val actual =
            assertFailsWith<KnowledgeAnswerGenerationException> {
                service.answer(AnswerKnowledgeQuestionQuery("question"))
            }

        assertTrue(actual === expected)
        assertEquals(1, attempts)
    }

    @Test
    fun `answer propagates a second truncation after exactly one retry`() {
        val index = InMemoryKnowledgeIndexPort()
        index.searchResults = listOf(searchHit(), searchHit().copy(chunkId = "chunk-2"))
        var attempts = 0
        val service =
            QueryKnowledgeService(
                InMemoryDocumentPort(),
                index,
                GenerateKnowledgeAnswerPort { _, _ ->
                    attempts += 1
                    throw KnowledgeAnswerGenerationException(KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED)
                },
            )

        val exception =
            assertFailsWith<KnowledgeAnswerGenerationException> {
                service.answer(AnswerKnowledgeQuestionQuery("question"))
            }

        assertEquals(KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED, exception.failure)
        assertEquals(2, attempts)
    }

    @Test
    fun `answer applies the same validation contract as search`() {
        val service = queryService()

        assertFailsWith<IllegalArgumentException> {
            service.answer(AnswerKnowledgeQuestionQuery("", 5, 0.5))
        }
        assertFailsWith<IllegalArgumentException> {
            service.answer(AnswerKnowledgeQuestionQuery("question", 0, 0.5))
        }
        assertFailsWith<IllegalArgumentException> {
            service.answer(AnswerKnowledgeQuestionQuery("question", 5, 2.0))
        }
    }

    @Test
    fun `indexing stores chunks then records durable completion`() {
        val fixture = indexingFixture()

        fixture.service.index(fixture.command())

        assertEquals(DocumentIndexingStatus.INDEXED, fixture.document.status)
        assertEquals(1, fixture.index.replacements)
        assertTrue(fixture.processed.wasCompleted(fixture.eventId))
        assertEquals(1, fixture.lock.releases)
    }

    @Test
    fun `completed event does not repeat chunking or index replacement`() {
        val fixture = indexingFixture()
        fixture.processed.complete(fixture.eventId, instant)

        fixture.service.index(fixture.command())

        assertEquals(DocumentIndexingStatus.PENDING, fixture.document.status)
        assertEquals(0, fixture.index.replacements)
    }

    @Test
    fun `stale deleted and indexed document events are completed without replacement`() {
        listOf(DocumentIndexingStatus.PENDING, DocumentIndexingStatus.DELETED, DocumentIndexingStatus.INDEXED)
            .forEach { status ->
                val fixture = indexingFixture(status)
                val version = if (status == DocumentIndexingStatus.PENDING) 2L else 1L

                fixture.service.index(fixture.command(version))

                assertEquals(0, fixture.index.replacements)
                assertTrue(fixture.processed.wasCompleted(fixture.eventId))
            }
    }

    @Test
    fun `indexing failure stores failed state without durable completion and releases lease`() {
        val fixture = indexingFixture()
        fixture.index.replaceFailure = IllegalStateException("Milvus unavailable")

        val failure = assertFailsWith<DocumentIndexingFailedException> { fixture.service.index(fixture.command()) }

        assertTrue(failure.cause is IllegalStateException)
        assertEquals("문서 색인에 실패했습니다: ${fixture.document.id}", failure.message)
        assertEquals(DocumentIndexingStatus.FAILED, fixture.document.status)
        assertEquals("Milvus unavailable", fixture.document.failureReason)
        assertFalse(fixture.processed.wasCompleted(fixture.eventId))
        assertEquals(1, fixture.lock.releases)
    }

    @Test
    fun `empty chunks fail indexing and remain retryable`() {
        val fixture = indexingFixture(chunks = emptyList())

        val failure = assertFailsWith<DocumentIndexingFailedException> { fixture.service.index(fixture.command()) }

        assertEquals("임베딩을 생성하기에 문서 내용이 너무 짧습니다.", failure.cause?.message)
        assertEquals(DocumentIndexingStatus.FAILED, fixture.document.status)
        assertEquals("임베딩을 생성하기에 문서 내용이 너무 짧습니다.", fixture.document.failureReason)
        assertFalse(fixture.processed.wasCompleted(fixture.eventId))
    }

    @Test
    fun `enabled graph extraction failure keeps the document failed and retryable`() {
        val fixture = indexingFixture(graphProjectionUseCase = failingGraphProjectionService())

        val failure = assertFailsWith<DocumentIndexingFailedException> { fixture.service.index(fixture.command()) }

        assertEquals("graph provider unavailable", failure.cause?.message)
        assertEquals(DocumentIndexingStatus.FAILED, fixture.document.status)
        assertEquals("graph provider unavailable", fixture.document.failureReason)
        assertFalse(fixture.processed.wasCompleted(fixture.eventId))
        assertEquals(1, fixture.lock.releases)
    }

    @Test
    fun `indexing rejects a concurrent lease without starting the workflow`() {
        val fixture = indexingFixture(lockAllowed = false)

        val exception =
            assertFailsWith<DocumentIndexingAlreadyInProgressException> {
                fixture.service.index(fixture.command())
            }

        assertEquals("다른 작업자가 색인 이벤트를 처리 중입니다: ${fixture.eventId}", exception.message)
        assertEquals(0, fixture.processed.claims)
        assertEquals(0, fixture.lock.releases)
    }

    private fun queryService(
        documents: InMemoryDocumentPort = InMemoryDocumentPort(),
        index: InMemoryKnowledgeIndexPort = InMemoryKnowledgeIndexPort(),
    ) = QueryKnowledgeService(documents, index, GenerateKnowledgeAnswerPort { _, _ -> "answer" })

    private fun indexingFixture(
        status: DocumentIndexingStatus = DocumentIndexingStatus.PENDING,
        chunks: List<KnowledgeChunk>? = null,
        lockAllowed: Boolean = true,
        graphProjectionUseCase: ProjectKnowledgeGraphUseCase = disabledGraphProjectionService(),
    ): IndexingFixture {
        val document = documentInStatus(status)
        val documents = InMemoryDocumentPort().also { it.save(document) }
        val index = InMemoryKnowledgeIndexPort()
        val processed = InMemoryProcessedMessagePort()
        val lock = RecordingDocumentIndexingLock(lockAllowed)
        val actualChunks =
            chunks
                ?: listOf(
                    KnowledgeChunk("chunk-1", document.id, document.version, 0, document.title, "content", emptyMap()),
                )
        val workflow =
            DocumentIndexingWorkflow(
                documents,
                ChunkKnowledgeDocumentPort { actualChunks },
                index,
                graphProjectionUseCase,
                processed,
                clock,
            )
        val eventId = UUID.randomUUID()
        return IndexingFixture(
            document,
            eventId,
            index,
            processed,
            lock,
            IndexKnowledgeDocumentService(lock, workflow),
        )
    }

    private fun IndexingFixture.command(version: Long = document.version) =
        IndexKnowledgeDocumentCommand(eventId, document.id.toString(), version)

    private fun documentInStatus(status: DocumentIndexingStatus): KnowledgeDocument {
        val document = registeredDocument()
        when (status) {
            DocumentIndexingStatus.PENDING -> {
                Unit
            }

            DocumentIndexingStatus.INDEXING -> {
                document.startIndexing(instant)
            }

            DocumentIndexingStatus.INDEXED -> {
                document.startIndexing(instant)
                document.completeIndexing(instant)
            }

            DocumentIndexingStatus.FAILED -> {
                document.startIndexing(instant)
                document.failIndexing("failed", instant)
            }

            DocumentIndexingStatus.DELETED -> {
                document.markDeleted(instant)
            }
        }
        return document
    }

    private fun registeredDocument() =
        KnowledgeDocument.register(DocumentId.newId(), "RAG", "content", emptyMap(), instant).also {
            it.pullDomainEvents()
        }

    private fun registerService(
        documents: KnowledgeDocumentPort,
        outbox: OutboxEventPort,
    ) = RegisterKnowledgeDocumentService(
        documents,
        outbox,
        EventIdGenerator { UUID.randomUUID() },
        CorrelationIdGenerator { correlationId },
        clock,
    )

    private fun deleteService(
        documents: KnowledgeDocumentPort,
        outbox: OutboxEventPort,
    ) = DeleteKnowledgeDocumentService(
        documents,
        outbox,
        EventIdGenerator { UUID.randomUUID() },
        CorrelationIdGenerator { correlationId },
        clock,
    )

    private fun searchHit() =
        KnowledgeSearchHit("chunk-1", UUID.randomUUID().toString(), 1, 0, "RAG", "content", 0.9, emptyMap())

    private fun disabledGraphProjectionService() =
        ProjectKnowledgeGraphService(
            ontologyPort = KnowledgeOntologyPort { error("비활성 그래프는 ontology를 읽지 않아야 합니다.") },
            extractKnowledgeGraphPort = ExtractKnowledgeGraphPort { error("비활성 그래프는 추출기를 호출하지 않아야 합니다.") },
            knowledgeGraphIndexPort =
                object : KnowledgeGraphIndexPort {
                    override fun replace(projection: KnowledgeGraphProjection) {
                        error("비활성 그래프는 저장하지 않아야 합니다.")
                    }

                    override fun remove(documentId: DocumentId) = Unit
                },
            validator = KnowledgeGraphExtractionValidator(),
            policy = KnowledgeGraphProjectionPolicy(false, 1, 0.7, 10, 10),
            clock = clock,
        )

    private fun failingGraphProjectionService() =
        ProjectKnowledgeGraphService(
            ontologyPort =
                KnowledgeOntologyPort {
                    KnowledgeOntology(
                        "test-v1",
                        listOf(
                            OntologyEntityType("CONCEPT", "concept"),
                        ),
                        emptyList(),
                    )
                },
            extractKnowledgeGraphPort = ExtractKnowledgeGraphPort { error("graph provider unavailable") },
            knowledgeGraphIndexPort =
                object : KnowledgeGraphIndexPort {
                    override fun replace(projection: KnowledgeGraphProjection) = Unit

                    override fun remove(documentId: DocumentId) = Unit
                },
            validator = KnowledgeGraphExtractionValidator(),
            policy = KnowledgeGraphProjectionPolicy(true, 1, 0.7, 10, 10),
            clock = clock,
        )

    private data class IndexingFixture(
        val document: KnowledgeDocument,
        val eventId: UUID,
        val index: InMemoryKnowledgeIndexPort,
        val processed: InMemoryProcessedMessagePort,
        val lock: RecordingDocumentIndexingLock,
        val service: IndexKnowledgeDocumentService,
    )

    private class InMemoryDocumentPort : KnowledgeDocumentPort {
        val values = mutableMapOf<DocumentId, KnowledgeDocument>()

        override fun save(document: KnowledgeDocument): KnowledgeDocument = document.also { values[it.id] = it }

        override fun findById(id: DocumentId): KnowledgeDocument? = values[id]

        override fun findAll(): List<KnowledgeDocument> = values.values.toList()
    }

    private class InMemoryOutboxPort : OutboxEventPort {
        val envelopes = mutableListOf<OutboxEnvelope>()

        override fun appendAll(envelopes: List<OutboxEnvelope>) {
            this.envelopes.addAll(envelopes)
        }

        override fun findPending(limit: Int) = envelopes.take(limit)

        override fun markDelivered(
            eventId: UUID,
            deliveredAt: Instant,
        ) = Unit

        override fun markFailed(
            eventId: UUID,
            error: String,
        ) = Unit
    }

    private class InMemoryProcessedMessagePort : DocumentIndexingCompletionPort {
        private val completed = mutableSetOf<UUID>()
        var claims = 0

        override fun claim(eventId: UUID): Boolean {
            claims++
            return eventId !in completed
        }

        override fun complete(
            eventId: UUID,
            completedAt: Instant,
        ) {
            completed += eventId
        }

        fun wasCompleted(eventId: UUID): Boolean = eventId in completed
    }

    private class RecordingDocumentIndexingLock(
        private val allowed: Boolean = true,
    ) : DocumentIndexingLockPort {
        var releases = 0

        override fun tryAcquire(eventId: UUID): DocumentIndexingLease? =
            if (allowed) DocumentIndexingLease(eventId, "owner") else null

        override fun release(lease: DocumentIndexingLease) {
            releases++
        }
    }

    private class InMemoryKnowledgeIndexPort : KnowledgeIndexPort {
        var replacements = 0
        var replaceFailure: RuntimeException? = null
        var searchResults: List<KnowledgeSearchHit> = emptyList()
        var lastSearchQuery: SearchKnowledgeQuery? = null
        val removedDocumentIds = mutableListOf<DocumentId>()

        override fun replace(replacement: KnowledgeIndexReplacement) {
            replaceFailure?.let { throw it }
            replacements++
        }

        override fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit> {
            lastSearchQuery = query
            return searchResults
        }

        override fun remove(documentId: DocumentId) {
            removedDocumentIds += documentId
        }
    }
}
