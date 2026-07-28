package dev.study.airag.application.knowledge.port.out

import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit

fun interface GenerateKnowledgeAnswerPort {
    /**
     * 제공된 근거 안에서만 사실을 구성해 질문에 답한다.
     *
     * 근거가 비어 있으면 모델을 호출하지 않고 근거 부족 답변을 반환한다.
     */
    fun generate(
        question: String,
        sources: List<KnowledgeSearchHit>,
    ): String
}
