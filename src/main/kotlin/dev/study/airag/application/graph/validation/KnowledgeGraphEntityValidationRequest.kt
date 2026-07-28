package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.ExtractedGraphEntity
import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology
import dev.study.airag.domain.model.KnowledgeChunk

internal data class KnowledgeGraphEntityValidationRequest(
    val ontology: KnowledgeOntology,
    val entity: ExtractedGraphEntity,
    val chunksById: Map<String, KnowledgeChunk>,
)
