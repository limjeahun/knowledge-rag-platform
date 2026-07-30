package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.ExtractedGraphEntity
import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology
import dev.study.airag.domain.model.KnowledgeChunk

/** 개체 후보의 ontology type과 원문 evidence를 함께 검증하는 내부 입력 묶음이다. */
internal data class KnowledgeGraphEntityValidationRequest(
    val ontology: KnowledgeOntology,
    val entity: ExtractedGraphEntity,
    val chunksById: Map<String, KnowledgeChunk>,
)
