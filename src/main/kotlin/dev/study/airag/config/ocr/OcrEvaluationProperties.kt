package dev.study.airag.config.ocr

import org.springframework.boot.context.properties.ConfigurationProperties

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
