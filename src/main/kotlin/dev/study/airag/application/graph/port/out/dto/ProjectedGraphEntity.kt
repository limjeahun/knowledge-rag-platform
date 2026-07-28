package dev.study.airag.application.graph.port.out.dto

data class ProjectedGraphEntity(
    val key: KnowledgeGraphEntityKey,
    val name: String,
    val aliases: Set<String>,
    val evidence: List<KnowledgeGraphEvidence>,
)
