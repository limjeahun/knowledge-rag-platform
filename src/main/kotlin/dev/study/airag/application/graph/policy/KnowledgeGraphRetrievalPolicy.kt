package dev.study.airag.application.graph.policy

/**
 * Hybrid GraphRAG 사실 조회의 활성화 여부와 최대 context 크기를 정의한다.
 *
 * LLM context와 SPARQL 비용에 영향을 주는 운영 정책이므로 Domain 상수가 아니라
 * Configuration에서 생성한다.
 */
data class KnowledgeGraphRetrievalPolicy(
    val enabled: Boolean,
    val maxFacts: Int,
    val maxSeedChunks: Int = 8,
    val maxHops: Int = 1,
) {
    init {
        require(maxFacts in 1..100) { "GraphRAG 사실 제한은 1 이상 100 이하여야 합니다." }
        require(maxSeedChunks > 0) { "그래프 검색의 최대 시드 청크 수는 양수여야 합니다." }
        require(maxHops in 0..2) { "그래프 검색의 최대 탐색 깊이는 0 이상 2 이하여야 합니다." }
    }
}
