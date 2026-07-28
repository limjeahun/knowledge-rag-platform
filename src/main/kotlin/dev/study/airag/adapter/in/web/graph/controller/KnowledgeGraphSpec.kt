package dev.study.airag.adapter.`in`.web.graph.controller

import dev.study.airag.adapter.`in`.web.common.response.ApiErrorResponse
import dev.study.airag.adapter.`in`.web.graph.response.KnowledgeGraphEntityResponse
import dev.study.airag.adapter.`in`.web.graph.response.KnowledgeGraphNeighborhoodResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Knowledge Graph", description = "온톨로지 기반 개체·관계와 원문 provenance 읽기 API")
interface KnowledgeGraphSpec {
    @Operation(
        summary = "지식 그래프 개체 검색",
        description = "대표 이름과 선택적 ontology type으로 개체를 검색하고 원문 근거를 반환한다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "개체 검색 성공",
                content = [
                    Content(
                        array =
                            ArraySchema(
                                schema = Schema(implementation = KnowledgeGraphEntityResponse::class),
                            ),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "검색 조건 또는 ontology type이 유효하지 않음",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
            ),
        ],
    )
    fun searchEntities(
        @Parameter(description = "개체 대표 이름에 포함될 문자열")
        query: String,
        @Parameter(description = "ontology 개체 타입", example = "TECHNOLOGY")
        type: String?,
        @Parameter(description = "최대 결과 수(1~100)")
        limit: Int,
    ): List<KnowledgeGraphEntityResponse>

    @Operation(
        summary = "지식 그래프 개체 이웃 조회",
        description = "한 개체에서 1~2단계로 연결된 개체·관계와 각 원문 근거를 반환한다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "이웃 그래프 조회 성공",
                content = [Content(schema = Schema(implementation = KnowledgeGraphNeighborhoodResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "탐색 깊이 또는 최대 결과 수가 유효하지 않음",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "그래프 개체를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
            ),
        ],
    )
    fun getNeighborhood(
        @Parameter(description = "중심 개체 UUID")
        entityId: String,
        @Parameter(description = "관계 탐색 깊이(1~2)")
        depth: Int,
        @Parameter(description = "최대 개체 및 관계 수(1~100)")
        limit: Int,
    ): KnowledgeGraphNeighborhoodResponse
}
