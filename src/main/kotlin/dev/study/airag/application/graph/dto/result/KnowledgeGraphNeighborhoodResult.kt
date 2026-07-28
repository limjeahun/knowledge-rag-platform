package dev.study.airag.application.graph.dto.result

data class KnowledgeGraphNeighborhoodResult(
    val center: KnowledgeGraphEntityResult,
    val entities: List<KnowledgeGraphEntityResult>,
    val relations: List<KnowledgeGraphRelationResult>,
)
