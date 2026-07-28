package dev.study.airag.application.graph.service

import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.dto.result.KnowledgeGraphEntityResult
import dev.study.airag.application.graph.dto.result.KnowledgeGraphNeighborhoodResult
import dev.study.airag.application.graph.exception.KnowledgeGraphEntityNotFoundException
import dev.study.airag.application.graph.mapper.toResult
import dev.study.airag.application.graph.mapper.toResults
import dev.study.airag.application.graph.port.`in`.GetKnowledgeEntityNeighborhoodUseCase
import dev.study.airag.application.graph.port.`in`.SearchKnowledgeGraphUseCase
import dev.study.airag.application.graph.port.out.KnowledgeGraphQueryPort
import dev.study.airag.application.graph.port.out.KnowledgeOntologyPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 지식 그래프를 이름으로 찾거나 개체 중심의 제한된 이웃 구조로 조회한다.
 */
@Service
class QueryKnowledgeGraphService(
    private val ontologyPort: KnowledgeOntologyPort,
    private val queryPort: KnowledgeGraphQueryPort,
) : SearchKnowledgeGraphUseCase,
    GetKnowledgeEntityNeighborhoodUseCase {
    @Transactional(readOnly = true)
    override fun search(query: SearchKnowledgeGraphQuery): List<KnowledgeGraphEntityResult> {
        val normalizedQuery = query.normalized()
        if (normalizedQuery.type != null) {
            // 
            require(normalizedQuery.type in ontologyPort.load().entityTypesByCode) {
                "온톨로지에 없는 개체 타입입니다: ${normalizedQuery.type}"
            }
        }
        return queryPort.searchEntities(normalizedQuery).toResults()
    }

    /**
     * 무제한 그래프 순회가 큰 조회와 순환을 만들지 않도록 깊이와 반환 건수를 제한한다.
     */
    @Transactional(readOnly = true)
    override fun getNeighborhood(query: GetKnowledgeEntityNeighborhoodQuery): KnowledgeGraphNeighborhoodResult {
        val neighborhood =
            queryPort.findNeighborhood(query)
                ?: throw KnowledgeGraphEntityNotFoundException(query.entityId)
        return neighborhood.toResult()
    }
}
