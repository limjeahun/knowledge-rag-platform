package dev.study.airag.domain.model

import dev.study.airag.domain.event.DocumentIndexingRequested
import dev.study.airag.domain.event.KnowledgeDocumentDeleted
import dev.study.airag.domain.exception.InvalidDocumentStateTransitionException
import dev.study.airag.domain.vo.DocumentId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KnowledgeDocumentTests {
    private val now = Instant.parse("2026-07-18T00:00:00Z")

    @Test
    fun `registration records one indexing requested event`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)

        val event = document.pullDomainEvents().single() as DocumentIndexingRequested

        assertEquals(document.id, event.documentId)
        assertEquals(document.version, event.documentVersion)
        assertEquals(now, event.occurredAt)
        assertTrue(document.pullDomainEvents().isEmpty())
    }

    @Test
    fun `retry records an indexing request only after a valid state transition`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        document.pullDomainEvents()
        document.startIndexing(now)
        document.failIndexing("failed", now)

        document.requestRetry(now.plusSeconds(1))

        assertTrue(document.pullDomainEvents().single() is DocumentIndexingRequested)
        assertFailsWith<InvalidDocumentStateTransitionException> { document.requestRetry(now.plusSeconds(2)) }
        assertTrue(document.pullDomainEvents().isEmpty())
    }

    @Test
    fun `repeated deletion records one deleted event`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        document.pullDomainEvents()

        document.markDeleted(now.plusSeconds(1))
        document.markDeleted(now.plusSeconds(2))

        val event = document.pullDomainEvents().single() as KnowledgeDocumentDeleted
        assertEquals(document.id, event.documentId)
        assertEquals(now.plusSeconds(1), event.occurredAt)
    }

    @Test
    fun `registered document starts pending and completes only through indexing`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)

        assertEquals(DocumentIndexingStatus.PENDING, document.status)
        document.startIndexing(now.plusSeconds(1))
        document.completeIndexing(now.plusSeconds(2))

        assertEquals(DocumentIndexingStatus.INDEXED, document.status)
        assertEquals(now.plusSeconds(2), document.indexedAt)
        assertNull(document.failureReason)
    }

    @Test
    fun `failed indexing can be requested again`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        document.startIndexing(now)
        document.failIndexing("embedding unavailable", now.plusSeconds(1))

        document.requestRetry(now.plusSeconds(2))

        assertEquals(DocumentIndexingStatus.PENDING, document.status)
        assertNull(document.failureReason)
    }

    @Test
    fun `indexed document can request ontology reindexing without changing its version`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        document.pullDomainEvents()
        document.startIndexing(now.plusSeconds(1))
        document.completeIndexing(now.plusSeconds(2))

        document.requestReindexing(now.plusSeconds(3))

        val event = document.pullDomainEvents().single() as DocumentIndexingRequested
        assertEquals(DocumentIndexingStatus.PENDING, document.status)
        assertEquals(1, document.version)
        assertNull(document.indexedAt)
        assertEquals(document.id, event.documentId)
        assertEquals(document.version, event.documentVersion)
        assertFailsWith<InvalidDocumentStateTransitionException> {
            document.requestReindexing(now.plusSeconds(4))
        }
    }

    @Test
    fun `pending document cannot complete indexing`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        val exception = assertFailsWith<InvalidDocumentStateTransitionException> { document.completeIndexing(now) }

        assertEquals(
            "색인 완료 작업은 문서 상태가 INDEXING일 때만 수행할 수 있습니다. 현재 상태: PENDING",
            exception.message,
        )
    }

    @Test
    fun `registration rejects blank title and content and copies metadata`() {
        val titleException =
            assertFailsWith<IllegalArgumentException> {
                KnowledgeDocument.register(DocumentId.newId(), " ", "content", emptyMap(), now)
            }
        val contentException =
            assertFailsWith<IllegalArgumentException> {
                KnowledgeDocument.register(DocumentId.newId(), "title", " ", emptyMap(), now)
            }

        assertEquals("문서 제목은 비어 있을 수 없습니다.", titleException.message)
        assertEquals("문서 본문은 비어 있을 수 없습니다.", contentException.message)

        val metadata = mutableMapOf("team" to "ai")
        val document = KnowledgeDocument.register(DocumentId.newId(), " title ", "content", metadata, now)
        metadata["team"] = "changed"

        assertEquals("title", document.title)
        assertEquals(mapOf("team" to "ai"), document.metadata)
    }

    @Test
    fun `only pending or failed documents can start indexing`() {
        val indexed = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        indexed.startIndexing(now)
        indexed.completeIndexing(now)

        val exception = assertFailsWith<InvalidDocumentStateTransitionException> { indexed.startIndexing(now) }

        assertEquals(
            "PENDING 또는 FAILED 상태의 문서만 색인을 시작할 수 있습니다. 현재 상태: INDEXED",
            exception.message,
        )

        val failed = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        failed.startIndexing(now)
        failed.failIndexing("failed", now)
        failed.startIndexing(now.plusSeconds(1))
        assertEquals(DocumentIndexingStatus.INDEXING, failed.status)
        assertNull(failed.failureReason)
    }

    @Test
    fun `failure reason must be present and is truncated to storage limit`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        document.startIndexing(now)

        val exception = assertFailsWith<IllegalArgumentException> { document.failIndexing(" ", now) }

        assertEquals("색인 실패 사유는 비어 있을 수 없습니다.", exception.message)

        document.failIndexing("x".repeat(2_001), now)
        assertEquals(2_000, document.failureReason?.length)
    }

    @Test
    fun `retry requires failed state`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)

        val exception = assertFailsWith<InvalidDocumentStateTransitionException> { document.requestRetry(now) }

        assertEquals(
            "색인 재시도 작업은 문서 상태가 FAILED일 때만 수행할 수 있습니다. 현재 상태: PENDING",
            exception.message,
        )
    }

    @Test
    fun `delete is idempotent and preserves the first deletion time`() {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        val deletedAt = now.plusSeconds(1)
        assertTrue(document.markDeleted(deletedAt))

        assertFalse(document.markDeleted(now.plusSeconds(2)))

        assertEquals(DocumentIndexingStatus.DELETED, document.status)
        assertEquals(deletedAt, document.updatedAt)
    }

    @Test
    fun `indexing decision covers every document state for the current version`() {
        val expected =
            mapOf(
                DocumentIndexingStatus.PENDING to DocumentIndexingDecision.INDEX,
                DocumentIndexingStatus.INDEXING to DocumentIndexingDecision.INDEX,
                DocumentIndexingStatus.INDEXED to DocumentIndexingDecision.ALREADY_INDEXED,
                DocumentIndexingStatus.FAILED to DocumentIndexingDecision.INDEX,
                DocumentIndexingStatus.DELETED to DocumentIndexingDecision.DOCUMENT_DELETED,
            )

        expected.forEach { (status, decision) ->
            assertEquals(decision, documentInStatus(status).decideIndexing(1), status.name)
        }
    }

    @Test
    fun `version mismatch is stale unless the document was deleted`() {
        DocumentIndexingStatus.entries.forEach { status ->
            val expected =
                if (status == DocumentIndexingStatus.DELETED) {
                    DocumentIndexingDecision.DOCUMENT_DELETED
                } else {
                    DocumentIndexingDecision.VERSION_MISMATCH
                }
            assertEquals(expected, documentInStatus(status).decideIndexing(2), status.name)
        }
    }

    @Test
    fun `conditional failure changes only an indexing document`() {
        val pending = documentInStatus(DocumentIndexingStatus.PENDING)
        val indexing = documentInStatus(DocumentIndexingStatus.INDEXING)

        assertFalse(pending.failIndexingIfInProgress("failure", now))
        assertTrue(indexing.failIndexingIfInProgress("failure", now))
        assertEquals(DocumentIndexingStatus.PENDING, pending.status)
        assertEquals(DocumentIndexingStatus.FAILED, indexing.status)
    }

    @Test
    fun `reconstitution restores persisted history without transitions`() {
        val id = DocumentId.newId()
        val indexedAt = now.plusSeconds(10)
        val document =
            KnowledgeDocument.reconstitute(
                id,
                "title",
                "content",
                mapOf("source" to "manual"),
                3,
                DocumentIndexingStatus.INDEXED,
                null,
                now,
                indexedAt,
                indexedAt,
            )

        assertEquals(id, document.id)
        assertEquals(3, document.version)
        assertEquals(indexedAt, document.indexedAt)
        assertTrue(document.metadata.containsKey("source"))
        assertTrue(document.pullDomainEvents().isEmpty())
    }

    private fun documentInStatus(status: DocumentIndexingStatus): KnowledgeDocument {
        val document = KnowledgeDocument.register(DocumentId.newId(), "title", "content", emptyMap(), now)
        when (status) {
            DocumentIndexingStatus.PENDING -> {
                Unit
            }

            DocumentIndexingStatus.INDEXING -> {
                document.startIndexing(now)
            }

            DocumentIndexingStatus.INDEXED -> {
                document.startIndexing(now)
                document.completeIndexing(now)
            }

            DocumentIndexingStatus.FAILED -> {
                document.startIndexing(now)
                document.failIndexing("failure", now)
            }

            DocumentIndexingStatus.DELETED -> {
                document.markDeleted(now)
            }
        }
        return document
    }
}
