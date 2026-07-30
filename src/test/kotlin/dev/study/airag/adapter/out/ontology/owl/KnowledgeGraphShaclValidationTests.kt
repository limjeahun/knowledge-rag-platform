package dev.study.airag.adapter.out.ontology.owl

import dev.study.airag.config.graph.KnowledgeGraphProperties
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KnowledgeGraphShaclValidationTests {
    private val resourceLoader = DefaultResourceLoader()
    private val snapshot = OwlOntologyCatalog(resourceLoader, KnowledgeGraphProperties()).load()
    private val shapes = Shapes.parse(snapshot.shapesGraph)

    @Test
    fun `valid document chunk fixture conforms to SHACL shapes`() {
        val report = ShaclValidator.get().validate(shapes, loadFixture("valid-architecture-graph-v1.ttl").graph)

        assertTrue(report.conforms(), report.entries.toString())
    }

    @Test
    fun `missing chunk index and invalid version violate SHACL shapes`() {
        val report = ShaclValidator.get().validate(shapes, loadFixture("invalid-architecture-graph-v1.ttl").graph)

        assertFalse(report.conforms())
        assertTrue(report.entries.size >= 2)
    }

    private fun loadFixture(fileName: String) =
        ModelFactory.createDefaultModel().also { model ->
            resourceLoader
                .getResource("classpath:ontology/fixtures/$fileName")
                .inputStream
                .use { RDFDataMgr.read(model, it, Lang.TURTLE) }
        }
}
