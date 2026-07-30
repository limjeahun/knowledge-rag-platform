package dev.study.airag.application.graph.dto

/**
 * 그래프 문장이 원문에서 직접 검증되었는지 OWL 의미론으로 도출되었는지 구분한다.
 *
 * 추론 문장에는 직접 인용 근거가 없으므로 이 값은 API와 LLM prompt에서 provenance를
 * 과장하지 않기 위한 핵심 계약이다.
 */
enum class KnowledgeGraphAssertionKind {
    /** 원문 document/chunk/quote로 역추적 가능한 직접 진술이다. */
    ASSERTED,

    /** 배포된 ontology와 asserted ABox로부터 reasoner가 도출한 문장이다. */
    INFERRED,
}
