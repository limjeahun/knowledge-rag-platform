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

    fun classFor(code: String): Resource = classesByCode[code] ?: error("OWL class code를 찾을 수 없습니다: $code")

    fun propertyFor(code: String): Property =
        propertiesByCode[code] ?: error("OWL object property code를 찾을 수 없습니다: $code")

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
