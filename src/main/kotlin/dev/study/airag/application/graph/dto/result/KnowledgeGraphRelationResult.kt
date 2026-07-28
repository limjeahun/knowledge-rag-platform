package dev.study.airag.application.graph.dto.result

data class KnowledgeGraphRelationResult(
    val relationId: String,
    val ontologyVersion: String,
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<KnowledgeGraphEvidenceResult>,
)
