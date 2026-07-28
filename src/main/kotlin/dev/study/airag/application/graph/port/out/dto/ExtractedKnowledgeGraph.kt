package dev.study.airag.application.graph.port.out.dto

/** LLM이 반환한 후보 그래프이며 아직 신뢰할 수 있는 지식으로 간주하지 않는다. */
data class ExtractedKnowledgeGraph(
    val entities: List<ExtractedGraphEntity>,
    val relations: List<ExtractedGraphRelation>,
)
