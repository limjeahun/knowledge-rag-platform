package dev.study.airag.adapter.`in`.web.knowledge.response

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 검색 또는 답변의 근거가 된 문서 일부다.
 *
 * chunkId와 문서 버전으로 출처를 추적한다.
 */
data class KnowledgeSearchHitResponse(
    @field:Schema(description = "검색 근거 청크 식별자")
    val chunkId: String,
    @field:Schema(description = "원본 문서 UUID")
    val documentId: String,
    @field:Schema(description = "검색 근거를 생성한 문서 버전")
    val documentVersion: Long,
    @field:Schema(description = "문서 안에서 청크의 순서")
    val chunkIndex: Int,
    @field:Schema(description = "원본 문서 제목")
    val title: String,
    @field:Schema(description = "검색 또는 답변의 근거가 된 본문 일부")
    val content: String,
    @field:Schema(description = "질의와 근거의 유사도", nullable = true)
    val score: Double?,
    @field:Schema(description = "원본 문서에서 전달된 부가 정보")
    val metadata: Map<String, String>,
)

/** 질문에 대한 답변과 실제로 사용한 문서 근거다. */
data class KnowledgeAnswerResponse(
    @field:Schema(description = "사용자가 요청한 질문")
    val question: String,
    @field:Schema(description = "검색된 근거만을 사용해 생성한 답변")
    val answer: String,
    @field:Schema(description = "답변 생성에 실제 사용한 문서 근거")
    val sources: List<KnowledgeSearchHitResponse>,
)
