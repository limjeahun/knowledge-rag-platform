package dev.study.airag.adapter.out.ontology.owl

import dev.study.airag.config.graph.KnowledgeGraphProperties
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.semanticweb.HermiT.Configuration
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.profiles.OWL2DLProfile
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * 배포에 고정된 OWL TBox와 SHACL shapes를 한 번 로드하고 의미론적 유효성을 검사한다.
 *
 * 루트 온톨로지와 import closure는 OWL 2 DL profile 및 HermiT 일관성 검사를 통과해야 한다.
 * 검증된 결과는 지연 초기화 후 재사용하여 요청마다 reasoner와 parser를 다시 구성하지 않는다.
 * checksum에는 ontology 문서와 SHACL shapes를 모두 포함해 제약 변경도 재색인 조건이 되게 한다.
 */
@Component
class OwlOntologyCatalog(
    private val resourceLoader: ResourceLoader,
    private val properties: KnowledgeGraphProperties,
) {
    private val snapshot: OwlOntologySnapshot by lazy(::loadSnapshot)

    /** 애플리케이션 수명 동안 동일한 검증 완료 스냅샷을 반환한다. */
    fun load(): OwlOntologySnapshot = snapshot

    private fun loadSnapshot(): OwlOntologySnapshot {
        val ontologyDocuments =
            properties.ontologyLocations.map { location ->
                location to readRequiredResource(location)
            }
        val shapes = readRequiredResource(properties.shapesLocation)
        val manager = OWLManager.createOWLOntologyManager()
        ontologyDocuments.forEach { (_, bytes) ->
            manager.loadOntologyFromOntologyDocument(ByteArrayInputStream(bytes))
        }
        val root =
            manager
                .ontologies()
                .toList()
                .firstOrNull { ontology ->
                    ontology.ontologyID.ontologyIRI
                        .map(IRI::toString)
                        .orElse("") == properties.rootOntologyIri
                } ?: error("루트 OWL ontology를 찾을 수 없습니다: ${properties.rootOntologyIri}")

        validateProfile(root)
        validateConsistency(root)

        val schemaModel = ModelFactory.createDefaultModel()
        ontologyDocuments.forEach { (_, bytes) ->
            RDFDataMgr.read(schemaModel, ByteArrayInputStream(bytes), Lang.TURTLE)
        }
        val shapesModel = ModelFactory.createDefaultModel()
        RDFDataMgr.read(shapesModel, ByteArrayInputStream(shapes), Lang.TURTLE)
        return OwlOntologySnapshot(
            rootOntology = root,
            schemaModel = schemaModel,
            shapesGraph = shapesModel.graph,
            version =
                root.ontologyID.versionIRI
                    .orElse(root.ontologyID.ontologyIRI.orElseThrow())
                    .toString(),
            checksum = checksum(ontologyDocuments.map(Pair<String, ByteArray>::second) + shapes),
        )
    }

    private fun validateProfile(ontology: OWLOntology) {
        val report = OWL2DLProfile().checkOntology(ontology)
        require(report.isInProfile) {
            "OWL 2 DL profile 위반입니다: ${report.violations.joinToString { it.toString() }}"
        }
    }

    private fun validateConsistency(ontology: OWLOntology) {
        val configuration =
            Configuration().apply {
                throwInconsistentOntologyException = false
            }
        val reasoner = ReasonerFactory().createReasoner(ontology, configuration)
        try {
            require(reasoner.isConsistent) { "OWL ontology가 논리적으로 일관되지 않습니다." }
            val unsatisfiable =
                reasoner
                    .getUnsatisfiableClasses()
                    .entities()
                    .toList()
                    .filterNot { it.isOWLNothing }
                    .toList()
            require(unsatisfiable.isEmpty()) {
                "만족 불가능한 OWL class가 있습니다: ${unsatisfiable.joinToString { it.iri.toString() }}"
            }
        } finally {
            reasoner.dispose()
        }
    }

    private fun readRequiredResource(location: String): ByteArray {
        val resource = resourceLoader.getResource(location)
        require(resource.exists()) { "Ontology resource를 찾을 수 없습니다: $location" }
        return resource.inputStream.use { it.readAllBytes() }
    }

    private fun checksum(documents: List<ByteArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        documents.forEach { bytes ->
            digest.update(bytes)
            digest.update(RESOURCE_SEPARATOR)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        val RESOURCE_SEPARATOR = byteArrayOf(0)
    }
}
