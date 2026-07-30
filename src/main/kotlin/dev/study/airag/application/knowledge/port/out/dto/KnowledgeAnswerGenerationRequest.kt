package dev.study.airag.application.knowledge.port.out.dto

import dev.study.airag.application.graph.dto.result.KnowledgeGraphFactResult
import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit

/**
 * 한 번의 답변 생성에 사용하는 질문, vector source와 semantic graph fact를 묶는다.
 *
 * 검색 결과를 Adapter 내부에서 다시 조회하지 않게 하여 답변에 사용한 근거와 API가 반환하는
 * 근거가 정확히 일치하도록 한다.
 */
data class KnowledgeAnswerGenerationRequest(
    val question: String,
    val sources: List<KnowledgeSearchHit>,
    val graphFacts: List<KnowledgeGraphFactResult>,
)
