package dev.study.airag.application.graph.port.out.dto

/**
 * 현재 OWL version과 다른 활성 지식 그래프 프로젝션을 찾는 저장소 조회 조건이다.
 */
data class KnowledgeGraphReprojectionCriteria(
    val currentOntologyVersion: String,
    val limit: Int,
) {
    init {
        require(currentOntologyVersion.isNotBlank()) { "현재 온톨로지 버전은 비어 있을 수 없습니다." }
        require(limit > 0) { "재투영 후보 조회 개수는 양수여야 합니다." }
    }
}
