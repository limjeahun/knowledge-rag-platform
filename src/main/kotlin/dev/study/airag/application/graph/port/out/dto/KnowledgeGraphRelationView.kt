package dev.study.airag.application.graph.port.out.dto

data class KnowledgeGraphRelationView(
    val relationId: String,
    val ontologyVersion: String,
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<KnowledgeGraphEvidenceView>,
)
