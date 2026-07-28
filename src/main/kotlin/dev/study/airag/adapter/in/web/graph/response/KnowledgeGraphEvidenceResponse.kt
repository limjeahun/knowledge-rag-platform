package dev.study.airag.adapter.`in`.web.graph.response

/** 추출된 지식이 실제 어느 문서 버전과 청크 구절에서 나왔는지 보여주는 출처다. */
data class KnowledgeGraphEvidenceResponse(
    val documentId: String,
    val documentVersion: Long,
    val chunkId: String,
    val quote: String,
    val confidence: Double,
)
