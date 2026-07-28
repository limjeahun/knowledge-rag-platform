package dev.study.airag.application.service

import dev.study.airag.application.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.exception.InvalidKnowledgeGraphExtractionException
import dev.study.airag.application.exception.KnowledgeGraphEntityNotFoundException
import dev.study.airag.application.port.out.ExtractKnowledgeGraphPort
import dev.study.airag.application.port.out.KnowledgeGraphIndexPort
import dev.study.airag.application.port.out.KnowledgeGraphQueryPort
import dev.study.airag.application.port.out.KnowledgeOntologyPort
import dev.study.airag.application.port.out.dto.ExtractedGraphEntity
import dev.study.airag.application.port.out.dto.ExtractedGraphEvidence
import dev.study.airag.application.port.out.dto.ExtractedGraphRelation
import dev.study.airag.application.port.out.dto.ExtractedKnowledgeGraph
import dev.study.airag.application.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.port.out.dto.KnowledgeOntology
import dev.study.airag.application.port.out.dto.OntologyEntityType
import dev.study.airag.application.port.out.dto.OntologyRelationType
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphEntity
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphEvidence
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphNeighborhood
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphRelation
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
import kotlin.test.assertTrue

class KnowledgeGraphApplicationServiceTests {
    private val now = Instant.parse("2026-07-28T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ontology =
        KnowledgeOntology(
            version = "test-v1",
            entityTypes =
                listOf(
                    OntologyEntityType("TECHNOLOGY", "technology"),
                    OntologyEntityType("DATA_STORE", "data store"),
                ),
            relationTypes =
                listOf(
                    OntologyRelationType("STORES_IN", "storage relation", setOf("TECHNOLOGY"), setOf("DATA_STORE")),
                ),
        )
    private val document =
        KnowledgeDocument
            .register(
                DocumentId.newId(),
                "RAG architecture",
                "Milvus stores vector embeddings.",
                emptyMap(),
                now,
            ).also { it.pullDomainEvents() }
    private val chunk =
        KnowledgeChunk(
            chunkId = "${document.id}-v1-c0",
            documentId = document.id,
            documentVersion = 1,
            chunkIndex = 0,
            title = document.title,
            content = "Milvus stores vector embeddings.",
            metadata = emptyMap(),
        )

    @Test
    fun `validator merges the same semantic entity and keeps exact provenance`() {
        val first =
            extraction(
                technologyName = "Milvus",
                evidenceQuote = "Milvus",
            )
        val second =
            extraction(
                technologyName = "MILVUS",
                evidenceQuote = "Milvus stores vector embeddings.",
            )

        val result =
            KnowledgeGraphExtractionValidator().validateAndMerge(
                ontology,
                listOf(
                    KnowledgeGraphExtractionBatch(listOf(chunk), first),
                    KnowledgeGraphExtractionBatch(listOf(chunk), second),
                ),
                policy(),
            )

        assertEquals(2, result.entities.size)
        val milvus = result.entities.single { it.key.normalizedName == "milvus" }
        assertEquals(2, milvus.evidence.size)
        assertEquals("Milvus", milvus.name)
        assertEquals(1, result.relations.size)
    }

    @Test
    fun `validator rejects a quote that does not exist in the source chunk`() {
        val invalid = extraction(evidenceQuote = "Milvus is always the best database.")

        val exception =
            assertFailsWith<InvalidKnowledgeGraphExtractionException> {
                KnowledgeGraphExtractionValidator().validateAndMerge(
                    ontology,
                    listOf(KnowledgeGraphExtractionBatch(listOf(chunk), invalid)),
                    policy(),
                )
            }

        assertTrue(exception.message.orEmpty().contains("실제 청크 본문"))
    }

    @Test
    fun `validator rejects a relation whose endpoint types violate the ontology`() {
        val invalidOntology =
            ontology.copy(
                relationTypes =
                    listOf(
                        OntologyRelationType("STORES_IN", "storage relation", setOf("DATA_STORE"), setOf("TECHNOLOGY")),
                    ),
            )

        assertFailsWith<InvalidKnowledgeGraphExtractionException> {
            KnowledgeGraphExtractionValidator().validateAndMerge(
                invalidOntology,
                listOf(KnowledgeGraphExtractionBatch(listOf(chunk), extraction())),
                policy(),
            )
        }
    }

    @Test
    fun `projection batches chunks validates candidates and stores current document version`() {
        val stored = mutableListOf<KnowledgeGraphProjection>()
        var extractionCalls = 0
        val service =
            ProjectKnowledgeGraphService(
                KnowledgeOntologyPort { ontology },
                ExtractKnowledgeGraphPort {
                    extractionCalls++
                    extraction(chunkId = it.chunks.single().chunkId)
                },
                recordingIndex(stored),
                KnowledgeGraphExtractionValidator(),
                policy(chunksPerRequest = 1),
                clock,
            )

        service.project(document, listOf(chunk, chunk.copy(chunkId = "${document.id}-v1-c1")))

        assertEquals(2, extractionCalls)
        assertEquals(document.id, stored.single().documentId)
        assertEquals(document.version, stored.single().documentVersion)
        assertEquals("test-v1", stored.single().ontologyVersion)
    }

    @Test
    fun `disabled projection does not load ontology call a model or mutate the graph index`() {
        val service =
            ProjectKnowledgeGraphService(
                KnowledgeOntologyPort { error("ontology must not be loaded") },
                ExtractKnowledgeGraphPort { error("model must not be called") },
                recordingIndex(mutableListOf()),
                KnowledgeGraphExtractionValidator(),
                policy(enabled = false),
                clock,
            )

        service.project(document, listOf(chunk))
    }

    @Test
    fun `graph query validates ontology type and maps a provenance aware neighborhood`() {
        val evidence = StoredKnowledgeGraphEvidence(document.id.toString(), 1, chunk.chunkId, "Milvus", 0.9)
        val milvus =
            StoredKnowledgeGraphEntity(
                UUID.randomUUID().toString(),
                ontology.version,
                "TECHNOLOGY",
                "Milvus",
                emptySet(),
                listOf(evidence),
            )
        val store =
            object : KnowledgeGraphQueryPort {
                override fun searchEntities(
                    text: String,
                    type: String?,
                    limit: Int,
                ) = listOf(milvus)

                override fun findNeighborhood(
                    entityId: String,
                    depth: Int,
                    limit: Int,
                ) = StoredKnowledgeGraphNeighborhood(
                    center = milvus,
                    entities = listOf(milvus),
                    relations =
                        listOf(
                            StoredKnowledgeGraphRelation(
                                UUID.randomUUID().toString(),
                                ontology.version,
                                "STORES_IN",
                                milvus.entityId,
                                milvus.name,
                                milvus.entityId,
                                milvus.name,
                                listOf(evidence),
                            ),
                        ),
                )
            }
        val service = QueryKnowledgeGraphService(KnowledgeOntologyPort { ontology }, store)

        val search = service.search(SearchKnowledgeGraphQuery("mil", "TECHNOLOGY", 10))
        val neighborhood =
            service.getNeighborhood(GetKnowledgeEntityNeighborhoodQuery(milvus.entityId, depth = 1, limit = 10))
        val returnedEvidence = search.single().evidence.single()

        assertEquals("Milvus", search.single().name)
        assertEquals(chunk.chunkId, returnedEvidence.chunkId)
        assertEquals(milvus.entityId, neighborhood.center.entityId)
        assertEquals("STORES_IN", neighborhood.relations.single().type)
        assertFailsWith<IllegalArgumentException> {
            service.search(SearchKnowledgeGraphQuery("mil", "UNKNOWN", 10))
        }
    }

    @Test
    fun `graph query reports a missing center entity`() {
        val store =
            object : KnowledgeGraphQueryPort {
                override fun searchEntities(
                    text: String,
                    type: String?,
                    limit: Int,
                ) = emptyList<StoredKnowledgeGraphEntity>()

                override fun findNeighborhood(
                    entityId: String,
                    depth: Int,
                    limit: Int,
                ): StoredKnowledgeGraphNeighborhood? = null
            }
        val service = QueryKnowledgeGraphService(KnowledgeOntologyPort { ontology }, store)

        assertFailsWith<KnowledgeGraphEntityNotFoundException> {
            service.getNeighborhood(GetKnowledgeEntityNeighborhoodQuery(UUID.randomUUID().toString()))
        }
    }

    private fun extraction(
        technologyName: String = "Milvus",
        evidenceQuote: String = "Milvus",
        chunkId: String = chunk.chunkId,
    ) = ExtractedKnowledgeGraph(
        entities =
            listOf(
                ExtractedGraphEntity(
                    "e1",
                    "TECHNOLOGY",
                    technologyName,
                    emptySet(),
                    0.9,
                    listOf(ExtractedGraphEvidence(chunkId, evidenceQuote)),
                ),
                ExtractedGraphEntity(
                    "e2",
                    "DATA_STORE",
                    "vector embeddings",
                    emptySet(),
                    0.9,
                    listOf(ExtractedGraphEvidence(chunkId, "vector embeddings")),
                ),
            ),
        relations =
            listOf(
                ExtractedGraphRelation(
                    "STORES_IN",
                    "e1",
                    "e2",
                    0.8,
                    listOf(ExtractedGraphEvidence(chunkId, "Milvus stores vector embeddings.")),
                ),
            ),
    )

    private fun policy(
        enabled: Boolean = true,
        chunksPerRequest: Int = 4,
    ) = KnowledgeGraphProjectionPolicy(enabled, chunksPerRequest, 0.7, 10, 10)

    private fun recordingIndex(stored: MutableList<KnowledgeGraphProjection>) =
        object : KnowledgeGraphIndexPort {
            override fun replace(projection: KnowledgeGraphProjection) {
                stored += projection
            }

            override fun remove(documentId: DocumentId) = Unit
        }
}
