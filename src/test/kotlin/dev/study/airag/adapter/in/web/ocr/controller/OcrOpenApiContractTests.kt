package dev.study.airag.adapter.`in`.web.ocr.controller

import dev.study.airag.application.port.`in`.EvaluateOcrUseCase
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

@WebMvcTest(OcrEvaluationController::class)
@Import(OpenApiConfig::class)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
    SwaggerConfig::class,
    SwaggerUiConfigProperties::class,
    SwaggerUiOAuthProperties::class,
)
class OcrOpenApiContractTests(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var evaluateOcrUseCase: EvaluateOcrUseCase

    @Test
    fun `OpenAPI exposes multipart OCR evaluation contract`() {
        mockMvc
            .get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.paths['/api/ocr/evaluations'].post.summary") { value("LLM OCR 성능 평가") }
                jsonPath("$.paths['/api/ocr/evaluations'].post.requestBody.content['multipart/form-data']") { exists() }
                jsonPath("$.paths['/api/ocr/evaluations'].post.responses['200']") { exists() }
                jsonPath("$.paths['/api/ocr/evaluations'].post.responses['413']") { exists() }
                jsonPath("$.paths['/api/ocr/evaluations'].post.responses['415']") { exists() }
                jsonPath("$.paths['/api/ocr/evaluations'].post.responses['502']") { exists() }
            }
    }
}
