package dev.study.airag.adapter.`in`.web.knowledge.controller

import dev.study.airag.application.port.`in`.AnswerKnowledgeQuestionUseCase
import dev.study.airag.application.port.`in`.DeleteKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.GetKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.ListKnowledgeDocumentsUseCase
import dev.study.airag.application.port.`in`.RegisterKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.RetryKnowledgeDocumentIndexingUseCase
import dev.study.airag.application.port.`in`.SearchKnowledgeUseCase
import dev.study.airag.config.OpenApiConfig
import org.junit.jupiter.api.Test
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

@WebMvcTest(controllers = [KnowledgeDocumentController::class, KnowledgeRetrievalController::class])
@Import(OpenApiConfig::class)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
    SwaggerConfig::class,
    SwaggerUiConfigProperties::class,
    SwaggerUiOAuthProperties::class,
)
class OpenApiContractTests(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var registerUseCase: RegisterKnowledgeDocumentUseCase

    @MockitoBean
    private lateinit var getUseCase: GetKnowledgeDocumentUseCase

    @MockitoBean
    private lateinit var listUseCase: ListKnowledgeDocumentsUseCase

    @MockitoBean
    private lateinit var retryUseCase: RetryKnowledgeDocumentIndexingUseCase

    @MockitoBean
    private lateinit var deleteUseCase: DeleteKnowledgeDocumentUseCase

    @MockitoBean
    private lateinit var searchUseCase: SearchKnowledgeUseCase

    @MockitoBean
    private lateinit var answerUseCase: AnswerKnowledgeQuestionUseCase

    @Test
    fun `OpenAPI document exposes every REST operation`() {
        mockMvc
            .get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.info.title") { value("Knowledge RAG Platform API") }
                jsonPath("$.info.version") { value("v1") }
                jsonPath("$.paths['/api/documents'].post.responses['202']") { exists() }
                jsonPath("$.paths['/api/documents'].post.summary") { value("지식 문서 등록") }
                jsonPath("$.paths['/api/documents'].post.tags[0]") { value("Knowledge") }
                jsonPath("$.paths['/api/documents'].get.responses['200']") { exists() }
                jsonPath("$.paths['/api/documents'].get.summary") { value("지식 문서 목록 조회") }
                jsonPath(
                    "$.paths['/api/documents'].get.responses['200'].content['*/*'].schema.items['\$ref']",
                ) { value("#/components/schemas/KnowledgeDocumentResponse") }
                jsonPath("$.paths['/api/documents/{documentId}'].get.responses['200']") { exists() }
                jsonPath("$.paths['/api/documents/{documentId}'].get.parameters[0].description") {
                    value("조회할 문서 UUID")
                }
                jsonPath("$.paths['/api/documents/{documentId}'].delete.responses['204']") { exists() }
                jsonPath("$.paths['/api/documents/{documentId}/retry'].post.responses['202']") { exists() }
                jsonPath("$.paths['/api/documents/{documentId}/retry'].post.responses['409']") { exists() }
                jsonPath(
                    "$.paths['/api/documents/{documentId}/retry'].post.responses['409']" +
                        ".content['*/*'].schema['\$ref']",
                ) { value("#/components/schemas/ApiErrorResponse") }
                jsonPath("$.paths['/api/search'].get.responses['200']") { exists() }
                jsonPath("$.paths['/api/search'].get.summary") { value("지식 검색") }
                jsonPath("$.paths['/api/search'].get.parameters[0].description") { value("검색할 자연어 질의") }
                jsonPath("$.paths['/api/chat'].post.responses['200']") { exists() }
                jsonPath("$.paths['/api/chat'].post.responses['415']") { exists() }
                jsonPath("$.paths['/api/chat'].post.responses['502']") { exists() }
                jsonPath(
                    "$.paths['/api/chat'].post.responses['502'].content['*/*'].schema['\$ref']",
                ) { value("#/components/schemas/ApiErrorResponse") }
                jsonPath("$.paths['/api/chat'].post.responses['500']") { exists() }
                jsonPath("$.paths['/api/chat'].post.summary") { value("지식 기반 질문") }
            }
    }

    @Test
    fun `Swagger UI entry point redirects to the bundled UI`() {
        mockMvc
            .get("/swagger-ui.html")
            .andExpect {
                status { is3xxRedirection() }
                redirectedUrl("/swagger-ui/index.html")
            }
    }
}
