package dev.study.airag.application.graph.port.out.dto

/**
 * 저장 Adapter가 반환하는 문장 단위 원문 provenance다.
 *
 * document version과 chunk ID는 quote가 나온 정확한 원본을 재현하며 confidence는 검증된
 * 추출 후보의 점수다. 추론 문장은 이 view를 가지지 않는다.
 */
data class KnowledgeGraphEvidenceView(
    val documentId: String,
    val documentVersion: Long,
    val chunkId: String,
    val quote: String,
    val confidence: Double,
)
