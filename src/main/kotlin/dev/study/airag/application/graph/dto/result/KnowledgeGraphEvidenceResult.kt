package dev.study.airag.application.graph.dto.result

/**
 * 그래프 답변을 원문 청크까지 추적할 수 있게 하는 출처다.
 *
 * documentId와 chunkId가 있으므로 사용자는 관계가 단순 모델 추론인지, 실제 등록 문서에서
 * 추출된 것인지 확인할 수 있다.
 */
data class KnowledgeGraphEvidenceResult(
    val documentId: String,
    val documentVersion: Long,
    val chunkId: String,
    val quote: String,
    val confidence: Double,
)
