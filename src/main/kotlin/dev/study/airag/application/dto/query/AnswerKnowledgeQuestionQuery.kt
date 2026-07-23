package dev.study.airag.application.dto.query

/**
 * 저장된 지식만을 근거로 질문에 답하기 위한 조건이다.
 *
 * topK는 1~20, similarityThreshold는 0.0~1.0 범위여야 한다.
 */
data class AnswerKnowledgeQuestionQuery(
    val question: String,
    val topK: Int = 5,
    val similarityThreshold: Double = 0.5,
)
