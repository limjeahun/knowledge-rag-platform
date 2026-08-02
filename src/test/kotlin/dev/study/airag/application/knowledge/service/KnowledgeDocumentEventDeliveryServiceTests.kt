package dev.study.airag.application.knowledge.service

import dev.study.airag.application.graph.port.out.KnowledgeGraphIndexPort
import dev.study.airag.application.graph.port.out.KnowledgeGraphProjectionRegistryPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjectionReceipt
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphReprojectionCriteria
import dev.study.airag.application.knowledge.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit
import dev.study.airag.application.knowledge.outbox.OutboxEnvelope
import dev.study.airag.application.knowledge.port.out.KnowledgeIndexPort
import dev.study.airag.application.knowledge.port.out.OutboxEventPort
import dev.study.airag.application.knowledge.port.out.PublishDocumentIndexingPort
import dev.study.airag.application.knowledge.port.out.dto.DocumentIndexingPublication
import dev.study.airag.application.knowledge.port.out.dto.KnowledgeIndexReplacement
import dev.study.airag.domain.event.DocumentIndexingRequested
import dev.study.airag.domain.event.KnowledgeDocumentDeleted
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
        val graph = RecordingKnowledgeGraphIndexPort()
        val registry = RecordingKnowledgeGraphProjectionRegistryPort()
        val published = mutableListOf<DocumentIndexingPublication>()
        val service =
            service(
                outbox,
                index,
                graph,
                publisher = PublishDocumentIndexingPort { published += it },
                registry = registry,
            )

        val result = service.deliverPending(10)

        assertEquals(indexing.event.documentId, published.single().documentId)
        assertEquals(indexing.eventId, published.single().eventId)
        assertEquals(listOf(removal.event.documentId), index.removed)
        assertEquals(listOf(removal.event.documentId), graph.removed)
        assertEquals(listOf(removal.event.documentId), registry.retired)
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
        val service =
            service(
                outbox,
                index,
                RecordingKnowledgeGraphIndexPort(),
                PublishDocumentIndexingPort { published += it },
            )

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
                service(
                    RecordingOutboxPort(emptyList()),
                    RecordingKnowledgeIndexPort(),
                    RecordingKnowledgeGraphIndexPort(),
                ).deliverPending(0)
            }

        assertEquals("Outbox 전달 건수 제한은 0보다 커야 합니다.", exception.message)
    }

    private fun service(
        outbox: OutboxEventPort,
        index: KnowledgeIndexPort,
        graphIndex: KnowledgeGraphIndexPort,
        publisher: PublishDocumentIndexingPort = PublishDocumentIndexingPort { },
        registry: KnowledgeGraphProjectionRegistryPort = RecordingKnowledgeGraphProjectionRegistryPort(),
    ) = DeliverPendingKnowledgeDocumentEventsService(outbox, publisher, index, graphIndex, clock, registry)

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

        override fun appendAll(envelopes: List<OutboxEnvelope>) = Unit

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

        override fun replace(replacement: KnowledgeIndexReplacement) = Unit

        override fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit> = emptyList()

        override fun remove(documentId: DocumentId) {
            if (documentId == removalFailureId) error("index unavailable")
            removed += documentId
        }
    }

    private class RecordingKnowledgeGraphIndexPort : KnowledgeGraphIndexPort {
        val removed = mutableListOf<DocumentId>()

        override fun replace(projection: KnowledgeGraphProjection): KnowledgeGraphProjectionReceipt =
            error("event delivery does not replace graph projections")

        override fun remove(documentId: DocumentId) {
            removed += documentId
        }
    }

    private class RecordingKnowledgeGraphProjectionRegistryPort : KnowledgeGraphProjectionRegistryPort {
        val retired = mutableListOf<DocumentId>()

        override fun activate(receipt: KnowledgeGraphProjectionReceipt) = Unit

        override fun retire(documentId: DocumentId) {
            retired += documentId
        }

        override fun findReprojectionCandidates(criteria: KnowledgeGraphReprojectionCriteria) = emptyList<DocumentId>()
    }
}
