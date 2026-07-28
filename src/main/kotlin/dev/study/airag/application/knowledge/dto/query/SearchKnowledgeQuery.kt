package dev.study.airag.application.knowledge.dto.query

/**
 * 저장된 지식에서 질문과 의미가 가까운 근거를 찾는 조건이다.
 *
 * topK는 1~20, similarityThreshold는 0.0~1.0 범위여야 한다.
 */
data class SearchKnowledgeQuery(
    val query: String,
    val topK: Int = 5,
    val similarityThreshold: Double = 0.5,
) {
    init {
        require(query.isNotBlank()) { "검색어는 비어 있을 수 없습니다." }
        require(topK in 1..20) { "topK는 1 이상 20 이하이어야 합니다." }
        require(similarityThreshold in 0.0..1.0) {
            "similarityThreshold는 0.0 이상 1.0 이하이어야 합니다."
        }
    }
}
