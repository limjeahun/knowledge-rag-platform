package dev.study.airag.adapter.out.graph.rdf

import dev.study.airag.adapter.out.ontology.owl.OwlOntologyCatalog
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import org.springframework.stereotype.Component

/**
 * OWL annotation code를 RDF class/property IRI로 해석하는 불변 색인이다.
 *
 * `core:extractable true`인 named resource만 수집하며 누락·중복 code는 시작 후 첫 사용 시
 * 즉시 실패시킨다. LLM이 반환한 안정적인 code를 실제 ontology IRI로 바꾸는 유일한 경계다.
 */
@Component
class OwlRdfVocabularyIndex(
    private val catalog: OwlOntologyCatalog,
) {
    private val classesByCode: Map<String, Resource> by lazy {
        indexedResources(RdfKnowledgeGraphVocabulary.OWL_CLASS)
    }
    private val propertiesByCode: Map<String, Property> by lazy {
        indexedResources(RdfKnowledgeGraphVocabulary.OWL_OBJECT_PROPERTY)
            .mapValues { (_, resource) -> resource.model.createProperty(resource.uri) }
    }

    /**
     * Application 개체 타입 code를 실제 OWL class RDF resource로 해석한다.
     *
     * @param code LLM 추출과 Application 검증을 통과한 `core:code`
     * @return 배포 schema model에 속한 named OWL class resource
     * @throws IllegalStateException code에 대응하는 추출 가능 class가 없는 경우
     */
    fun classFor(code: String): Resource = classesByCode[code] ?: error("OWL class code를 찾을 수 없습니다: $code")

    /**
     * Application 관계 타입 code를 실제 OWL object property로 해석한다.
     *
     * @param code LLM 추출과 Application 검증을 통과한 `core:code`
     * @return 배포 schema model의 IRI로 생성한 Jena property
     * @throws IllegalStateException code에 대응하는 추출 가능 object property가 없는 경우
     */
    fun propertyFor(code: String): Property =
        propertiesByCode[code] ?: error("OWL object property code를 찾을 수 없습니다: $code")

    /**
     * 지정한 RDF 타입의 extractable OWL resource를 code 기준 불변 색인으로 만든다.
     *
     * Jena iterator는 명시적으로 닫고, code 누락이나 중복을 최초 색인 생성 시 즉시 실패시킨다.
     * 같은 code가 class와 property에 각각 존재하는 것은 별도 색인이므로 허용되지만 한 색인
     * 내부의 중복은 RDF 변환 대상을 모호하게 하므로 허용하지 않는다.
     *
     * @param rdfType `owl:Class` 또는 `owl:ObjectProperty`
     * @return code에서 schema resource로의 insertion-order map
     * @throws IllegalStateException extractable resource의 code가 없거나 같은 타입 안에서 중복된 경우
     */
    private fun indexedResources(rdfType: Resource): Map<String, Resource> {
        val model = catalog.load().schemaModel
        val resources = linkedMapOf<String, Resource>()
        val iterator = model.listResourcesWithProperty(RdfKnowledgeGraphVocabulary.RDF_TYPE, rdfType)
        try {
            while (iterator.hasNext()) {
                val resource = iterator.next()
                if (!resource.hasLiteral(RdfKnowledgeGraphVocabulary.EXTRACTABLE, true)) continue
                val code =
                    resource.getProperty(RdfKnowledgeGraphVocabulary.CODE)?.string
                        ?: error("추출 가능한 OWL resource에 code annotation이 없습니다: ${resource.uri}")
                check(resources.put(code, resource) == null) { "중복 OWL code입니다: $code" }
            }
        } finally {
            iterator.close()
        }
        return resources
    }
}
