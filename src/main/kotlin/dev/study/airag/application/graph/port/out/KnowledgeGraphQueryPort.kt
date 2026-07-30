package dev.study.airag.application.graph.port.out

import dev.study.airag.application.graph.dto.query.FindRelevantKnowledgeGraphFactsQuery
import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphFactView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphNeighborhoodView

/**
 * 저장 기술을 노출하지 않고 개체 검색, 제한된 이웃 탐색과 관련 사실 조회를 제공한다.
 *
 * 구현체는 asserted/inferred 구분과 provenance를 보존하되 Jena·SPARQL 타입을 반환해서는 안 된다.
 */
interface KnowledgeGraphQueryPort {
    /** 정규화된 이름과 선택적 ontology code에 맞는 asserted 개체를 찾는다. */
    fun searchEntities(query: SearchKnowledgeGraphQuery): List<KnowledgeGraphEntityView>

    /** 중심 개체가 없으면 `null`, 있으면 depth/limit이 적용된 이웃 view를 반환한다. */
    fun findNeighborhood(query: GetKnowledgeEntityNeighborhoodQuery): KnowledgeGraphNeighborhoodView?

    /** Hybrid GraphRAG context에 사용할 방향성 asserted/inferred 사실을 반환한다. */
    fun findRelevantFacts(query: FindRelevantKnowledgeGraphFactsQuery): List<KnowledgeGraphFactView>
}
