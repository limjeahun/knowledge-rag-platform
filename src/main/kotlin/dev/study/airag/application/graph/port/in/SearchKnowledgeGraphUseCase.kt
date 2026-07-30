package dev.study.airag.application.graph.port.`in`

import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.dto.result.KnowledgeGraphEntityResult

/** 이름과 ontology type을 이용해 provenance가 있는 그래프 개체를 검색한다. */
fun interface SearchKnowledgeGraphUseCase {
    /**
     * 검증된 limit 안에서 이름과 선택적 ontology type에 맞는 활성 개체를 찾는다.
     *
     * @param query 사용자 검색 문자열, 선택적 type code와 결과 상한
     * @return 일치하는 개체와 직접 원문 evidence, 없으면 빈 목록
     */
    fun search(query: SearchKnowledgeGraphQuery): List<KnowledgeGraphEntityResult>
}
