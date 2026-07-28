package dev.study.airag.config.ocr

import dev.study.airag.application.ocr.port.`in`.EvaluateOcrUseCase
import dev.study.airag.application.ocr.port.out.ExtractTextFromImagePort
import dev.study.airag.application.ocr.service.EvaluateOcrService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OcrEvaluationProperties::class)
class OcrEvaluationConfig {
    @Bean
    fun evaluateOcrUseCase(
        extractTextFromImagePort: ExtractTextFromImagePort,
        properties: OcrEvaluationProperties,
    ): EvaluateOcrUseCase = EvaluateOcrService(extractTextFromImagePort, properties.models)
}
