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

    /**
     * 애플리케이션 수명 동안 동일한 검증 완료 스냅샷을 반환한다.
     *
     * 최초 호출에서만 [loadSnapshot]을 실행하며 이후 호출은 같은 객체를 반환한다. 따라서 배포 중
     * classpath의 TTL 파일이 바뀌어도 자동으로 다시 읽지 않는다. 온톨로지를 교체했다면
     * 애플리케이션을 재시작하여 version과 checksum을 하나의 일관된 단위로 다시 적재해야 한다.
     *
     * @return OWL 2 DL profile, 논리 일관성 및 리소스 존재 검사를 통과한 불변 스냅샷
     * @throws IllegalArgumentException 최초 적재 시 리소스, profile 또는 논리 계약이 유효하지 않은 경우
     */
    fun load(): OwlOntologySnapshot = snapshot

    /**
     * 설정에 선언된 모든 OWL 문서와 SHACL shapes를 읽어 하나의 배포 스냅샷으로 조립한다.
     *
     * OWL API manager에 모든 문서를 먼저 등록하므로 루트 ontology의 import closure가 같은
     * manager 안에서 해석된다. OWL API 모델은 DL 검증과 추론에, Jena 모델은 RDF 변환과
     * SHACL 검증에 사용한다. 반환 전에 두 표현이 동일한 원본 바이트에서 만들어지며,
     * checksum도 해당 바이트 전체를 대상으로 계산한다.
     *
     * @return 루트 OWL ontology, 합쳐진 schema model, SHACL graph, version IRI와 checksum
     * @throws IllegalStateException [KnowledgeGraphProperties.rootOntologyIri]에 해당하는 루트가 없는 경우
     * @throws IllegalArgumentException 온톨로지나 shapes 리소스 또는 의미론적 계약이 유효하지 않은 경우
     */
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

    /**
     * 루트 ontology의 import closure가 OWL 2 DL 표현력 안에 있는지 검사한다.
     *
     * HermiT가 보장하는 DL 추론 전제를 배포 시점에 고정하기 위한 검사다. profile 위반을
     * 허용한 채 일부 공리만 해석하는 방식으로 조용히 의미가 달라지는 것을 막는다.
     *
     * @param ontology 설정에서 선택된 루트 ontology
     * @throws IllegalArgumentException OWL 2 DL profile 위반이 하나 이상 발견된 경우
     */
    private fun validateProfile(ontology: OWLOntology) {
        val report = OWL2DLProfile().checkOntology(ontology)
        require(report.isInProfile) {
            "OWL 2 DL profile 위반입니다: ${report.violations.joinToString { it.toString() }}"
        }
    }

    /**
     * HermiT로 ontology 일관성과 모든 named class의 만족 가능성을 검사한다.
     *
     * 전체 ontology가 일관되더라도 특정 named class가 `owl:Nothing`과 동치가 되면 그 타입의
     * 인스턴스를 만들 수 없으므로 배포 오류로 취급한다. Reasoner는 native 자원과 내부 cache를
     * 소유하므로 성공·실패와 관계없이 이 메서드 안에서 반드시 폐기한다.
     *
     * @param ontology profile 검사를 먼저 통과한 루트 ontology
     * @throws IllegalArgumentException ontology가 불일치하거나 만족 불가능한 named class가 있는 경우
     */
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
                    .unsatisfiableClasses
                    .entities()
                    .filter { type -> !type.isOWLNothing }
                    .toList()
            require(unsatisfiable.isEmpty()) {
                "만족 불가능한 OWL class가 있습니다: ${unsatisfiable.joinToString { it.iri.toString() }}"
            }
        } finally {
            reasoner.dispose()
        }
    }

    /**
     * Spring resource 위치의 전체 바이트를 읽고 스트림을 즉시 닫는다.
     * 문자열로 디코딩했다가 다시 인코딩하지 않기 때문에 checksum은 줄바꿈과 annotation을 포함한 실제 배포 파일을 정확히 식별한다.
     *
     * @param location `classpath:` 또는 Spring [ResourceLoader]가 지원하는 리소스 위치
     * @return 해당 리소스의 원본 바이트
     * @throws IllegalArgumentException 리소스가 존재하지 않는 경우
     */
    private fun readRequiredResource(location: String): ByteArray {
        val resource = resourceLoader.getResource(location)
        require(resource.exists()) { "Ontology resource를 찾을 수 없습니다: $location" }
        return resource.inputStream.use { it.readAllBytes() }
    }

    /**
     * 순서가 고정된 ontology 및 shapes 문서들을 하나의 SHA-256 식별자로 계산한다.
     * 각 문서 뒤에 NUL 구분자를 추가하여 서로 다른 문서 경계가 같은 연결 바이트로 해석되는 모호성을 제거한다.
     * 입력 순서는 설정의 ontology 위치 순서와 마지막 shapes 순서다.
     *
     * @param documents checksum에 포함할 원본 리소스 바이트 목록
     * @return 소문자 16진수 64자리 SHA-256 문자열
     */
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
