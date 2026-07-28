package dev.study.airag.application.graph.port.out.dto

data class KnowledgeGraphNeighborhoodView(
    val center: KnowledgeGraphEntityView,
    val entities: List<KnowledgeGraphEntityView>,
    val relations: List<KnowledgeGraphRelationView>,
)
