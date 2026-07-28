package dev.study.airag.config

import dev.study.airag.application.service.KnowledgeGraphProjectionPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 온톨로지/그래프 프로젝션의 배포별 설정이다.
 *
 * 모델명, batch 크기, 신뢰도와 최대 건수는 업무 Aggregate 상수가 아니라 모델 성능과
 * 운영 자원에 따라 조정되는 기술 정책이므로 configuration에 둔다.
 */
@ConfigurationProperties(prefix = "app.knowledge.graph")
data class KnowledgeGraphProperties(
    val enabled: Boolean = false,
    val ontologyLocation: String = "classpath:ontology/knowledge-ontology-v1.json",
    val extractionModel: String = "qwen3.6:27b",
    val chunksPerRequest: Int = 4,
    val minimumConfidence: Double = 0.7,
    val maxEntitiesPerDocument: Int = 200,
    val maxRelationsPerDocument: Int = 400,
) {
    init {
        require(ontologyLocation.isNotBlank()) { "그래프 온톨로지 위치는 비어 있을 수 없습니다." }
        require(extractionModel.isNotBlank()) { "그래프 추출 모델명은 비어 있을 수 없습니다." }
    }
}

@Configuration
@EnableConfigurationProperties(KnowledgeGraphProperties::class)
class KnowledgeGraphConfig {
    /** Spring configuration 값을 프레임워크 비의존 애플리케이션 정책으로 변환한다. */
    @Bean
    fun knowledgeGraphProjectionPolicy(properties: KnowledgeGraphProperties) =
        KnowledgeGraphProjectionPolicy(
            enabled = properties.enabled,
            chunksPerRequest = properties.chunksPerRequest,
            minimumConfidence = properties.minimumConfidence,
            maxEntitiesPerDocument = properties.maxEntitiesPerDocument,
            maxRelationsPerDocument = properties.maxRelationsPerDocument,
        )
}
