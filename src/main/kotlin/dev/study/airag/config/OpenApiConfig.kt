package dev.study.airag.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// http://localhost:8080/swagger-ui.html
@Configuration
class OpenApiConfig {
    @Bean
    fun knowledgePlatformOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Knowledge RAG Platform API")
                    .description("지식 문서의 비동기 색인, 검색 및 근거 기반 답변을 제공하는 REST API")
                    .version("v1"),
            )
}
