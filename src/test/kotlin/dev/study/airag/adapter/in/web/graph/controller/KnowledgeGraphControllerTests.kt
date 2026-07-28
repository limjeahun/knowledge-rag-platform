package dev.study.airag.adapter.`in`.web.graph.controller

import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.dto.result.KnowledgeGraphEntityResult
import dev.study.airag.application.graph.dto.result.KnowledgeGraphEvidenceResult
import dev.study.airag.application.graph.dto.result.KnowledgeGraphNeighborhoodResult
import dev.study.airag.application.graph.port.`in`.GetKnowledgeEntityNeighborhoodUseCase
import dev.study.airag.application.graph.port.`in`.SearchKnowledgeGraphUseCase
import dev.study.airag.config.web.OpenApiConfig
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springdoc.core.properties.SwaggerUiOAuthProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springdoc.webmvc.ui.SwaggerConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals

@WebMvcTest(KnowledgeGraphController::class)
@Import(OpenApiConfig::class)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
    SwaggerConfig::class,
    SwaggerUiConfigProperties::class,
    SwaggerUiOAuthProperties::class,
)
class KnowledgeGraphControllerTests(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var searchUseCase: SearchKnowledgeGraphUseCase

    @MockitoBean
    private lateinit var neighborhoodUseCase: GetKnowledgeEntityNeighborhoodUseCase

    @Test
    fun `binds graph request DTOs and preserves provenance`() {
        var searchQuery: SearchKnowledgeGraphQuery? = null
        var neighborhoodQuery: GetKnowledgeEntityNeighborhoodQuery? = null
        val entity = entity()
        Mockito
            .`when`(
                searchUseCase.search(
                    Mockito.any(SearchKnowledgeGraphQuery::class.java)
                        ?: SearchKnowledgeGraphQuery("fallback"),
                ),
            ).thenAnswer { invocation ->
                searchQuery = invocation.getArgument(0)
                listOf(entity)
            }
        Mockito
            .`when`(
                neighborhoodUseCase.getNeighborhood(
                    Mockito.any(GetKnowledgeEntityNeighborhoodQuery::class.java)
                        ?: GetKnowledgeEntityNeighborhoodQuery("fallback"),
                ),
            ).thenAnswer { invocation ->
                neighborhoodQuery = invocation.getArgument(0)
                KnowledgeGraphNeighborhoodResult(entity, listOf(entity), emptyList())
            }

        mockMvc
            .get("/api/graph/entities") {
                param("query", "mil")
                param("type", "TECHNOLOGY")
                param("limit", "5")
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].evidence[0].chunkId") { value("chunk-1") }
            }
        mockMvc
            .get("/api/graph/entities/entity-1/neighborhood") {
                param("depth", "2")
                param("limit", "20")
            }.andExpect {
                status { isOk() }
                jsonPath("$.center.entityId") { value("entity-1") }
            }

        assertEquals(SearchKnowledgeGraphQuery("mil", "TECHNOLOGY", 5), searchQuery)
        assertEquals(GetKnowledgeEntityNeighborhoodQuery("entity-1", 2, 20), neighborhoodQuery)
    }

    @Test
    fun `uses request DTO defaults when optional graph parameters are omitted`() {
        val entity = entity()
        var searchQuery: SearchKnowledgeGraphQuery? = null
        var neighborhoodQuery: GetKnowledgeEntityNeighborhoodQuery? = null
        Mockito
            .`when`(
                searchUseCase.search(
                    Mockito.any(SearchKnowledgeGraphQuery::class.java)
                        ?: SearchKnowledgeGraphQuery("fallback"),
                ),
            ).thenAnswer { invocation ->
                searchQuery = invocation.getArgument(0)
                listOf(entity)
            }
        Mockito
            .`when`(
                neighborhoodUseCase.getNeighborhood(
                    Mockito.any(GetKnowledgeEntityNeighborhoodQuery::class.java)
                        ?: GetKnowledgeEntityNeighborhoodQuery("fallback"),
                ),
            ).thenAnswer { invocation ->
                neighborhoodQuery = invocation.getArgument(0)
                KnowledgeGraphNeighborhoodResult(entity, listOf(entity), emptyList())
            }

        mockMvc.get("/api/graph/entities") { param("query", "mil") }.andExpect { status { isOk() } }
        mockMvc.get("/api/graph/entities/entity-1/neighborhood").andExpect { status { isOk() } }

        assertEquals(SearchKnowledgeGraphQuery("mil", null, 20), searchQuery)
        assertEquals(GetKnowledgeEntityNeighborhoodQuery("entity-1", 1, 50), neighborhoodQuery)
    }

    @Test
    fun `OpenAPI exposes graph request DTO fields as parameters`() {
        mockMvc
            .get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.paths['/api/graph/entities'].get.parameters[*].name") {
                    value(org.hamcrest.Matchers.hasItems("query", "type", "limit"))
                }
                jsonPath("$.paths['/api/graph/entities/{entityId}/neighborhood'].get.parameters[*].name") {
                    value(org.hamcrest.Matchers.hasItems("entityId", "depth", "limit"))
                }
            }
    }

    private fun entity() =
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
}
