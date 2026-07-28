package dev.study.airag.adapter.`in`.web.graph.controller

import dev.study.airag.application.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.dto.result.KnowledgeGraphEntityResult
import dev.study.airag.application.dto.result.KnowledgeGraphEvidenceResult
import dev.study.airag.application.dto.result.KnowledgeGraphNeighborhoodResult
import dev.study.airag.application.port.`in`.GetKnowledgeEntityNeighborhoodUseCase
import dev.study.airag.application.port.`in`.SearchKnowledgeGraphUseCase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KnowledgeGraphControllerTests {
    @Test
    fun `translates read only graph endpoints through inbound ports and preserves provenance`() {
        var searchQuery: SearchKnowledgeGraphQuery? = null
        var neighborhoodQuery: GetKnowledgeEntityNeighborhoodQuery? = null
        val entity =
            KnowledgeGraphEntityResult(
                entityId = "entity-1",
                ontologyVersion = "test-v1",
                type = "TECHNOLOGY",
                name = "Milvus",
                aliases = setOf("Milvus DB"),
                evidence =
                    listOf(
                        KnowledgeGraphEvidenceResult("document-1", 2, "chunk-1", "Milvus", 0.9),
                    ),
            )
        val controller =
            KnowledgeGraphController(
                SearchKnowledgeGraphUseCase {
                    searchQuery = it
                    listOf(entity)
                },
                GetKnowledgeEntityNeighborhoodUseCase {
                    neighborhoodQuery = it
                    KnowledgeGraphNeighborhoodResult(entity, listOf(entity), emptyList())
                },
            )

        val search = controller.searchEntities("mil", "TECHNOLOGY", 5)
        val neighborhood = controller.getNeighborhood("entity-1", 2, 20)
        val evidence = search.single().evidence.single()

        assertEquals(SearchKnowledgeGraphQuery("mil", "TECHNOLOGY", 5), searchQuery)
        assertEquals("chunk-1", evidence.chunkId)
        assertEquals(GetKnowledgeEntityNeighborhoodQuery("entity-1", 2, 20), neighborhoodQuery)
        assertEquals("entity-1", neighborhood.center.entityId)
    }
}
