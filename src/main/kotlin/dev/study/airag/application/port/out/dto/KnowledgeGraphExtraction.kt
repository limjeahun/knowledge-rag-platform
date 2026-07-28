package dev.study.airag.application.port.out.dto

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

/** LLM이 반환한 후보 그래프이며 아직 신뢰할 수 있는 지식으로 간주하지 않는다. */
data class ExtractedKnowledgeGraph(
    val entities: List<ExtractedGraphEntity>,
    val relations: List<ExtractedGraphRelation>,
)

/**
 * 모델 응답 안에서만 유효한 localKey로 식별되는 개체 후보다.
 *
 * localKey는 관계의 끝점을 연결하기 위한 임시 값일 뿐 영구 ID가 아니다. 영구 개체 동일성은
 * 검증 단계에서 ontologyVersion + type + 정규화된 이름으로 계산한다.
 */
data class ExtractedGraphEntity(
    val localKey: String,
    val type: String,
    val name: String,
    val aliases: Set<String>,
    val confidence: Double,
    val evidence: List<ExtractedGraphEvidence>,
)

/** 같은 모델 응답에 포함된 두 개체 후보 사이의 관계 후보다. */
data class ExtractedGraphRelation(
    val type: String,
    val sourceKey: String,
    val targetKey: String,
    val confidence: Double,
    val evidence: List<ExtractedGraphEvidence>,
)

/**
 * 추출된 사실이 어느 청크의 어떤 문장에 근거하는지 나타내는 출처다.
 *
 * quote는 검증 단계에서 실제 청크 본문에 존재하는지 확인한다. 이 검증을 통과하지 못한
 * 모델의 설명이나 추론은 그래프에 저장하지 않는다.
 */
data class ExtractedGraphEvidence(
    val chunkId: String,
    val quote: String,
)
