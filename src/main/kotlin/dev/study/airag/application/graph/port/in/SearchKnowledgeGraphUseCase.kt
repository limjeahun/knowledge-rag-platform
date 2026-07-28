package dev.study.airag.application.graph.port.`in`

import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.dto.result.KnowledgeGraphEntityResult

/** 이름과 ontology type을 이용해 provenance가 있는 그래프 개체를 검색한다. */
fun interface SearchKnowledgeGraphUseCase {
    fun search(query: SearchKnowledgeGraphQuery): List<KnowledgeGraphEntityResult>
}
