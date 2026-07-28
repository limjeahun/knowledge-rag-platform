package dev.study.airag.application.knowledge.dto.result

/** 저장된 지식으로 생성한 답변과 실제 사용한 근거다. */
data class KnowledgeAnswerResult(
    val question: String,
    val answer: String,
    val sources: List<KnowledgeSearchHit>,
)
