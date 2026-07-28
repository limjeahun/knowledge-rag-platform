package dev.study.airag.application.graph.port.out.dto

/**
 * 그래프 저장소가 반환하는 기술 중립 조회 모델이다.
 *
 * JPA Entity를 애플리케이션이나 REST로 노출하지 않도록 별도 모델을 사용한다.
 */
data class KnowledgeGraphEntityView(
    val entityId: String,
    val ontologyVersion: String,
    val type: String,
    val name: String,
    val aliases: Set<String>,
    val evidence: List<KnowledgeGraphEvidenceView>,
)
