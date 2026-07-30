package dev.study.airag.adapter.out.graph.rdf

import dev.study.airag.adapter.out.ontology.owl.OwlOntologyCatalog
import dev.study.airag.application.graph.exception.InvalidKnowledgeGraphExtractionException
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.springframework.stereotype.Component

/**
 * asserted RDF와 provenance가 배포된 SHACL 계약을 만족하는지 저장 전에 검사한다.
 *
 * OWL의 열린 세계 의미론만으로 표현하기 어려운 필수값·건수·datatype·값 범위는 SHACL이
 * 닫힌 입력 계약으로 검사한다. schema model을 data graph와 합쳐 `sh:class` 검사가 ontology
 * 타입 선언을 볼 수 있게 하며, 위반 report는 색인 실패 원인으로 그대로 전달한다.
 */
@Component
class RdfKnowledgeGraphValidator(
    private val catalog: OwlOntologyCatalog,
) {
    /**
     * asserted와 provenance RDF가 배포된 SHACL 입력 계약을 모두 만족하는지 검사한다.
     *
     * schema model을 데이터와 union하여 `sh:class`가 OWL class 선언을 확인할 수 있게 한다.
     * validation은 전달된 모델을 수정하지 않으며 conform하지 않는 report의 모든 entry를
     * 색인 실패 예외에 포함한다. inferred 모델은 이 단계 이후에 생성되므로 검사 대상이 아니다.
     *
     * @param models 직접 진술 triple과 그 statement-level provenance
     * @throws InvalidKnowledgeGraphExtractionException 하나 이상의 SHACL constraint를 위반한 경우
     */
    fun validate(models: RdfProjectionModels) {
        val snapshot = catalog.load()
        val data =
            ModelFactory.createUnion(
                snapshot.schemaModel,
                ModelFactory.createUnion(models.asserted, models.provenance),
            )
        val report =
            ShaclValidator
                .get()
                .validate(Shapes.parse(snapshot.shapesGraph), data.graph)
        if (!report.conforms()) {
            throw InvalidKnowledgeGraphExtractionException(
                "RDF projection이 SHACL 계약을 위반했습니다: ${report.entries.joinToString()}",
            )
        }
    }
}
