package dev.study.airag.config.graph

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 온톨로지/그래프 프로젝션의 배포별 설정이다.
 *
 * 모델명, batch 크기, 신뢰도와 최대 건수는 업무 Aggregate 상수가 아니라 모델 성능과
 * 운영 자원에 따라 조정되는 기술 정책이므로 configuration에 둔다.
 */
@ConfigurationProperties(prefix = "app.knowledge.graph")
data class KnowledgeGraphProperties(
    val enabled: Boolean = false,
    val ontologyLocations: List<String> =
        listOf(
            "classpath:ontology/core/knowledge-core-v1.ttl",
            "classpath:ontology/domain/software-architecture-v1.ttl",
        ),
    val rootOntologyIri: String = "urn:airag:ontology:software-architecture",
    val shapesLocation: String = "classpath:ontology/shapes/software-architecture-shapes-v1.ttl",
    val fusekiDatasetUrl: String = "http://localhost:3030/knowledge",
    val hybridRetrievalEnabled: Boolean = true,
    val maxGraphFacts: Int = 20,
    val maxInferredStatements: Int = 2_000,
    val extractionModel: String = "qwen3.6:27b",
    val chunksPerRequest: Int = 4,
    val minimumConfidence: Double = 0.7,
    val maxEntitiesPerDocument: Int = 200,
    val maxRelationsPerDocument: Int = 400,
) {
    init {
        require(ontologyLocations.isNotEmpty() && ontologyLocations.none(String::isBlank)) {
            "OWL 온톨로지 위치는 하나 이상이어야 합니다."
        }
        require(rootOntologyIri.isNotBlank()) { "루트 OWL ontology IRI는 비어 있을 수 없습니다." }
        require(shapesLocation.isNotBlank()) { "SHACL shapes 위치는 비어 있을 수 없습니다." }
        require(fusekiDatasetUrl.isNotBlank()) { "Fuseki dataset URL은 비어 있을 수 없습니다." }
        require(maxGraphFacts in 1..100) { "GraphRAG 사실 수는 1 이상 100 이하이어야 합니다." }
        require(maxInferredStatements > 0) { "추론 statement 제한은 0보다 커야 합니다." }
        require(extractionModel.isNotBlank()) { "그래프 추출 모델명은 비어 있을 수 없습니다." }
    }
}
