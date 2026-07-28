package dev.study.airag.application.graph.port.out.dto

/** 같은 모델 응답에 포함된 두 개체 후보 사이의 관계 후보다. */
data class ExtractedGraphRelation(
    val type: String,
    val sourceKey: String,
    val targetKey: String,
    val confidence: Double,
    val evidence: List<ExtractedGraphEvidence>,
)
