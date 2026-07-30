package dev.study.airag.adapter.`in`.web.graph.response

import io.swagger.v3.oas.annotations.media.Schema

/** 방향성 관계와 양 끝점, 원문 provenance를 함께 반환한다. */
data class KnowledgeGraphRelationResponse(
    val relationId: String,
    val ontologyVersion: String,
    @field:Schema(example = "USES")
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<KnowledgeGraphEvidenceResponse>,
    val assertionKind: String,
)
