package dev.study.airag.adapter.out.ontology.owl

import dev.study.airag.config.graph.KnowledgeGraphProperties
import org.junit.jupiter.api.Test
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.profiles.OWL2DLProfile
import org.springframework.core.io.DefaultResourceLoader
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwlOntologySemanticTests {
    private val snapshot =
        OwlOntologyCatalog(DefaultResourceLoader(), KnowledgeGraphProperties()).load()

    @Test
    fun `ontology is OWL 2 DL consistent and has no unsatisfiable named classes`() {
        val report = OWL2DLProfile().checkOntology(snapshot.rootOntology)
        val reasoner = ReasonerFactory().createReasoner(snapshot.rootOntology)

        try {
            assertTrue(report.isInProfile, report.violations.joinToString())
            assertTrue(reasoner.isConsistent)
            assertTrue(reasoner.getUnsatisfiableClasses().entities().allMatch { it.isOWLNothing })
        } finally {
            reasoner.dispose()
        }
    }

    @Test
    fun `reasoner entails class and object property hierarchies declared by the ontology`() {
        val dataFactory = OWLManager.getOWLDataFactory()
        val vectorIndex = dataFactory.getOWLClass(IRI.create("$SOFTWARE_IRI#VectorIndex"))
        val dataStore = dataFactory.getOWLClass(IRI.create("$SOFTWARE_IRI#DataStore"))
        val indexedIn = dataFactory.getOWLObjectProperty(IRI.create("$SOFTWARE_IRI#indexedIn"))
        val storesIn = dataFactory.getOWLObjectProperty(IRI.create("$SOFTWARE_IRI#storesIn"))
        val reasoner = ReasonerFactory().createReasoner(snapshot.rootOntology)

        try {
            assertTrue(reasoner.isEntailed(dataFactory.getOWLSubClassOfAxiom(vectorIndex, dataStore)))
            assertTrue(reasoner.isEntailed(dataFactory.getOWLSubObjectPropertyOfAxiom(indexedIn, storesIn)))
            assertFalse(reasoner.isEntailed(dataFactory.getOWLSubClassOfAxiom(dataStore, vectorIndex)))
        } finally {
            reasoner.dispose()
        }
    }

    private companion object {
        const val SOFTWARE_IRI = "urn:airag:ontology:software-architecture"
    }
}
