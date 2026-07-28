package dev.study.airag.application.dto.query

/** 이름 일부와 선택적 ontology type으로 지식 그래프 개체를 찾는다. */
data class SearchKnowledgeGraphQuery(
    val text: String,
    val type: String? = null,
    val limit: Int = 20,
)
