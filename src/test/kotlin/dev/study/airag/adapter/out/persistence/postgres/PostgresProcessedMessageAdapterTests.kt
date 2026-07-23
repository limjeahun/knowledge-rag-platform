package dev.study.airag.adapter.out.persistence.postgres

import dev.study.airag.adapter.out.persistence.postgres.document.KnowledgeDocumentMapper
import dev.study.airag.adapter.out.persistence.postgres.document.KnowledgeDocumentRepository
import dev.study.airag.adapter.out.persistence.postgres.document.PostgresKnowledgeDocumentAdapter
import dev.study.airag.adapter.out.persistence.postgres.outbox.OutboxEventMapper
import dev.study.airag.adapter.out.persistence.postgres.outbox.OutboxEventRepository
import dev.study.airag.adapter.out.persistence.postgres.outbox.PostgresOutboxAdapter
import dev.study.airag.adapter.out.persistence.postgres.processedmessage.PostgresDocumentIndexingCompletionAdapter
import dev.study.airag.adapter.out.persistence.postgres.processedmessage.ProcessedMessageRepository
import dev.study.airag.application.model.outbox.OutboxEnvelope
import dev.study.airag.domain.event.DocumentIndexingRequested
import dev.study.airag.domain.event.KnowledgeDocumentDeleted
import dev.study.airag.domain.model.DocumentIndexingStatus
import dev.study.airag.domain.model.KnowledgeDocument
import dev.study.airag.domain.vo.DocumentId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest(
    showSql = false,
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresProcessedMessageAdapterTests(
    @Autowired private val documentRepository: KnowledgeDocumentRepository,
    @Autowired private val outboxRepository: OutboxEventRepository,
    @Autowired private val processedMessageRepository: ProcessedMessageRepository,
    @Autowired transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val documentAdapter =
        PostgresKnowledgeDocumentAdapter(
            documentRepository,
            KnowledgeDocumentMapper(JsonMapper.builder().build()),
        )
    private val outboxAdapter = PostgresOutboxAdapter(outboxRepository, OutboxEventMapper())
    private val completionAdapter = PostgresDocumentIndexingCompletionAdapter(processedMessageRepository, "indexer")

    @BeforeEach
    fun clearTables() {
        processedMessageRepository.deleteAllInBatch()
        outboxRepository.deleteAllInBatch()
        documentRepository.deleteAllInBatch()
    }

    @Test
    fun `claim requires the caller transaction to own the advisory lock`() {
        val exception =
            kotlin.runCatching { completionAdapter.claim(UUID.randomUUID()) }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
        assertEquals("문서 색인 처리 권한 확인은 활성 트랜잭션 안에서 수행해야 합니다.", exception.message)
    }

    @Test
    fun `concurrent claim waits for completion and then rejects the duplicate`() {
        val eventId = UUID.randomUUID()
        val firstClaimed = CountDownLatch(1)
        val allowFirstCommit = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first =
                executor.submit<Boolean> {
                    transactionTemplate.execute {
                        val claimed = completionAdapter.claim(eventId)
                        firstClaimed.countDown()
                        assertTrue(allowFirstCommit.await(5, TimeUnit.SECONDS))
                        completionAdapter.complete(eventId, Instant.parse("2026-07-20T00:00:00Z"))
                        claimed
                    }
                }
            assertTrue(firstClaimed.await(5, TimeUnit.SECONDS))

            val duplicate =
                executor.submit<Boolean> {
                    transactionTemplate.execute {
                        completionAdapter.claim(eventId)
                    }
                }

            assertFalse(duplicate.isDone)
            allowFirstCommit.countDown()

            assertTrue(first.get(5, TimeUnit.SECONDS))
            assertFalse(duplicate.get(5, TimeUnit.SECONDS))
        } finally {
            allowFirstCommit.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `document adapter saves state and mapper restores the persisted document`() {
        val registeredAt = Instant.parse("2026-07-20T00:00:00Z")
        val document =
            KnowledgeDocument.register(
                DocumentId.newId(),
                "RAG",
                "retrieval augmented generation",
                mapOf("team" to "ai"),
                registeredAt,
            )

        assertTrue(documentAdapter.save(document) === document)
        document.startIndexing(registeredAt.plusSeconds(1))
        document.completeIndexing(registeredAt.plusSeconds(2))
        documentAdapter.save(document)

        val stored = documentRepository.findById(document.id.value).orElseThrow()
        assertEquals("INDEXED", stored.indexingStatus)

        val restored = documentAdapter.findById(document.id)
        assertEquals(document.id, restored?.id)
        assertEquals(document.metadata, restored?.metadata)
        assertEquals(DocumentIndexingStatus.INDEXED, restored?.status)
        assertEquals(document.indexedAt, restored?.indexedAt)

        assertNull(documentAdapter.findById(DocumentId.newId()))
    }

    @Test
    fun `outbox preserves order and records publish failure and completion`() {
        val first = indexingEvent(Instant.parse("2026-07-20T00:00:00Z"))
        val second = removalEvent(first.event.occurredAt.plusSeconds(1))
        outboxAdapter.append(second)
        outboxAdapter.append(first)

        assertEquals(first.eventId, outboxAdapter.findPending(1).single().eventId)
        assertEquals(listOf(first.eventId, second.eventId), outboxAdapter.findPending(10).map { it.eventId })
        assertTrue(outboxAdapter.findPending(10)[0].event is DocumentIndexingRequested)
        assertTrue(outboxAdapter.findPending(10)[1].event is KnowledgeDocumentDeleted)

        outboxAdapter.markFailed(second.eventId, "x".repeat(2_001))
        val failed = outboxRepository.findById(second.eventId).orElseThrow()
        assertEquals(1, failed.publishAttempts)
        assertEquals(2_000, failed.lastError?.length)
        assertNull(failed.publishedAt)

        outboxAdapter.markDelivered(second.eventId, second.event.occurredAt.plusSeconds(2))
        val published = outboxRepository.findById(second.eventId).orElseThrow()
        assertEquals(2, published.publishAttempts)
        assertNull(published.lastError)
        assertTrue(published.publishedAt != null)
        assertEquals(listOf(first.eventId), outboxAdapter.findPending(10).map { it.eventId })
    }

    private fun indexingEvent(occurredAt: Instant) =
        OutboxEnvelope(
            UUID.randomUUID(),
            UUID.randomUUID(),
            DocumentIndexingRequested(occurredAt, DocumentId.newId(), 1),
        )

    private fun removalEvent(occurredAt: Instant) =
        OutboxEnvelope(
            UUID.randomUUID(),
            UUID.randomUUID(),
            KnowledgeDocumentDeleted(occurredAt, DocumentId.newId(), 1),
        )

    companion object {
        private val postgres = PostgreSQLContainer("postgres:17.6-bookworm").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }

        @JvmStatic
        @AfterAll
        fun stopPostgres() {
            postgres.stop()
        }
    }
}
