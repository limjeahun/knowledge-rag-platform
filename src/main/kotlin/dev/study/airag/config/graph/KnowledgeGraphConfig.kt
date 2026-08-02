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
    /**
     * 외부 설정에서 그래프 생성 Use Case에 필요한 값만 Application policy로 투영한다.
     *
     * Fuseki URL, OWL 위치와 모델명 같은 Adapter 기술 설정은 포함하지 않는다. 이 분리로
     * Application Service가 Spring configuration이나 특정 제품을 알지 않게 한다.
     *
     * @param properties 환경 변수와 YAML이 binding된 전체 graph 설정
     * @return 활성화, batch, confidence와 문서별 개수 상한만 가진 정책
     */
    @Bean
    fun knowledgeGraphProjectionPolicy(properties: KnowledgeGraphProperties) =
        KnowledgeGraphProjectionPolicy(
            enabled = properties.enabled,
            chunksPerRequest = properties.chunksPerRequest,
            minimumConfidence = properties.minimumConfidence,
            maxEntitiesPerDocument = properties.maxEntitiesPerDocument,
            maxRelationsPerDocument = properties.maxRelationsPerDocument,
        )

    /**
     * 그래프 생성과 Hybrid GraphRAG flag가 모두 켜진 경우에만 사실 조회를 활성화한다.
     *
     * graph 자체가 꺼진 상태에서 retrieval만 켜도 Fuseki를 호출하지 않도록 두 flag를 논리곱한다.
     *
     * @param properties 외부 graph 및 hybrid retrieval 설정
     * @return 활성 여부와 질문당 최대 graph fact 수
     */
    @Bean
    fun knowledgeGraphRetrievalPolicy(properties: KnowledgeGraphProperties) =
        KnowledgeGraphRetrievalPolicy(
            enabled = properties.enabled && properties.hybridRetrievalEnabled,
            maxFacts = properties.maxGraphFacts,
            maxSeedChunks = properties.maxGraphSeedChunks,
            maxHops = properties.maxGraphHops,
        )
}
