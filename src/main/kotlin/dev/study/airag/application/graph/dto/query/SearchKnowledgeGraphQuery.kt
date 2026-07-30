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

    /**
     * 외부 경계에서 들어온 검색 문자열을 비교에 적합한 불변 Query로 정규화한다.
     *
     * 본문 양끝 공백을 제거하고 빈 type 문자열을 `null`로 통일한다. 원본 객체를 변경하지
     * 않으며 대소문자는 저장 Adapter가 SPARQL 비교 정책에 맞게 처리한다.
     *
     * @return 정규화된 새 Query
     */
    internal fun normalized(): SearchKnowledgeGraphQuery =
        copy(
            text = text.trim(),
            type = type?.trim()?.takeIf(String::isNotEmpty),
        )
}
