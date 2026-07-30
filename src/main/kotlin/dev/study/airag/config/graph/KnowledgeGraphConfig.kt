package dev.study.airag.config.graph

import dev.study.airag.application.graph.policy.KnowledgeGraphProjectionPolicy
import dev.study.airag.application.graph.policy.KnowledgeGraphRetrievalPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 외부 graph 설정을 프레임워크 비의존 Application policy로 조립하는 Spring configuration이다.
 *
 * OWL/Fuseki 기술 설정은 Adapter가 직접 사용하고, Use Case에는 활성화·batch·신뢰도·조회 상한처럼
 * orchestration에 필요한 값만 policy로 전달한다.
 */
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

    /** 그래프와 hybrid retrieval이 모두 활성화된 경우에만 SPARQL 사실 조회를 허용한다. */
    @Bean
    fun knowledgeGraphRetrievalPolicy(properties: KnowledgeGraphProperties) =
        KnowledgeGraphRetrievalPolicy(
            enabled = properties.enabled && properties.hybridRetrievalEnabled,
            maxFacts = properties.maxGraphFacts,
        )
}
