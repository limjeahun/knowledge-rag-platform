package dev.study.airag.adapter.out.graph.rdf

import org.apache.jena.rdf.model.Model

/**
 * OWL reasoner가 직접 진술 ABox에서 새로 도출한 문장과 그 생성 출처다.
 *
 * [inferred]에는 asserted 모델에 이미 존재하는 문장을 중복 저장하지 않는다. [provenance]는
 * 추론 활동과 ontology version만 기록하며 원문에서 직접 인용하지 않은 quote를 만들지 않는다.
 */
data class OwlReasoningResult(
    val inferred: Model,
    val provenance: Model,
)
