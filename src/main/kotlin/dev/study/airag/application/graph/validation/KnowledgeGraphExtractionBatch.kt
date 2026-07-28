package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.ExtractedKnowledgeGraph
import dev.study.airag.domain.model.KnowledgeChunk

data class KnowledgeGraphExtractionBatch(
    val chunks: List<KnowledgeChunk>,
    val extraction: ExtractedKnowledgeGraph,
)
