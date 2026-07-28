package dev.study.airag.application.knowledge.exception

/** 기술 구현과 무관하게 호출자가 구분해야 하는 답변 생성 실패 유형이다. */
enum class KnowledgeAnswerGenerationFailure(
    val description: String,
) {
    OUTPUT_TRUNCATED("AI 모델이 응답 길이 한도 내에서 답변 생성을 완료하지 못했습니다."),
    EMPTY_RESPONSE("AI 모델이 빈 답변을 반환했습니다."),
    PROVIDER_CALL_FAILED("AI 답변 생성 서비스 호출에 실패했습니다."),
}
