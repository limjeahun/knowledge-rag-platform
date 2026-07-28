package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.policy.KnowledgeGraphProjectionPolicy
import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology

/** 후보 그래프 묶음을 검증하고 병합하는 데 필요한 입력을 하나로 묶는다. */
data class KnowledgeGraphValidationRequest(
    val ontology: KnowledgeOntology,
    val batches: List<KnowledgeGraphExtractionBatch>,
    val policy: KnowledgeGraphProjectionPolicy,
)
