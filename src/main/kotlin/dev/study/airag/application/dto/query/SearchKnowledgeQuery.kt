package dev.study.airag.application.dto.query

/**
 * 저장된 지식에서 질문과 의미가 가까운 근거를 찾는 조건이다.
 *
 * topK는 1~20, similarityThreshold는 0.0~1.0 범위여야 한다.
 */
data class SearchKnowledgeQuery(
    val query: String,
    val topK: Int = 5,
    val similarityThreshold: Double = 0.5,
)
