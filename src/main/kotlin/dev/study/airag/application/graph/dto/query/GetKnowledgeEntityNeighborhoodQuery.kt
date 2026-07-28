package dev.study.airag.application.graph.dto.query

/** 한 개체에서 제한된 단계만 이동하여 연결된 지식과 provenance를 조회한다. */
data class GetKnowledgeEntityNeighborhoodQuery(
    val entityId: String,
    val depth: Int = 1,
    val limit: Int = 50,
) {
    init {
        require(entityId.isNotBlank()) { "그래프 개체 ID는 비어 있을 수 없습니다." }
        require(depth in 1..2) { "그래프 탐색 depth는 1 이상 2 이하이어야 합니다." }
        require(limit in 1..100) { "그래프 탐색 limit은 1 이상 100 이하이어야 합니다." }
    }
}
