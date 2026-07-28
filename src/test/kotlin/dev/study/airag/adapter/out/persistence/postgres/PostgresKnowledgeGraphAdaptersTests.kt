package dev.study.airag.adapter.out.persistence.postgres

import dev.study.airag.adapter.out.persistence.postgres.document.KnowledgeDocumentEntity
import dev.study.airag.adapter.out.persistence.postgres.document.KnowledgeDocumentRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.adapter.PostgresKnowledgeGraphIndexAdapter
import dev.study.airag.adapter.out.persistence.postgres.graph.adapter.PostgresKnowledgeGraphQueryAdapter
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphEntityEvidenceRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphEntityRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphProjectionRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphRelationEvidenceRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphRelationRepository
import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityKey
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEvidence
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphEntity
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphRelation
import dev.study.airag.domain.vo.DocumentId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
@Import(
    PostgresKnowledgeGraphIndexAdapter::class,
    PostgresKnowledgeGraphQueryAdapter::class,
    PostgresKnowledgeGraphAdaptersTests.ObjectMapperTestConfig::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresKnowledgeGraphAdaptersTests(
    @Autowired private val documentRepository: KnowledgeDocumentRepository,
    @Autowired private val entityRepository: KnowledgeGraphEntityRepository,
    @Autowired private val entityEvidenceRepository: KnowledgeGraphEntityEvidenceRepository,
    @Autowired private val relationRepository: KnowledgeGraphRelationRepository,
    @Autowired private val relationEvidenceRepository: KnowledgeGraphRelationEvidenceRepository,
    @Autowired private val projectionRepository: KnowledgeGraphProjectionRepository,
    @Autowired private val indexAdapter: PostgresKnowledgeGraphIndexAdapter,
    @Autowired private val queryAdapter: PostgresKnowledgeGraphQueryAdapter,
) {
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
        indexAdapter.replace(fullProjection(firstDocument))
        indexAdapter.replace(fullProjection(firstDocument))
        indexAdapter.replace(milvusOnlyProjection(secondDocument))

        val search = queryAdapter.searchEntities(SearchKnowledgeGraphQuery("mil", "TECHNOLOGY", 10))
        val milvus = search.single()
        val neighborhood =
            queryAdapter.findNeighborhood(GetKnowledgeEntityNeighborhoodQuery(milvus.entityId, 1, 10))
        val limitedNeighborhood =
            queryAdapter.findNeighborhood(GetKnowledgeEntityNeighborhoodQuery(milvus.entityId, 1, 1))

        assertEquals(2, milvus.evidence.size)
        assertEquals(
            milvus.entityId,
            queryAdapter.searchEntities(SearchKnowledgeGraphQuery("Milvus DB", null, 10)).single().entityId,
        )
        assertNotNull(neighborhood)
        assertEquals(2, neighborhood.entities.size)
        assertEquals("STORES_IN", neighborhood.relations.single().type)
        assertEquals(1, limitedNeighborhood?.entities?.size)
        assertTrue(limitedNeighborhood?.relations.orEmpty().isEmpty())
        assertEquals(2, projectionRepository.count())

        indexAdapter.remove(firstDocument)

        val preserved = queryAdapter.searchEntities(SearchKnowledgeGraphQuery("mil", null, 10)).single()
        assertEquals(secondDocument.toString(), preserved.evidence.single().documentId)
        assertEquals(1, entityRepository.count())
        assertEquals(0, relationRepository.count())

        indexAdapter.remove(secondDocument)
        indexAdapter.remove(secondDocument)

        assertTrue(queryAdapter.searchEntities(SearchKnowledgeGraphQuery("mil", null, 10)).isEmpty())
        assertEquals(0, entityRepository.count())
        assertEquals(0, projectionRepository.count())
    }

    @Test
    fun `projection replacement rolls back every graph row when a relation endpoint is missing`() {
        val documentId = saveDocument("invalid graph")
        val alpha = KnowledgeGraphEntityKey("CONCEPT", "alpha")
        val missing = KnowledgeGraphEntityKey("CONCEPT", "missing")
        val evidence = KnowledgeGraphEvidence("chunk-0", "Alpha", 0.9)
        val invalidProjection =
            KnowledgeGraphProjection(
                documentId = documentId,
                documentVersion = 1,
                ontologyVersion = "test-v1",
                entities = listOf(ProjectedGraphEntity(alpha, "Alpha", emptySet(), listOf(evidence))),
                relations = listOf(ProjectedGraphRelation("LINKS", alpha, missing, listOf(evidence))),
                projectedAt = now,
            )

        assertFailsWith<NoSuchElementException> {
            indexAdapter.replace(invalidProjection)
        }

        assertEquals(0, entityRepository.count())
        assertEquals(0, entityEvidenceRepository.count())
        assertEquals(0, relationRepository.count())
        assertEquals(0, relationEvidenceRepository.count())
        assertEquals(0, projectionRepository.count())
    }

    @Test
    fun `neighborhood query respects depth limit and does not revisit a cycle`() {
        val documentId = saveDocument("graph traversal")
        indexAdapter.replace(cyclicProjection(documentId))
        val center =
            queryAdapter
                .searchEntities(SearchKnowledgeGraphQuery("Alpha", "CONCEPT", 10))
                .single()

        val depthOne =
            assertNotNull(
                queryAdapter.findNeighborhood(GetKnowledgeEntityNeighborhoodQuery(center.entityId, 1, 10)),
            )
        val depthTwo =
            assertNotNull(
                queryAdapter.findNeighborhood(GetKnowledgeEntityNeighborhoodQuery(center.entityId, 2, 10)),
            )
        val limited =
            assertNotNull(
                queryAdapter.findNeighborhood(GetKnowledgeEntityNeighborhoodQuery(center.entityId, 2, 3)),
            )

        assertEquals(2, depthOne.entities.size)
        assertEquals(4, depthTwo.entities.size)
        assertEquals(
            depthTwo.entities.size,
            depthTwo.entities
                .map { it.entityId }
                .distinct()
                .size,
        )
        assertEquals(3, limited.entities.size)
    }

    @Test
    fun `neighborhood query returns absence for malformed and unknown entity identifiers`() {
        assertNull(queryAdapter.findNeighborhood(GetKnowledgeEntityNeighborhoodQuery("not-a-uuid", 1, 10)))
        assertNull(
            queryAdapter.findNeighborhood(
                GetKnowledgeEntityNeighborhoodQuery(UUID.randomUUID().toString(), 1, 10),
            ),
        )
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

    private fun cyclicProjection(documentId: DocumentId): KnowledgeGraphProjection {
        val alpha = KnowledgeGraphEntityKey("CONCEPT", "alpha")
        val beta = KnowledgeGraphEntityKey("CONCEPT", "beta")
        val gamma = KnowledgeGraphEntityKey("CONCEPT", "gamma")
        val delta = KnowledgeGraphEntityKey("CONCEPT", "delta")
        val evidence = KnowledgeGraphEvidence("chunk-0", "graph traversal", 0.9)
        val entities =
            listOf(
                ProjectedGraphEntity(alpha, "Alpha", emptySet(), listOf(evidence)),
                ProjectedGraphEntity(beta, "Beta", emptySet(), listOf(evidence)),
                ProjectedGraphEntity(gamma, "Gamma", emptySet(), listOf(evidence)),
                ProjectedGraphEntity(delta, "Delta", emptySet(), listOf(evidence)),
            )
        val relations =
            listOf(
                ProjectedGraphRelation("LINKS", alpha, beta, listOf(evidence)),
                ProjectedGraphRelation("LINKS", beta, gamma, listOf(evidence)),
                ProjectedGraphRelation("LINKS", gamma, delta, listOf(evidence)),
                ProjectedGraphRelation("LINKS", delta, beta, listOf(evidence)),
            )
        return KnowledgeGraphProjection(
            documentId = documentId,
            documentVersion = 1,
            ontologyVersion = "test-v1",
            entities = entities,
            relations = relations,
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

    @TestConfiguration(proxyBeanMethods = false)
    class ObjectMapperTestConfig {
        @Bean
        @Primary
        fun objectMapper(): ObjectMapper = JsonMapper.builder().build()
    }
}
