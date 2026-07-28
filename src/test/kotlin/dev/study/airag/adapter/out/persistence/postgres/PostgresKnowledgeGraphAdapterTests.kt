package dev.study.airag.adapter.out.persistence.postgres

import dev.study.airag.adapter.out.persistence.postgres.document.KnowledgeDocumentEntity
import dev.study.airag.adapter.out.persistence.postgres.document.KnowledgeDocumentRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.KnowledgeGraphEntityEvidenceRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.KnowledgeGraphEntityRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.KnowledgeGraphProjectionRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.KnowledgeGraphRelationEvidenceRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.KnowledgeGraphRelationRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.PostgresKnowledgeGraphAdapter
import dev.study.airag.application.port.out.dto.KnowledgeGraphEntityKey
import dev.study.airag.application.port.out.dto.KnowledgeGraphEvidence
import dev.study.airag.application.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.port.out.dto.ProjectedGraphEntity
import dev.study.airag.application.port.out.dto.ProjectedGraphRelation
import dev.study.airag.domain.vo.DocumentId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DataJpaTest(
    showSql = false,
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresKnowledgeGraphAdapterTests(
    @Autowired private val documentRepository: KnowledgeDocumentRepository,
    @Autowired private val entityRepository: KnowledgeGraphEntityRepository,
    @Autowired private val entityEvidenceRepository: KnowledgeGraphEntityEvidenceRepository,
    @Autowired private val relationRepository: KnowledgeGraphRelationRepository,
    @Autowired private val relationEvidenceRepository: KnowledgeGraphRelationEvidenceRepository,
    @Autowired private val projectionRepository: KnowledgeGraphProjectionRepository,
) {
    private val adapter =
        PostgresKnowledgeGraphAdapter(
            entityRepository,
            entityEvidenceRepository,
            relationRepository,
            relationEvidenceRepository,
            projectionRepository,
            JsonMapper.builder().build(),
        )
    private val now = Instant.parse("2026-07-28T00:00:00Z")

    @BeforeEach
    fun clearTables() {
        relationEvidenceRepository.deleteAllInBatch()
        relationRepository.deleteAllInBatch()
        entityEvidenceRepository.deleteAllInBatch()
        projectionRepository.deleteAllInBatch()
        entityRepository.deleteAllInBatch()
        documentRepository.deleteAllInBatch()
    }

    @Test
    fun `replace query and removal preserve graph elements still proven by another document`() {
        val firstDocument = saveDocument("Milvus architecture")
        val secondDocument = saveDocument("Milvus operations")
        adapter.replace(fullProjection(firstDocument))
        adapter.replace(fullProjection(firstDocument))
        adapter.replace(milvusOnlyProjection(secondDocument))

        val search = adapter.searchEntities("mil", "TECHNOLOGY", 10)
        val milvus = search.single()
        val neighborhood = adapter.findNeighborhood(milvus.entityId, depth = 1, limit = 10)
        val limitedNeighborhood = adapter.findNeighborhood(milvus.entityId, depth = 1, limit = 1)

        assertEquals(2, milvus.evidence.size)
        assertEquals(milvus.entityId, adapter.searchEntities("Milvus DB", null, 10).single().entityId)
        assertNotNull(neighborhood)
        assertEquals(2, neighborhood.entities.size)
        assertEquals("STORES_IN", neighborhood.relations.single().type)
        assertEquals(1, limitedNeighborhood?.entities?.size)
        assertTrue(limitedNeighborhood?.relations.orEmpty().isEmpty())
        assertEquals(2, projectionRepository.count())

        adapter.remove(firstDocument)

        val preserved = adapter.searchEntities("mil", null, 10).single()
        assertEquals(secondDocument.toString(), preserved.evidence.single().documentId)
        assertEquals(1, entityRepository.count())
        assertEquals(0, relationRepository.count())

        adapter.remove(secondDocument)
        adapter.remove(secondDocument)

        assertTrue(adapter.searchEntities("mil", null, 10).isEmpty())
        assertEquals(0, entityRepository.count())
        assertEquals(0, projectionRepository.count())
    }

    private fun saveDocument(title: String): DocumentId {
        val id = DocumentId.newId()
        documentRepository.save(
            KnowledgeDocumentEntity(
                id = id.value,
                title = title,
                originalContent = "Milvus stores vector embeddings.",
                metadataJson = "{}",
                documentVersion = 1,
                indexingStatus = "INDEXING",
                failureReason = null,
                registeredAt = now,
                indexedAt = null,
                updatedAt = now,
            ),
        )
        return id
    }

    private fun fullProjection(documentId: DocumentId): KnowledgeGraphProjection {
        val milvus = KnowledgeGraphEntityKey("TECHNOLOGY", "milvus")
        val embeddings = KnowledgeGraphEntityKey("DATA_STORE", "vector embeddings")
        val evidence = KnowledgeGraphEvidence("chunk-0", "Milvus stores vector embeddings.", 0.9)
        return KnowledgeGraphProjection(
            documentId = documentId,
            documentVersion = 1,
            ontologyVersion = "test-v1",
            entities =
                listOf(
                    ProjectedGraphEntity(milvus, "Milvus", emptySet(), listOf(evidence)),
                    ProjectedGraphEntity(embeddings, "vector embeddings", emptySet(), listOf(evidence)),
                ),
            relations =
                listOf(
                    ProjectedGraphRelation("STORES_IN", milvus, embeddings, listOf(evidence)),
                ),
            projectedAt = now,
        )
    }

    private fun milvusOnlyProjection(documentId: DocumentId): KnowledgeGraphProjection {
        val milvus = KnowledgeGraphEntityKey("TECHNOLOGY", "milvus")
        val evidence = KnowledgeGraphEvidence("chunk-0", "Milvus", 0.95)
        return KnowledgeGraphProjection(
            documentId = documentId,
            documentVersion = 1,
            ontologyVersion = "test-v1",
            entities = listOf(ProjectedGraphEntity(milvus, "Milvus", setOf("Milvus DB"), listOf(evidence))),
            relations = emptyList(),
            projectedAt = now,
        )
    }

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
