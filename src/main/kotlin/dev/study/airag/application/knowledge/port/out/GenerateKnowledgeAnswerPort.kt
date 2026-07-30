package dev.study.airag.application.knowledge.port.out

import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit
import dev.study.airag.application.knowledge.port.out.dto.KnowledgeAnswerGenerationRequest

/**
 * 검색된 문서와 그래프 근거를 이용해 자연어 답변을 생성하는 외부 AI 능력이다.
 *
 * Application은 prompt, ChatClient와 모델 옵션을 알지 않으며 Adapter는 제공받지 않은 사실을
 * 근거처럼 생성해서는 안 된다.
 */
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

    /** Hybrid GraphRAG parameter object를 받아 문서 근거와 그래프 사실을 함께 사용한다. */
    fun generate(request: KnowledgeAnswerGenerationRequest): String = generate(request.question, request.sources)
}
