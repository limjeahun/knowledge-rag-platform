package dev.study.airag.application.port.`in`

import dev.study.airag.application.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.dto.result.KnowledgeSearchHit

fun interface SearchKnowledgeUseCase {
    /** 검색 조건을 검증하고 유사도 기준을 충족한 근거를 반환한다. */
    fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit>
}
