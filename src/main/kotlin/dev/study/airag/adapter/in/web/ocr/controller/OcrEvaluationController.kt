package dev.study.airag.adapter.`in`.web.ocr.controller

import dev.study.airag.adapter.`in`.web.ocr.mapper.toCommand
import dev.study.airag.adapter.`in`.web.ocr.mapper.toResponse
import dev.study.airag.adapter.`in`.web.ocr.request.OcrEvaluationRequest
import dev.study.airag.adapter.`in`.web.ocr.response.OcrEvaluationResponse
import dev.study.airag.application.port.`in`.EvaluateOcrUseCase
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/ocr/evaluations")
class OcrEvaluationController(
    private val evaluateOcrUseCase: EvaluateOcrUseCase,
) : OcrEvaluationSpec {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun evaluate(
        @ModelAttribute request: OcrEvaluationRequest,
    ): OcrEvaluationResponse =
        evaluateOcrUseCase
            .evaluate(request.toCommand())
            .toResponse()
}
