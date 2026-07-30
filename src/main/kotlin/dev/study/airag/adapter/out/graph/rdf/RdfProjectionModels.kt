package dev.study.airag.adapter.out.graph.rdf

import org.apache.jena.rdf.model.Model

/**
 * Application projection을 RDF로 옮긴 뒤 SHACL과 추론기에 전달하는 중간 결과다.
 *
 * [asserted]에는 검증된 직접 진술만, [provenance]에는 원문 document/chunk/quote와 연결된
 * reified statement만 들어간다. 추론 문장은 이 단계에서 포함하지 않는다.
 */
data class RdfProjectionModels(
    val asserted: Model,
    val provenance: Model,
)
