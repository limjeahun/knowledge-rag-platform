package dev.study.airag.adapter.`in`.web

import dev.study.airag.adapter.`in`.web.request.AskKnowledgeRequest
import dev.study.airag.adapter.`in`.web.request.RegisterKnowledgeDocumentRequest
import dev.study.airag.adapter.`in`.web.response.ApiErrorResponse
import dev.study.airag.adapter.`in`.web.response.KnowledgeAnswerResponse
import dev.study.airag.adapter.`in`.web.response.KnowledgeDocumentResponse
import dev.study.airag.adapter.`in`.web.response.KnowledgeSearchHitResponse
import dev.study.airag.adapter.`in`.web.response.RegisteredKnowledgeDocumentResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Knowledge", description = "지식 문서의 색인 수명주기와 RAG 검색·답변 API")
interface KnowledgeSpec {
    @Operation(summary = "지식 문서 등록", description = "원본 문서를 저장하고 비동기 색인을 접수한다.")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "202",
                    description = "문서 등록과 색인 요청이 접수됨",
                    content = [Content(schema = Schema(implementation = RegisteredKnowledgeDocumentResponse::class))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "제목 또는 본문이 유효하지 않음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "415",
                    description = "지원하지 않는 요청 본문 형식",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "502",
                    description = "AI 답변 공급자가 답변 생성을 완료하지 못함",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
            ],
    )
    fun register(request: RegisterKnowledgeDocumentRequest): RegisteredKnowledgeDocumentResponse

    @Operation(summary = "지식 문서 조회", description = "원본 본문을 제외한 문서 정보와 현재 색인 상태를 조회한다.")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "문서 정보 조회 성공",
                    content = [Content(schema = Schema(implementation = KnowledgeDocumentResponse::class))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "문서 ID 형식이 유효하지 않음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "문서를 찾을 수 없음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
            ],
    )
    fun get(
        @Parameter(description = "조회할 문서 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        documentId: String,
    ): KnowledgeDocumentResponse

    @Operation(summary = "문서 색인 재시도", description = "마지막 색인에 실패한 문서를 다시 대기 상태로 전환한다.")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "202",
                    description = "재색인 요청이 접수됨",
                    content = [Content(schema = Schema(implementation = RegisteredKnowledgeDocumentResponse::class))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "문서 ID 형식이 유효하지 않음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "문서를 찾을 수 없음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "409",
                    description = "현재 문서 상태가 재시도 조건에 맞지 않음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
            ],
    )
    fun retry(
        @Parameter(description = "재색인할 문서 UUID")
        documentId: String,
    ): RegisteredKnowledgeDocumentResponse

    @Operation(
        summary = "지식 문서 삭제",
        description = "문서를 삭제 상태로 전환하고 검색 인덱스 제거를 비동기로 요청한다.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "204", description = "문서 삭제가 접수됨"),
                ApiResponse(
                    responseCode = "400",
                    description = "문서 ID 형식이 유효하지 않음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "문서를 찾을 수 없음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
            ],
    )
    fun delete(
        @Parameter(description = "삭제할 문서 UUID")
        documentId: String,
    )

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
