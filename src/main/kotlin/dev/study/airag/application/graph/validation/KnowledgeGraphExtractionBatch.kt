package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.ExtractedKnowledgeGraph
import dev.study.airag.domain.model.KnowledgeChunk

/**
 * LLM에 전달한 원본 청크와 그 호출에서 반환된 후보 그래프를 분리하지 않고 보존한다.
 *
 * validator는 이 결합을 이용해 다른 batch의 chunk ID나 생성된 quote를 근거로 사용할 수 없게 한다.
 */
data class KnowledgeGraphExtractionBatch(
    val chunks: List<KnowledgeChunk>,
    val extraction: ExtractedKnowledgeGraph,
)
