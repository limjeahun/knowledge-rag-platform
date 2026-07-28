package dev.study.airag.application.graph.port.out.dto

/**
 * 지식 그래프의 각 사실을 원본 문서로 역추적하기 위한 provenance다.
 *
 * 점수는 모델 확신도의 기록일 뿐 사실 여부를 대신하지 않는다. quote가 실제 chunk에
 * 포함되는지 확인하는 provenance 검증과 ontology 규칙 검증을 모두 통과해야 저장된다.
 */
data class KnowledgeGraphEvidence(
    val chunkId: String,
    val quote: String,
    val confidence: Double,
)
