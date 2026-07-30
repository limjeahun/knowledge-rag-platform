package dev.study.airag.adapter.out.persistence.postgres

import dev.study.airag.adapter.out.persistence.postgres.document.KnowledgeDocumentEntity
import dev.study.airag.adapter.out.persistence.postgres.document.KnowledgeDocumentRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.registry.KnowledgeGraphProjectionRunRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.registry.KnowledgeOntologyVersionRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.registry.PostgresKnowledgeGraphProjectionRegistryAdapter
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjectionReceipt
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
import kotlin.test.assertEquals
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
    PostgresKnowledgeGraphProjectionRegistryAdapter::class,
    PostgresKnowledgeGraphProjectionRegistryAdapterTests.ObjectMapperTestConfig::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresKnowledgeGraphProjectionRegistryAdapterTests(
    @Autowired private val documentRepository: KnowledgeDocumentRepository,
    @Autowired private val projectionRunRepository: KnowledgeGraphProjectionRunRepository,
    @Autowired private val ontologyVersionRepository: KnowledgeOntologyVersionRepository,
    @Autowired private val registryAdapter: PostgresKnowledgeGraphProjectionRegistryAdapter,
) {
    private val now = Instant.parse("2026-07-28T00:00:00Z")

    @BeforeEach
    fun clearTables() {
        projectionRunRepository.deleteAllInBatch()
        ontologyVersionRepository.deleteAllInBatch()
        documentRepository.deleteAllInBatch()
    }

    @Test
    fun `registry keeps one active Fuseki projection and retains retired history`() {
        val documentId = saveDocument()

        registryAdapter.activate(receipt(documentId, 1, now))
        registryAdapter.activate(receipt(documentId, 2, now.plusSeconds(60)))

        val ontology = ontologyVersionRepository.findAll().single()
        val active =
            projectionRunRepository
                .findAllByDocumentIdAndStatus(documentId.value, "ACTIVE")
                .single()

        assertEquals("OWL", ontology.ontologyFormat)
        assertEquals("FUSEKI", active.backend)
        assertEquals(2, active.documentVersion)
        assertEquals(1, projectionRunRepository.findAllByDocumentIdAndStatus(documentId.value, "RETIRED").size)

        registryAdapter.retire(documentId)

        assertTrue(projectionRunRepository.findAllByDocumentIdAndStatus(documentId.value, "ACTIVE").isEmpty())
        assertEquals(2, projectionRunRepository.findAllByDocumentIdAndStatus(documentId.value, "RETIRED").size)
    }

    private fun receipt(
        documentId: DocumentId,
        documentVersion: Long,
        projectedAt: Instant,
    ) = KnowledgeGraphProjectionReceipt(
        documentId = documentId,
        documentVersion = documentVersion,
        ontologyIri = "urn:airag:ontology:software-architecture",
        ontologyVersion = "urn:airag:ontology:software-architecture:1.0.0",
        ontologyChecksum = "a".repeat(64),
        graphNames =
            listOf(
                "urn:airag:graph:document:$documentId:v$documentVersion:asserted",
                "urn:airag:graph:document:$documentId:v$documentVersion:inferred",
            ),
        projectedAt = projectedAt,
    )

    private fun saveDocument(): DocumentId {
        val id = DocumentId.newId()
        documentRepository.save(
            KnowledgeDocumentEntity(
                id = id.value,
                title = "semantic projection registry",
                originalContent = "An OWL knowledge graph is stored in Fuseki.",
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

        @Bean
        fun clock(): java.time.Clock =
            java.time.Clock.fixed(
                Instant.parse("2026-07-28T01:00:00Z"),
                java.time.ZoneOffset.UTC,
            )
    }
}
