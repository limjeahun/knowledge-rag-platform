package dev.study.airag.application.graph.port.out.dto

data class ProjectedGraphRelation(
    val type: String,
    val source: KnowledgeGraphEntityKey,
    val target: KnowledgeGraphEntityKey,
    val evidence: List<KnowledgeGraphEvidence>,
)
