package dev.study.airag.adapter.out.graph.rdf

import dev.study.airag.adapter.out.ontology.owl.OwlOntologyCatalog
import dev.study.airag.application.graph.dto.KnowledgeGraphAssertionKind
import dev.study.airag.application.graph.dto.query.FindRelevantKnowledgeGraphFactsQuery
import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityKey
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEvidence
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphEntity
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphRelation
import dev.study.airag.config.graph.KnowledgeGraphProperties
import dev.study.airag.domain.vo.DocumentId
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.query.DatasetFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import java.net.ServerSocket
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FusekiKnowledgeGraphAdapterTests {
    private var server: FusekiServer? = null

    @AfterEach
    fun stopServer() {
        server?.stop()
    }

    @Test
    fun `stores asserted inferred and provenance graphs and queries them through Fuseki`() {
        val port = freePort()
        server =
            FusekiServer
                .create()
                .loopback(true)
                .port(port)
                .add("/knowledge", DatasetFactory.createTxnMem())
                .build()
                .start()
        val properties =
            KnowledgeGraphProperties(
                enabled = true,
                fusekiDatasetUrl = "http://localhost:$port/knowledge",
            )
        val catalog = OwlOntologyCatalog(DefaultResourceLoader(), properties)
        val gateway = FusekiRdfDatasetGateway(properties)
        val mapper = RdfKnowledgeGraphProjectionMapper(OwlRdfVocabularyIndex(catalog))
        val index =
            FusekiKnowledgeGraphIndexAdapter(
                gateway,
                catalog,
                mapper,
                RdfKnowledgeGraphValidator(catalog),
                OwlKnowledgeGraphReasoner(catalog, properties),
            )
        val query = FusekiKnowledgeGraphQueryAdapter(gateway)
        val projection = projection()

        val receipt = index.replace(projection)

        assertEquals(5, receipt.graphNames.size)
        val indexer = query.searchEntities(SearchKnowledgeGraphQuery("Indexer")).single()
        assertTrue(indexer.evidence.isNotEmpty())

        val neighborhood =
            query.findNeighborhood(
                GetKnowledgeEntityNeighborhoodQuery(indexer.entityId, depth = 1, limit = 20),
            )!!
        assertTrue(
            neighborhood.relations.any {
                it.type == "WRITES_TO" &&
                    it.assertionKind == KnowledgeGraphAssertionKind.ASSERTED &&
                    it.evidence.isNotEmpty()
            },
        )
        assertTrue(
            neighborhood.relations.any {
                it.type == "USES" &&
                    it.assertionKind == KnowledgeGraphAssertionKind.INFERRED &&
                    it.evidence.isEmpty()
            },
        )
        val relevant = query.findRelevantFacts(FindRelevantKnowledgeGraphFactsQuery("Indexer", 20))
        assertEquals(setOf("WRITES_TO", "USES"), relevant.map { it.type }.toSet())

        index.remove(projection.documentId)

        assertTrue(query.searchEntities(SearchKnowledgeGraphQuery("Indexer")).isEmpty())
    }

    private fun projection(): KnowledgeGraphProjection {
        val source = KnowledgeGraphEntityKey("COMPONENT", "indexer")
        val target = KnowledgeGraphEntityKey("VECTOR_INDEX", "milvus")
        val evidence =
            KnowledgeGraphEvidence(
                chunkId = "chunk-1",
                quote = "Indexer writes document chunks to Milvus.",
                confidence = 0.97,
            )
        return KnowledgeGraphProjection(
            documentId = DocumentId.from(UUID.randomUUID().toString()),
            documentVersion = 1,
            ontologyVersion = "urn:airag:ontology:software-architecture:1.0.0",
            entities =
                listOf(
                    ProjectedGraphEntity(source, "Indexer", setOf("Index writer"), listOf(evidence)),
                    ProjectedGraphEntity(target, "Milvus", emptySet(), listOf(evidence)),
                ),
            relations = listOf(ProjectedGraphRelation("WRITES_TO", source, target, listOf(evidence))),
            projectedAt = Instant.parse("2026-07-29T00:00:00Z"),
        )
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
