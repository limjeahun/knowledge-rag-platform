package dev.study.airag.adapter.`in`.web.graph.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/** 이름 일부와 선택적 ontology type으로 그래프 개체를 검색하는 HTTP 요청이다. */
data class SearchKnowledgeGraphRequest(
    @field:NotBlank(message = "그래프 검색어는 비어 있을 수 없습니다.")
    @field:Schema(description = "개체 대표 이름에 포함될 문자열")
    val query: String = "",
    @field:Schema(description = "ontology 개체 타입", example = "TECHNOLOGY", nullable = true)
    val type: String? = null,
    @field:Min(value = 1, message = "그래프 검색 limit은 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "그래프 검색 limit은 100 이하이어야 합니다.")
    @field:Schema(description = "최대 결과 수", minimum = "1", maximum = "100", defaultValue = "20")
    val limit: Int = 20,
)
