package dev.study.airag.application.graph.port.out.dto

import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.vo.DocumentId

/**
 * 한 번의 모델 호출에서 분석할 문서 청크와 온톨로지를 전달한다.
 *
 * 전체 문서를 한 프롬프트에 넣지 않고 작은 청크 묶음으로 호출하여 모델 context 한도를
 * 넘지 않게 한다. 최종 병합과 중복 제거는 모델이 아니라 애플리케이션이 결정한다.
 */
data class KnowledgeGraphExtractionRequest(
    val documentId: DocumentId,
    val documentVersion: Long,
    val title: String,
    val ontology: KnowledgeOntology,
    val chunks: List<KnowledgeChunk>,
)
