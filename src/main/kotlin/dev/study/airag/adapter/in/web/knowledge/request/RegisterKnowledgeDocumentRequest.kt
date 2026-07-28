package dev.study.airag.adapter.`in`.web.knowledge.request

import dev.study.airag.application.dto.command.RegisterKnowledgeDocumentCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/** 검색과 답변에 사용할 원본 지식을 등록하며 제목과 본문은 공백일 수 없다. */
data class RegisterKnowledgeDocumentRequest(
    @field:NotBlank(message = "제목은 비어 있을 수 없습니다.")
    @field:Schema(description = "문서를 식별할 수 있는 제목", example = "RAG 운영 가이드")
    val title: String,
    @field:NotBlank(message = "본문은 비어 있을 수 없습니다.")
    @field:Schema(description = "검색과 답변의 원본이 되는 문서 본문", example = "RAG는 검색된 근거를 바탕으로 답변합니다.")
    val content: String,
    @field:Schema(description = "검색 필터와 출처 추적에 사용할 부가 정보")
    val metadata: Map<String, String> = emptyMap(),
) {
    fun toCommand(): RegisterKnowledgeDocumentCommand = RegisterKnowledgeDocumentCommand(title, content, metadata)
}
