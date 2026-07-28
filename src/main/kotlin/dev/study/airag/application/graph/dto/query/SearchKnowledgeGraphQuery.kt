package dev.study.airag.application.graph.dto.query

/** 이름 일부와 선택적 ontology type으로 지식 그래프 개체를 찾는다. */
data class SearchKnowledgeGraphQuery(
    val text: String,
    val type: String? = null,
    val limit: Int = 20,
) {
    init {
        require(text.isNotBlank()) { "그래프 검색어는 비어 있을 수 없습니다." }
        require(limit in 1..100) { "그래프 검색 limit은 1 이상 100 이하이어야 합니다." }
    }

    internal fun normalized(): SearchKnowledgeGraphQuery =
        copy(
            text = text.trim(),
            type = type?.trim()?.takeIf(String::isNotEmpty),
        )
}
