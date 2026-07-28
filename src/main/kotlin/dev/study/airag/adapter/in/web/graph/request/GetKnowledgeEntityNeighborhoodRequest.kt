package dev.study.airag.adapter.`in`.web.graph.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** 중심 개체에서 연결된 그래프를 탐색하는 HTTP 요청 파라미터다. */
data class GetKnowledgeEntityNeighborhoodRequest(
    @field:Min(value = 1, message = "그래프 탐색 depth는 1 이상이어야 합니다.")
    @field:Max(value = 2, message = "그래프 탐색 depth는 2 이하이어야 합니다.")
    @field:Schema(description = "관계 탐색 깊이", minimum = "1", maximum = "2", defaultValue = "1")
    val depth: Int = 1,
    @field:Min(value = 1, message = "그래프 탐색 limit은 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "그래프 탐색 limit은 100 이하이어야 합니다.")
    @field:Schema(description = "최대 개체 및 관계 수", minimum = "1", maximum = "100", defaultValue = "50")
    val limit: Int = 50,
)
