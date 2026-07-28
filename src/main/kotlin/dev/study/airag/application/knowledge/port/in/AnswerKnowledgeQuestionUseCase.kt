package dev.study.airag.application.knowledge.port.`in`

import dev.study.airag.application.knowledge.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.knowledge.dto.result.KnowledgeAnswerResult

fun interface AnswerKnowledgeQuestionUseCase {
    /**
     * 질문과 관련된 근거를 한 번 검색하고 같은 근거 집합으로 답변을 생성한다.
     */
    fun answer(query: AnswerKnowledgeQuestionQuery): KnowledgeAnswerResult
}
