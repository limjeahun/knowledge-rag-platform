package dev.study.airag.application.graph.service

import dev.study.airag.application.graph.dto.command.ReindexKnowledgeDocumentsForOntologyCommand
import dev.study.airag.application.graph.port.out.KnowledgeGraphProjectionRegistryPort
import dev.study.airag.application.graph.port.out.KnowledgeOntologyPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjectionReceipt
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphReprojectionCriteria
import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology
import dev.study.airag.application.knowledge.outbox.OutboxEnvelope
import dev.study.airag.application.knowledge.port.out.KnowledgeDocumentPort
import dev.study.airag.application.knowledge.port.out.OutboxEventPort
import dev.study.airag.domain.event.DocumentIndexingRequested
import dev.study.airag.domain.model.DocumentIndexingStatus
import dev.study.airag.domain.model.KnowledgeDocument
import dev.study.airag.domain.vo.DocumentId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReindexKnowledgeDocumentsForOntologyServiceTests {
    private val now = Instant.parse("2026-07-31T00:00:00Z")

    @Test
    fun `requests reindexing through aggregate and outbox only for indexed stale projections`() {
        val indexed = indexedDocument()
        val failed = failedDocument()
        val documents = InMemoryDocumentPort(listOf(indexed, failed))
        val outbox = RecordingOutboxEventPort()
        val registry = FixedProjectionRegistry(listOf(indexed.id, failed.id))
        val service =
            ReindexKnowledgeDocumentsForOntologyService(
                ontologyPort = KnowledgeOntologyPort { KnowledgeOntology(CURRENT_ONTOLOGY, emptyList(), emptyList()) },
                projectionRegistryPort = registry,
                documentPort = documents,
                outboxEventPort = outbox,
                eventIdGenerator = { UUID.fromString("00000000-0000-0000-0000-000000000001") },
                correlationIdGenerator = { UUID.fromString("00000000-0000-0000-0000-000000000002") },
                clock = Clock.fixed(now, ZoneOffset.UTC),
            )

        val result = service.requestReindexing(ReindexKnowledgeDocumentsForOntologyCommand(limit = 10))

        assertEquals(CURRENT_ONTOLOGY, registry.criteria?.currentOntologyVersion)
        assertEquals(listOf(indexed.id.toString()), result.requestedDocumentIds)
        assertEquals(listOf(failed.id.toString()), result.skippedDocumentIds)
        assertEquals(DocumentIndexingStatus.PENDING, indexed.status)
        assertNull(indexed.indexedAt)
        assertEquals(DocumentIndexingStatus.FAILED, failed.status)
        val event = outbox.envelopes.single().event as DocumentIndexingRequested
        assertEquals(indexed.id, event.documentId)
        assertEquals(indexed.version, event.documentVersion)
    }

    @Test
    fun `does not append an empty outbox batch when no reprojection candidate exists`() {
        val outbox = RecordingOutboxEventPort()
        val service =
            ReindexKnowledgeDocumentsForOntologyService(
                ontologyPort = KnowledgeOntologyPort { KnowledgeOntology(CURRENT_ONTOLOGY, emptyList(), emptyList()) },
                projectionRegistryPort = FixedProjectionRegistry(emptyList()),
                documentPort = InMemoryDocumentPort(emptyList()),
                outboxEventPort = outbox,
                eventIdGenerator = { UUID.randomUUID() },
                correlationIdGenerator = { UUID.randomUUID() },
                clock = Clock.fixed(now, ZoneOffset.UTC),
            )

        val result = service.requestReindexing(ReindexKnowledgeDocumentsForOntologyCommand())

        assertTrue(result.requestedDocumentIds.isEmpty())
        assertEquals(0, outbox.appendCalls)
    }

    private fun indexedDocument(): KnowledgeDocument =
        KnowledgeDocument
            .register(DocumentId.newId(), "indexed", "content", emptyMap(), now.minusSeconds(10))
            .also {
                it.pullDomainEvents()
                it.startIndexing(now.minusSeconds(9))
                it.completeIndexing(now.minusSeconds(8))
            }

    private fun failedDocument(): KnowledgeDocument =
        KnowledgeDocument
            .register(DocumentId.newId(), "failed", "content", emptyMap(), now.minusSeconds(10))
            .also {
                it.pullDomainEvents()
                it.startIndexing(now.minusSeconds(9))
                it.failIndexing("failure", now.minusSeconds(8))
            }

    private class InMemoryDocumentPort(
        documents: List<KnowledgeDocument>,
    ) : KnowledgeDocumentPort {
        private val values = documents.associateBy(KnowledgeDocument::id).toMutableMap()

        override fun save(document: KnowledgeDocument): KnowledgeDocument = document.also { values[it.id] = it }

        override fun findById(id: DocumentId): KnowledgeDocument? = values[id]

        override fun findAll(): List<KnowledgeDocument> = values.values.toList()
    }

    private class FixedProjectionRegistry(
        private val candidates: List<DocumentId>,
    ) : KnowledgeGraphProjectionRegistryPort {
        var criteria: KnowledgeGraphReprojectionCriteria? = null

        override fun activate(receipt: KnowledgeGraphProjectionReceipt) = Unit

        override fun retire(documentId: DocumentId) = Unit

        override fun findReprojectionCandidates(criteria: KnowledgeGraphReprojectionCriteria): List<DocumentId> {
            this.criteria = criteria
            return candidates
        }
    }

    private class RecordingOutboxEventPort : OutboxEventPort {
        val envelopes = mutableListOf<OutboxEnvelope>()
        var appendCalls = 0

        override fun appendAll(envelopes: List<OutboxEnvelope>) {
            appendCalls++
            this.envelopes += envelopes
        }

        override fun findPending(limit: Int): List<OutboxEnvelope> = emptyList()

        override fun markDelivered(
            eventId: UUID,
            deliveredAt: Instant,
        ) = Unit

        override fun markFailed(
            eventId: UUID,
            error: String,
        ) = Unit
    }

    private companion object {
        const val CURRENT_ONTOLOGY = "urn:airag:ontology:software-architecture:2.0.0"
    }
}
