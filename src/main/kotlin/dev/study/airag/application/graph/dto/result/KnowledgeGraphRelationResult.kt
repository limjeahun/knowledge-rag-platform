package dev.study.airag.application.graph.dto.result

import dev.study.airag.application.graph.dto.KnowledgeGraphAssertionKind

/**
 * HTTP나 RDF 기술 타입을 모르는 방향성 그래프 관계 조회 결과다.
 *
 * source/target 이름을 함께 보존해 소비자가 추가 개체 조회 없이 관계를 표시할 수 있다.
 */
data class KnowledgeGraphRelationResult(
    val relationId: String,
    val ontologyVersion: String,
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<KnowledgeGraphEvidenceResult>,
    val assertionKind: KnowledgeGraphAssertionKind = KnowledgeGraphAssertionKind.ASSERTED,
)
