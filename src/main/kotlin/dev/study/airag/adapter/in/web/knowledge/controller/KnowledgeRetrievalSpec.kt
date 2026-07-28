package dev.study.airag.adapter.`in`.web.knowledge.controller

import dev.study.airag.adapter.`in`.web.common.response.ApiErrorResponse
import dev.study.airag.adapter.`in`.web.knowledge.request.AskKnowledgeRequest
import dev.study.airag.adapter.`in`.web.knowledge.response.KnowledgeAnswerResponse
import dev.study.airag.adapter.`in`.web.knowledge.response.KnowledgeSearchHitResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Knowledge", description = "지식 문서의 색인 수명주기와 RAG 검색·답변 API")
interface KnowledgeRetrievalSpec {
    @Operation(summary = "지식 검색", description = "저장된 지식에서 질의문과 의미가 가까운 문서 근거를 검색한다.")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "유사도순 검색 결과",
                    content =
                        [
                            Content(
                                array =
                                    ArraySchema(
                                        schema = Schema(implementation = KnowledgeSearchHitResponse::class),
                                    ),
                            ),
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "검색어 또는 검색 범위가 유효하지 않음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
            ],
    )
    fun search(
        @Parameter(description = "검색할 자연어 질의", example = "색인 실패 재시도")
        query: String,
        @Parameter(description = "반환할 최대 검색 결과 수(1~20)", example = "5")
        topK: Int,
        @Parameter(description = "검색 결과로 인정할 최소 유사도(0.0~1.0)", example = "0.5")
        similarityThreshold: Double,
    ): List<KnowledgeSearchHitResponse>

    @Operation(summary = "지식 기반 질문", description = "검색된 지식 근거를 사용해 답변하고 실제 사용한 출처를 반환한다.")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "근거 기반 답변 생성 성공",
                    content = [Content(schema = Schema(implementation = KnowledgeAnswerResponse::class))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "질문 또는 검색 범위가 유효하지 않음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "415",
                    description = "지원하지 않는 요청 본문 형식",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
            ],
    )
    fun chat(request: AskKnowledgeRequest): KnowledgeAnswerResponse
}
