package dev.study.airag.config.graph

import dev.study.airag.application.graph.policy.KnowledgeGraphProjectionPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
