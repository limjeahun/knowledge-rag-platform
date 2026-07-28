package dev.study.airag.application.graph.port.out.dto

data class KnowledgeGraphEvidenceView(
    val documentId: String,
    val documentVersion: Long,
    val chunkId: String,
    val quote: String,
    val confidence: Double,
)
