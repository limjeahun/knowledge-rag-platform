package dev.study.airag.application.graph.dto.result

import dev.study.airag.application.graph.dto.KnowledgeGraphAssertionKind

/**
 * 답변 생성과 REST/MCP 응답이 공유하는 방향성 있는 지식 그래프 사실이다.
 *
 * [assertionKind]가 `ASSERTED`일 때만 [evidence]가 원문 인용을 포함할 수 있다. `INFERRED`
 * 사실의 빈 evidence는 근거 누락이 아니라 ontology entailment라는 의미다.
 */
data class KnowledgeGraphFactResult(
    val relationId: String,
    val ontologyVersion: String,
    val assertionKind: KnowledgeGraphAssertionKind,
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<KnowledgeGraphEvidenceResult>,
)
