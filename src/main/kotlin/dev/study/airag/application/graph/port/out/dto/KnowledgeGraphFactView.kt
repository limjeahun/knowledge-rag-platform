package dev.study.airag.application.graph.port.out.dto

import dev.study.airag.application.graph.dto.KnowledgeGraphAssertionKind

/**
 * 그래프 조회 Adapter가 Application으로 전달하는 기술 독립적 관계 사실이다.
 *
 * Jena QuerySolution, RDFNode, graph IRI 같은 저장 기술 정보는 이 경계를 통과하지 않는다.
 */
data class KnowledgeGraphFactView(
    val relationId: String,
    val ontologyVersion: String,
    val assertionKind: KnowledgeGraphAssertionKind,
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<KnowledgeGraphEvidenceView>,
)
