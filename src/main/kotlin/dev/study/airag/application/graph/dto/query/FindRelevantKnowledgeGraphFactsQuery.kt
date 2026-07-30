package dev.study.airag.application.graph.dto.query

/**
 * Hybrid GraphRAG 답변에 사용할 제한된 관련 subgraph 사실 조회 조건이다.
 *
 * [text]는 질문 또는 검색 문장이고 [limit]은 Adapter가 반환할 최대 관계 수다. Application
 * policy가 이 값보다 더 작은 운영 상한을 적용할 수 있다.
 */
data class FindRelevantKnowledgeGraphFactsQuery(
    val text: String,
    val limit: Int = 20,
) {
    init {
        require(text.isNotBlank()) { "그래프 사실 검색어는 비어 있을 수 없습니다." }
        require(limit in 1..100) { "그래프 사실 검색 limit은 1 이상 100 이하여야 합니다." }
    }
}
