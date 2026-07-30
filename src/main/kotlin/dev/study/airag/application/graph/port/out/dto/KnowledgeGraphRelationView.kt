package dev.study.airag.application.graph.port.out.dto

import dev.study.airag.application.graph.dto.KnowledgeGraphAssertionKind

/** 개체 이웃 조회에서 반환하는 방향성 관계와 assertion provenance 요약이다. */
data class KnowledgeGraphRelationView(
    val relationId: String,
    val ontologyVersion: String,
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<KnowledgeGraphEvidenceView>,
    val assertionKind: KnowledgeGraphAssertionKind = KnowledgeGraphAssertionKind.ASSERTED,
)
