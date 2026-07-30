package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.ExtractedGraphEvidence
import dev.study.airag.domain.model.KnowledgeChunk

/**
 * evidence가 실제 요청 batch의 chunk와 정확한 quote를 가리키는지 검증하는 내부 입력이다.
 */
internal data class KnowledgeGraphEvidenceValidationRequest(
    val evidence: List<ExtractedGraphEvidence>,
    val confidence: Double,
    val chunksById: Map<String, KnowledgeChunk>,
)
