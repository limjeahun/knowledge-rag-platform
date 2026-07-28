package dev.study.airag.adapter.`in`.web.graph.mapper

import dev.study.airag.adapter.`in`.web.graph.request.GetKnowledgeEntityNeighborhoodRequest
import dev.study.airag.adapter.`in`.web.graph.request.SearchKnowledgeGraphRequest
import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KnowledgeGraphWebMappingsTests {
    @Test
    fun `converts graph search request to application query`() {
        val request = SearchKnowledgeGraphRequest("mil", "TECHNOLOGY", 5)

        assertEquals(SearchKnowledgeGraphQuery("mil", "TECHNOLOGY", 5), request.toQuery())
    }

    @Test
    fun `converts graph neighborhood request and path entity id to application query`() {
        val request = GetKnowledgeEntityNeighborhoodRequest(depth = 2, limit = 20)

        assertEquals(
            GetKnowledgeEntityNeighborhoodQuery("entity-1", 2, 20),
            request.toQuery("entity-1"),
        )
    }
}
