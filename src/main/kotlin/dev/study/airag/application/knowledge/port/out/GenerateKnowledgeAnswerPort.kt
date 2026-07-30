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
     *
     * @param question 사용자의 자연어 질문
     * @param sources vector 검색에서 선택된 문서 청크 근거
     * @return 외부 식별자나 citation 표현을 포함하지 않는 자연어 답변
     */
    fun generate(
        question: String,
        sources: List<KnowledgeSearchHit>,
    ): String

    /**
     * Hybrid GraphRAG parameter object를 받아 문서 근거와 그래프 사실을 함께 사용한다.
     *
     * 기본 구현은 기존 Adapter 호환성을 위해 graph fact를 무시하고 두 인자 메서드에 위임한다.
     * Hybrid를 지원하는 Adapter는 이 메서드를 override해야 한다.
     *
     * @param request 질문, vector source와 asserted/inferred graph fact
     * @return 근거 제한 자연어 답변
     */
    fun generate(request: KnowledgeAnswerGenerationRequest): String = generate(request.question, request.sources)
}
