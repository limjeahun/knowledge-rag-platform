package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.ExtractedGraphEvidence
import dev.study.airag.domain.model.KnowledgeChunk

internal data class KnowledgeGraphEvidenceValidationRequest(
    val evidence: List<ExtractedGraphEvidence>,
    val confidence: Double,
    val chunksById: Map<String, KnowledgeChunk>,
)
