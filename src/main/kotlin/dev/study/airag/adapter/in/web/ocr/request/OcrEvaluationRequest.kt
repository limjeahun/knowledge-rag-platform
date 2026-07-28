package dev.study.airag.adapter.`in`.web.ocr.request

import dev.study.airag.application.ocr.dto.command.EvaluateOcrCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import org.springframework.web.multipart.MultipartFile

/** OCR 평가에 필요한 이미지와 비교 조건을 하나의 multipart 요청으로 바인딩한다. */
data class OcrEvaluationRequest(
    @field:Schema(
        description = "PNG, JPEG 또는 WebP 이미지",
        type = "string",
        format = "binary",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val image: MultipartFile? = null,
    @field:NotBlank(message = "정답 텍스트는 비어 있을 수 없습니다.")
    @field:Schema(
        description = "이미지에 실제로 표시된 정답 텍스트",
        example = "문서 번호 123",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groundTruth: String = "",
    @field:Schema(
        description = "명시하면 해당 모델만 평가하며 생략하면 설정된 우선순위에 따라 폴백합니다.",
        example = "qwen3.6:27b",
    )
    val model: String? = null,
)
