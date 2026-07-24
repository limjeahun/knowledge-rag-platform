package dev.study.airag.application.service

import dev.study.airag.application.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.dto.result.KnowledgeSearchHit
import dev.study.airag.application.outbox.OutboxEnvelope
import dev.study.airag.application.port.out.KnowledgeIndexPort
import dev.study.airag.application.port.out.OutboxEventPort
import dev.study.airag.application.port.out.PublishDocumentIndexingPort
import dev.study.airag.application.port.out.dto.DocumentIndexingPublication
import dev.study.airag.domain.event.DocumentIndexingRequested
import dev.study.airag.domain.event.KnowledgeDocumentDeleted
import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.vo.DocumentId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KnowledgeDocumentEventDeliveryServiceTests {
    private val instant = Instant.parse("2026-07-20T00:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)

    @Test
    fun `delivers indexing and removal events through their outbound capabilities`() {
        val indexing = indexingEnvelope()
        val removal = removalEnvelope()
        val outbox = RecordingOutboxPort(listOf(indexing, removal))
        val index = RecordingKnowledgeIndexPort()
        val published = mutableListOf<DocumentIndexingPublication>()
        val service = service(outbox, index, PublishDocumentIndexingPort { published += it })

        val result = service.deliverPending(10)

        assertEquals(indexing.event.documentId, published.single().documentId)
        assertEquals(indexing.eventId, published.single().eventId)
        assertEquals(listOf(removal.event.documentId), index.removed)
        assertEquals(listOf(indexing.eventId, removal.eventId), result.deliveredEventIds)
        assertEquals(emptyList(), result.failures)
        assertEquals(result.deliveredEventIds, outbox.delivered)
    }

    @Test
    fun `records a failed removal for retry and continues the remaining batch`() {
        val failedRemoval = removalEnvelope()
        val indexing = indexingEnvelope()
        val outbox = RecordingOutboxPort(listOf(failedRemoval, indexing))
        val index = RecordingKnowledgeIndexPort(failedRemoval.event.documentId)
        val published = mutableListOf<DocumentIndexingPublication>()
        val service = service(outbox, index, PublishDocumentIndexingPort { published += it })

        val result = service.deliverPending(10)

        assertEquals(listOf(indexing.eventId), published.map { it.eventId })
        assertEquals(listOf(indexing.eventId), result.deliveredEventIds)
        assertEquals(failedRemoval.eventId, result.failures.single().eventId)
        assertEquals(listOf(failedRemoval.eventId to "index unavailable"), outbox.failed)
    }

    @Test
    fun `rejects a non-positive batch limit`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                service(RecordingOutboxPort(emptyList()), RecordingKnowledgeIndexPort()).deliverPending(0)
            }

        assertEquals("Outbox 전달 건수 제한은 0보다 커야 합니다.", exception.message)
    }

    private fun service(
        outbox: OutboxEventPort,
        index: KnowledgeIndexPort,
        publisher: PublishDocumentIndexingPort = PublishDocumentIndexingPort { },
    ) = DeliverPendingKnowledgeDocumentEventsService(outbox, publisher, index, clock)

    private fun indexingEnvelope() =
        OutboxEnvelope(
            UUID.randomUUID(),
            UUID.randomUUID(),
            DocumentIndexingRequested(instant, DocumentId.newId(), 1),
        )

    private fun removalEnvelope() =
        OutboxEnvelope(
            UUID.randomUUID(),
            UUID.randomUUID(),
            KnowledgeDocumentDeleted(instant, DocumentId.newId(), 1),
        )

    private class RecordingOutboxPort(
        private val pending: List<OutboxEnvelope>,
    ) : OutboxEventPort {
        val delivered = mutableListOf<UUID>()
        val failed = mutableListOf<Pair<UUID, String>>()

        override fun append(envelope: OutboxEnvelope) = Unit

        override fun findPending(limit: Int): List<OutboxEnvelope> = pending.take(limit)

        override fun markDelivered(
            eventId: UUID,
            deliveredAt: Instant,
        ) {
            delivered += eventId
        }

        override fun markFailed(
            eventId: UUID,
            error: String,
        ) {
            failed += eventId to error
        }
    }

    private class RecordingKnowledgeIndexPort(
        private val removalFailureId: DocumentId? = null,
    ) : KnowledgeIndexPort {
        val removed = mutableListOf<DocumentId>()

        override fun replace(
            documentId: DocumentId,
            documentVersion: Long,
            chunks: List<KnowledgeChunk>,
        ) = Unit

        override fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit> = emptyList()

        override fun remove(documentId: DocumentId) {
            if (documentId == removalFailureId) error("index unavailable")
            removed += documentId
        }
    }
}
