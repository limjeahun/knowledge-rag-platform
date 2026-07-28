package dev.study.airag.adapter.`in`.web.graph.response

data class KnowledgeGraphNeighborhoodResponse(
    val center: KnowledgeGraphEntityResponse,
    val entities: List<KnowledgeGraphEntityResponse>,
    val relations: List<KnowledgeGraphRelationResponse>,
)
