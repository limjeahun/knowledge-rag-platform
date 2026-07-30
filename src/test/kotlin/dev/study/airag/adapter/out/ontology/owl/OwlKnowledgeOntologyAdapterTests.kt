package dev.study.airag.adapter.out.ontology.owl

import dev.study.airag.config.graph.KnowledgeGraphProperties
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OwlKnowledgeOntologyAdapterTests {
    private val catalog = OwlOntologyCatalog(DefaultResourceLoader(), KnowledgeGraphProperties())

    @Test
    fun `projects extractable OWL classes and object properties as the application ontology`() {
        val adapter = OwlKnowledgeOntologyAdapter(OwlKnowledgeOntologyTranslator(catalog))

        val ontology = adapter.load()

        assertEquals("urn:airag:ontology:software-architecture:1.0.0", ontology.version)
        assertEquals(ontology.entityTypes.sortedBy { it.code }, ontology.entityTypes)
        assertEquals(ontology.relationTypes.sortedBy { it.code }, ontology.relationTypes)
        assertTrue("TECHNOLOGY" in ontology.entityTypesByCode)
        assertTrue("VECTOR_INDEX" in ontology.entityTypesByCode)
        assertTrue("DOCUMENT" in ontology.entityTypesByCode)
        assertEquals(
            setOf("DOCUMENT_CHUNK"),
            ontology.relationTypesByCode.getValue("INDEXED_IN").sourceTypes,
        )
        assertEquals(
            setOf("VECTOR_INDEX"),
            ontology.relationTypesByCode.getValue("INDEXED_IN").targetTypes,
        )
        assertTrue("RELATIONAL_DATABASE" in ontology.relationTypesByCode.getValue("STORES_IN").targetTypes)
        assertSame(ontology, adapter.load())
    }
}
