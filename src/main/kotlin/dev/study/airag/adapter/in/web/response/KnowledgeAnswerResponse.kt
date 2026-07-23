package dev.study.airag.adapter.`in`.web.response

import io.swagger.v3.oas.annotations.media.Schema

/** 질문에 대한 답변과 실제로 사용한 문서 근거다. */
data class KnowledgeAnswerResponse(
    @field:Schema(description = "사용자가 요청한 질문")
    val question: String,
    @field:Schema(description = "검색된 근거만을 사용해 생성한 답변")
    val answer: String,
    @field:Schema(description = "답변 생성에 실제 사용한 문서 근거")
    val sources: List<KnowledgeSearchHitResponse>,
)
