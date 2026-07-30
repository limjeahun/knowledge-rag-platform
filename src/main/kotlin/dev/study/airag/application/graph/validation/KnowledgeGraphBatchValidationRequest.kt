package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology

/** 한 LLM batch의 후보를 ontology와 신뢰도 정책으로 검증하는 내부 입력 묶음이다. */
internal data class KnowledgeGraphBatchValidationRequest(
    val ontology: KnowledgeOntology,
    val batch: KnowledgeGraphExtractionBatch,
    val minimumConfidence: Double,
)
