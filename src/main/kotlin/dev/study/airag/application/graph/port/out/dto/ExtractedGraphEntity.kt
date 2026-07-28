package dev.study.airag.application.graph.port.out.dto

/**
 * 모델 응답 안에서만 유효한 localKey로 식별되는 개체 후보다.
 *
 * localKey는 관계의 끝점을 연결하기 위한 임시 값일 뿐 영구 ID가 아니다. 영구 개체 동일성은
 * 검증 단계에서 ontologyVersion + type + 정규화된 이름으로 계산한다.
 */
data class ExtractedGraphEntity(
    val localKey: String,
    val type: String,
    val name: String,
    val aliases: Set<String>,
    val confidence: Double,
    val evidence: List<ExtractedGraphEvidence>,
)
