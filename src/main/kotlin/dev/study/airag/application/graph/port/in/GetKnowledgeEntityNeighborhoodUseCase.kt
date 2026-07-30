package dev.study.airag.application.graph.port.`in`

import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.result.KnowledgeGraphNeighborhoodResult

/** 개체를 중심으로 제한된 깊이의 관계와 연결 개체를 조회한다. */
fun interface GetKnowledgeEntityNeighborhoodUseCase {
    /**
     * 활성 그래프에서 중심 개체의 제한된 1~2 hop 이웃을 조회한다.
     *
     * @param query 중심 entity ID와 검증된 depth·limit
     * @return 중심 개체, 연결 개체 및 asserted/inferred 관계
     * @throws KnowledgeGraphEntityNotFoundException 중심 개체가 활성 그래프에 없는 경우
     */
    fun getNeighborhood(query: GetKnowledgeEntityNeighborhoodQuery): KnowledgeGraphNeighborhoodResult
}
