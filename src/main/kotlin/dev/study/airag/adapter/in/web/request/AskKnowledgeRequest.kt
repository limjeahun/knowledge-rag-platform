package dev.study.airag.adapter.`in`.web.request

import dev.study.airag.application.dto.query.AnswerKnowledgeQuestionQuery
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * 저장된 지식을 근거로 답변받기 위한 요청이다.
 *
 * 질문은 공백일 수 없고 topK는 1~20, similarityThreshold는 0.0~1.0 범위여야 한다.
 */
data class AskKnowledgeRequest(
    @field:NotBlank(message = "질문은 비어 있을 수 없습니다.")
    @field:Schema(description = "저장된 지식을 근거로 답할 질문", example = "문서 색인은 어떻게 재시도하나요?")
    val question: String,
    @field:Min(value = 1, message = "topK는 1 이상이어야 합니다.")
    @field:Max(value = 20, message = "topK는 20 이하이어야 합니다.")
    @field:Schema(description = "답변 근거로 사용할 최대 검색 결과 수", example = "5", minimum = "1", maximum = "20")
    val topK: Int = 5,
    @field:Schema(
        description = "답변 근거로 인정할 최소 유사도",
        example = "0.5",
        minimum = "0.0",
        maximum = "1.0",
    )
    val similarityThreshold: Double = 0.5,
) {
    /**
     * 질문을 검색에 사용할 수 있는 형태로 변환한다.
     */
    fun toQuery(): AnswerKnowledgeQuestionQuery =
        AnswerKnowledgeQuestionQuery(
            question = this.question,
            topK = this.topK,
            similarityThreshold = this.similarityThreshold,
        )
}
