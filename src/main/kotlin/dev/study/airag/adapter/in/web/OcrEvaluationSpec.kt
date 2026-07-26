package dev.study.airag.adapter.`in`.web

import dev.study.airag.adapter.`in`.web.response.ApiErrorResponse
import dev.study.airag.adapter.`in`.web.response.OcrEvaluationResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.constraints.NotBlank
import org.springframework.web.multipart.MultipartFile

interface OcrEvaluationSpec {
    @Operation(
        summary = "LLM OCR 성능 평가",
        description =
            "이미지의 OCR 추출 결과를 정답 텍스트와 비교해 CER, WER, 정확도와 모델 호출 시간을 반환합니다. " +
                "모델을 생략하면 설정된 우선순위에 따라 폴백합니다.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "OCR 성능 평가 성공",
                    content = [Content(schema = Schema(implementation = OcrEvaluationResponse::class))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "이미지, 정답 텍스트 또는 모델명이 유효하지 않음",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "413",
                    description = "업로드 이미지가 허용 크기를 초과함",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "415",
                    description = "지원하지 않는 이미지 형식",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
                ApiResponse(
                    responseCode = "502",
                    description = "요청한 OCR 모델 또는 모든 폴백 모델 호출 실패",
                    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
                ),
            ],
    )
    fun evaluate(
        @Parameter(description = "PNG, JPEG 또는 WebP 이미지")
        image: MultipartFile,
        @Parameter(description = "이미지에 실제로 표시된 정답 텍스트")
        @NotBlank
        groundTruth: String,
        @Parameter(
            description =
                "명시하면 해당 모델만 평가합니다. 생략하면 qwen3.6:27b부터 시작해 gemma4:31b로 폴백합니다.",
            example = "qwen3.6:27b",
        )
        model: String?,
    ): OcrEvaluationResponse
}
