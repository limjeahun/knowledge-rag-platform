package dev.study.airag.application.graph.port.out.dto

/** 그래프 조회 Adapter가 반환하는 중심 개체 기준의 제한된 이웃 view다. */
data class KnowledgeGraphNeighborhoodView(
    val center: KnowledgeGraphEntityView,
    val entities: List<KnowledgeGraphEntityView>,
    val relations: List<KnowledgeGraphRelationView>,
)
