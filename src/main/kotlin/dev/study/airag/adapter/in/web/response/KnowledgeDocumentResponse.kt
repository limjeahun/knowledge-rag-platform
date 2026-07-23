package dev.study.airag.adapter.`in`.web.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * 등록된 문서의 현재 색인 진행 상황이다.
 *
 * 실패 상태가 아니면 failureReason은 없고, 색인이 완료되지 않았으면 indexedAt은 없다.
 */
data class KnowledgeDocumentResponse(
    @field:Schema(description = "문서 UUID")
    val documentId: String,
    @field:Schema(description = "문서 제목")
    val title: String,
    @field:Schema(description = "현재 원본 문서 버전")
    val version: Long,
    @field:Schema(
        description = "현재 색인 상태",
        allowableValues = ["PENDING", "INDEXING", "INDEXED", "FAILED", "DELETED"],
    )
    val status: String,
    @field:Schema(description = "마지막 색인 실패 사유. 실패 상태가 아니면 null", nullable = true)
    val failureReason: String?,
    @field:Schema(description = "문서 등록 시각")
    val registeredAt: Instant,
    @field:Schema(description = "현재 버전 색인 완료 시각. 완료 전이면 null", nullable = true)
    val indexedAt: Instant?,
)
