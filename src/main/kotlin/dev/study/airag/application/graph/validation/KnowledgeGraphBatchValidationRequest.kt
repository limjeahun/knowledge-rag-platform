package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology

internal data class KnowledgeGraphBatchValidationRequest(
    val ontology: KnowledgeOntology,
    val batch: KnowledgeGraphExtractionBatch,
    val minimumConfidence: Double,
)
