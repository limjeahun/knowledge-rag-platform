package dev.study.airag.application.port.`in`

import dev.study.airag.application.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.dto.result.KnowledgeGraphNeighborhoodResult

/** 개체를 중심으로 제한된 깊이의 관계와 연결 개체를 조회한다. */
fun interface GetKnowledgeEntityNeighborhoodUseCase {
    fun getNeighborhood(query: GetKnowledgeEntityNeighborhoodQuery): KnowledgeGraphNeighborhoodResult
}
