package dev.study.airag.application.knowledge.exception

/** 외부 AI 답변 생성 능력이 정상적인 지식 답변을 반환하지 못했음을 나타낸다. */
class KnowledgeAnswerGenerationException(
    val failure: KnowledgeAnswerGenerationFailure,
    cause: Throwable? = null,
) : RuntimeException(failure.description, cause)
