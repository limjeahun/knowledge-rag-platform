package dev.study.airag.application.graph.port.`in`

import dev.study.airag.application.graph.dto.query.FindRelevantKnowledgeGraphFactsQuery
import dev.study.airag.application.graph.dto.result.KnowledgeGraphFactResult

/**
 * 질문과 관련된 asserted/inferred 그래프 사실을 제한된 크기로 조회하는 읽기 Use Case다.
 *
 * 그래프 기능이 비활성화된 경우 빈 목록을 반환하며 외부 저장소를 호출하지 않는다.
 */
fun interface FindRelevantKnowledgeGraphFactsUseCase {
    fun findRelevantFacts(query: FindRelevantKnowledgeGraphFactsQuery): List<KnowledgeGraphFactResult>
}
