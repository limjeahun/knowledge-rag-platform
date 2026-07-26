package dev.study.airag.config

import dev.study.airag.application.port.`in`.EvaluateOcrUseCase
import dev.study.airag.application.port.out.ExtractTextFromImagePort
import dev.study.airag.application.service.EvaluateOcrService
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "app.ocr")
data class OcrEvaluationProperties(
    val models: List<String> = emptyList(),
) {
    init {
        require(models.isNotEmpty()) { "app.ocr.models에 OCR 모델을 하나 이상 설정해야 합니다." }
        require(models.none(String::isBlank)) { "app.ocr.models의 모델명은 비어 있을 수 없습니다." }
        require(models.distinct().size == models.size) { "app.ocr.models에 중복 모델이 있습니다." }
    }
}

@Configuration
@EnableConfigurationProperties(OcrEvaluationProperties::class)
class OcrEvaluationConfig {
    @Bean
    fun evaluateOcrUseCase(
        extractTextFromImagePort: ExtractTextFromImagePort,
        properties: OcrEvaluationProperties,
    ): EvaluateOcrUseCase = EvaluateOcrService(extractTextFromImagePort, properties.models)
}
