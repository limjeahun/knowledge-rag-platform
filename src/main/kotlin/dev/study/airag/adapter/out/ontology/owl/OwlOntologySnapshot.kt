package dev.study.airag.adapter.out.ontology.owl

import org.apache.jena.graph.Graph
import org.apache.jena.rdf.model.Model
import org.semanticweb.owlapi.model.OWLOntology

/**
 * 한 번 검증된 배포 온톨로지 묶음의 불변 스냅샷이다.
 *
 * OWL API 모델은 DL profile·논리 일관성 검사에 사용하고, Jena 모델과 그래프는 RDF 투영 및
 * SHACL 검증에 사용한다. [checksum]은 TBox와 Shapes 전체 바이트를 기준으로 계산되므로 값이
 * 달라지면 기존 문서 그래프를 새 의미 계약으로 재생성해야 한다.
 */
data class OwlOntologySnapshot(
    val rootOntology: OWLOntology,
    val schemaModel: Model,
    val shapesGraph: Graph,
    val version: String,
    val checksum: String,
)
