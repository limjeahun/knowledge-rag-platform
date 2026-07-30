package dev.study.airag.adapter.out.ontology.owl

import dev.study.airag.config.graph.KnowledgeGraphProperties
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.vocabulary.OWL
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import kotlin.test.assertTrue

class OntologyDocumentationTests {
    private val snapshot =
        OwlOntologyCatalog(DefaultResourceLoader(), KnowledgeGraphProperties()).load()

    @Test
    fun `every AIRAG OWL term has bilingual labels comments and a definition`() {
        val model = snapshot.schemaModel
        val documentedTypes =
            listOf(
                OWL.Ontology,
                OWL.Class,
                OWL.ObjectProperty,
                OWL.DatatypeProperty,
                ResourceFactory.createResource("${OWL.NS}AnnotationProperty"),
            )
        val terms =
            documentedTypes
                .flatMap { type ->
                    model
                        .listResourcesWithProperty(RDF.type, type)
                        .toList()
                }.filter { term ->
                    term.isURIResource &&
                        (
                            term.uri.startsWith(CORE_NAMESPACE) ||
                                term.uri.startsWith(SOFTWARE_NAMESPACE)
                        )
                }.distinctBy(Resource::getURI)

        val violations =
            terms.mapNotNull { term ->
                val missing = mutableListOf<String>()
                if (!term.hasLanguages(RDFS.label, REQUIRED_LANGUAGES)) missing += "rdfs:label@en,@ko"
                if (!term.hasLanguages(RDFS.comment, REQUIRED_LANGUAGES)) missing += "rdfs:comment@en,@ko"
                if (!term.hasNonBlankLiteral(SKOS_DEFINITION)) missing += "skos:definition"
                missing.takeIf(List<String>::isNotEmpty)?.let { "${term.uri}: ${it.joinToString()}" }
            }

        assertTrue(
            violations.isEmpty(),
            "문서화되지 않은 OWL 용어가 있습니다:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `every SHACL node and property shape explains its contract in both languages`() {
        val model = ModelFactory.createModelForGraph(snapshot.shapesGraph)
        val nodeShapes = model.listResourcesWithProperty(RDF.type, SH_NODE_SHAPE).toList()
        val propertyShapes =
            nodeShapes
                .flatMap { shape -> shape.listProperties(SH_PROPERTY).toList() }
                .map { statement -> statement.resource }
                .distinctBy(Resource::toString)

        val nodeViolations =
            nodeShapes.mapNotNull { shape ->
                val missing = mutableListOf<String>()
                if (!shape.hasLanguages(SH_NAME, REQUIRED_LANGUAGES)) missing += "sh:name@en,@ko"
                if (!shape.hasLanguages(SH_DESCRIPTION, REQUIRED_LANGUAGES)) missing += "sh:description@en,@ko"
                missing.takeIf(List<String>::isNotEmpty)?.let { "$shape: ${it.joinToString()}" }
            }
        val propertyViolations =
            propertyShapes.mapNotNull { shape ->
                val missing = mutableListOf<String>()
                if (!shape.hasLanguages(SH_NAME, REQUIRED_LANGUAGES)) missing += "sh:name@en,@ko"
                if (!shape.hasLanguages(SH_DESCRIPTION, REQUIRED_LANGUAGES)) missing += "sh:description@en,@ko"
                if (!shape.hasLanguages(SH_MESSAGE, REQUIRED_LANGUAGES)) missing += "sh:message@en,@ko"
                missing.takeIf(List<String>::isNotEmpty)?.let { "$shape: ${it.joinToString()}" }
            }

        assertTrue(nodeShapes.isNotEmpty(), "SHACL node shape가 하나 이상 필요합니다.")
        assertTrue(propertyShapes.isNotEmpty(), "SHACL property shape가 하나 이상 필요합니다.")
        assertTrue(
            nodeViolations.isEmpty() && propertyViolations.isEmpty(),
            "문서화되지 않은 SHACL shape가 있습니다:\n" +
                (nodeViolations + propertyViolations).joinToString("\n"),
        )
    }

    private fun Resource.hasLanguages(
        property: Property,
        languages: Set<String>,
    ): Boolean {
        val actual =
            listProperties(property)
                .toList()
                .mapNotNull { statement ->
                    statement
                        .`object`
                        .takeIf { it.isLiteral }
                        ?.asLiteral()
                        ?.language
                }.toSet()
        return actual.containsAll(languages)
    }

    private fun Resource.hasNonBlankLiteral(property: Property): Boolean =
        listProperties(property)
            .toList()
            .any { statement ->
                statement.`object`.isLiteral && statement.string.isNotBlank()
            }

    private companion object {
        const val CORE_NAMESPACE = "urn:airag:ontology:knowledge-core"
        const val SOFTWARE_NAMESPACE = "urn:airag:ontology:software-architecture"
        val REQUIRED_LANGUAGES = setOf("en", "ko")
        val SKOS_DEFINITION: Property =
            ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#definition")
        val SH_NODE_SHAPE: Resource =
            ResourceFactory.createResource("http://www.w3.org/ns/shacl#NodeShape")
        val SH_PROPERTY: Property =
            ResourceFactory.createProperty("http://www.w3.org/ns/shacl#property")
        val SH_NAME: Property =
            ResourceFactory.createProperty("http://www.w3.org/ns/shacl#name")
        val SH_DESCRIPTION: Property =
            ResourceFactory.createProperty("http://www.w3.org/ns/shacl#description")
        val SH_MESSAGE: Property =
            ResourceFactory.createProperty("http://www.w3.org/ns/shacl#message")
    }
}
