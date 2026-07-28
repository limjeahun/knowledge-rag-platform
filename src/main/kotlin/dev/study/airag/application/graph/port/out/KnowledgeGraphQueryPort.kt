package dev.study.airag.application.graph.port.out

import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphNeighborhoodView

/** 저장 기술을 노출하지 않고 개체 검색과 제한된 이웃 탐색을 제공한다. */
interface KnowledgeGraphQueryPort {
    fun searchEntities(query: SearchKnowledgeGraphQuery): List<KnowledgeGraphEntityView>

    fun findNeighborhood(query: GetKnowledgeEntityNeighborhoodQuery): KnowledgeGraphNeighborhoodView?
}
