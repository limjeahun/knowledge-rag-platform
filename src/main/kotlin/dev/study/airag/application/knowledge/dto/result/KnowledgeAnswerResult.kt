package dev.study.airag.application.knowledge.dto.result

import dev.study.airag.application.graph.dto.result.KnowledgeGraphFactResult

/**
 * 저장된 지식으로 생성한 답변과 실제 사용한 vector·graph 근거다.
 *
 * [sources]와 [graphFacts]는 모델 호출에 전달한 동일 객체이며 응답 소비자가 답변의 출처와
 * asserted/inferred 구분을 감사할 수 있게 한다.
 */
data class KnowledgeAnswerResult(
    val question: String,
    val answer: String,
    val sources: List<KnowledgeSearchHit>,
    val graphFacts: List<KnowledgeGraphFactResult> = emptyList(),
)
