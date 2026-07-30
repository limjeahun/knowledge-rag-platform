package dev.study.airag.adapter.out.graph.rdf

import dev.study.airag.adapter.out.ontology.owl.OwlOntologyCatalog
import dev.study.airag.config.graph.KnowledgeGraphProperties
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDF
import org.semanticweb.HermiT.Configuration
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.IRI
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * OWL 2 DL TBox와 asserted ABox로부터 명명된 개체의 class/property entailment를 계산한다.
 *
 * HermiT로 개체 타입과 객체 속성 값을 계산하고, subproperty·equivalent property·inverse
 * property entailment도 물질화한다. asserted 모델에 이미 있는 문장은 제외하며 새 문장에는
 * ontology version과 reasoning activity만 기록한다. 추론 폭증을 막기 위해 설정된 statement
 * 상한을 넘으면 전체 프로젝션을 실패시킨다.
 */
@Component
class OwlKnowledgeGraphReasoner(
    private val catalog: OwlOntologyCatalog,
    private val properties: KnowledgeGraphProperties,
) {
    /**
     * asserted RDF를 배포 TBox와 결합해 class 및 object-property entailment를 물질화한다.
     *
     * Jena 모델을 임시 OWL ontology로 변환하고 import closure의 모든 공리를 합친 뒤 HermiT
     * 일관성을 먼저 확인한다. named individual의 전체 타입과 객체 속성 값을 조회하고,
     * [materializePropertyHierarchy]로 명시 관계의 상위·동치·역관계도 보완한다. 원문에 직접
     * 존재하는 문장은 결과에서 제외하며 추론 문장마다 quote 없는 provenance를 생성한다.
     *
     * @param asserted Application 검증과 SHACL 검증을 통과한 직접 진술 RDF 모델
     * @return 새롭게 도출된 문장 모델과 그 추론 provenance
     * @throws IllegalArgumentException asserted ABox를 합친 ontology가 불일치하거나 추론 상한을 넘은 경우
     */
    fun infer(asserted: Model): OwlReasoningResult {
        val snapshot = catalog.load()
        val manager = OWLManager.createOWLOntologyManager()
        val combined = manager.createOntology()
        snapshot.rootOntology.importsClosure().forEach { imported ->
            manager.addAxioms(combined, imported.axioms())
        }
        val assertedOntology =
            manager.loadOntologyFromOntologyDocument(
                ByteArrayInputStream(asserted.asTurtle()),
            )
        manager.addAxioms(combined, assertedOntology.axioms())

        val configuration =
            Configuration().apply {
                throwInconsistentOntologyException = false
            }
        val reasoner = ReasonerFactory().createReasoner(combined, configuration)
        try {
            require(reasoner.isConsistent) { "asserted RDF를 포함한 OWL ontology가 일관되지 않습니다." }
            val inferred = ModelFactory.createDefaultModel()
            val provenance = ModelFactory.createDefaultModel()
            val activity =
                provenance
                    .createResource("${RdfKnowledgeGraphVocabulary.ACTIVITY_NAMESPACE}reasoning:${snapshot.checksum}")
                    .addProperty(
                        RdfKnowledgeGraphVocabulary.RDF_TYPE,
                        RdfKnowledgeGraphVocabulary.PROV_ACTIVITY,
                    )
            val individuals = assertedOntology.individualsInSignature().toList()
            val objectProperties =
                combined
                    .objectPropertiesInSignature()
                    .toList()
                    .filterNot { it.isOWLTopObjectProperty || it.isOWLBottomObjectProperty }

            individuals.forEach { individual ->
                val subject = inferred.createResource(individual.iri.toString())
                reasoner
                    .getTypes(individual, false)
                    .entities()
                    .toList()
                    .filterNot { it.isOWLThing || it.isOWLNothing }
                    .forEach { type ->
                        val statement =
                            inferred.createStatement(
                                subject,
                                RDF.type,
                                inferred.createResource(type.iri.toString()),
                            )
                        addInferred(statement, asserted, inferred, provenance, activity, snapshot.version)
                    }
                objectProperties.forEach { property ->
                    reasoner.getObjectPropertyValues(individual, property).entities().forEach { target ->
                        val statement =
                            inferred.createStatement(
                                subject,
                                inferred.createProperty(property.iri.toString()),
                                inferred.createResource(target.iri.toString()),
                            )
                        addInferred(statement, asserted, inferred, provenance, activity, snapshot.version)
                    }
                }
            }
            materializePropertyHierarchy(
                asserted = asserted,
                inferred = inferred,
                provenance = provenance,
                activity = activity,
                ontologyVersion = snapshot.version,
                objectPropertyIris = objectProperties.map { it.iri.toString() }.toSet(),
                reasoner = reasoner,
                manager = manager,
            )
            require(inferred.size() <= properties.maxInferredStatements) {
                "OWL 추론 statement 수가 제한(${properties.maxInferredStatements})을 초과했습니다."
            }
            return OwlReasoningResult(inferred, provenance)
        } finally {
            reasoner.dispose()
        }
    }

    /**
     * asserted 객체 속성 문장에 대해 상위·동치 property와 inverse property 문장을 추가한다.
     *
     * HermiT의 개체별 property-value 조회만으로 구현체나 ontology 버전에 따라 누락될 수 있는
     * property hierarchy 결과를 명시적으로 물질화한다. IRI subject/object인 문장만 대상으로
     * 하며 datatype property와 blank node 관계는 처리하지 않는다.
     *
     * @param asserted 원문에서 직접 추출한 RDF
     * @param inferred 새 entailment를 누적할 RDF
     * @param provenance 추론 statement provenance를 누적할 RDF
     * @param activity 이번 ontology checksum을 식별하는 PROV reasoning activity
     * @param ontologyVersion entailment에 사용한 version IRI
     * @param objectPropertyIris TBox에 선언된 유효 object property IRI
     * @param reasoner 현재 결합 ontology를 분류한 HermiT Reasoner
     * @param manager OWL property 객체를 생성하는 현재 ontology manager
     */
    private fun materializePropertyHierarchy(
        asserted: Model,
        inferred: Model,
        provenance: Model,
        activity: org.apache.jena.rdf.model.Resource,
        ontologyVersion: String,
        objectPropertyIris: Set<String>,
        reasoner: org.semanticweb.owlapi.reasoner.OWLReasoner,
        manager: org.semanticweb.owlapi.model.OWLOntologyManager,
    ) {
        asserted
            .listStatements()
            .toList()
            .filter { statement ->
                statement.subject.isURIResource &&
                    statement.`object`.isURIResource &&
                    statement.predicate.uri in objectPropertyIris
            }.forEach { statement ->
                val property =
                    manager.owlDataFactory.getOWLObjectProperty(
                        IRI.create(statement.predicate.uri),
                    )
                val sameDirection =
                    (
                        reasoner.getSuperObjectProperties(property, false).entities().toList() +
                            reasoner.getEquivalentObjectProperties(property).entities().toList()
                    ).filterNot {
                        it.isOWLTopObjectProperty || it.isOWLBottomObjectProperty
                    }
                sameDirection.forEach { entailedProperty ->
                    addInferred(
                        inferred.createStatement(
                            inferred.createResource(statement.subject.uri),
                            inferred.createProperty(entailedProperty.namedProperty.iri.toString()),
                            inferred.createResource(statement.resource.uri),
                        ),
                        asserted,
                        inferred,
                        provenance,
                        activity,
                        ontologyVersion,
                    )
                }
                reasoner.getInverseObjectProperties(property).entities().forEach { inverse ->
                    addInferred(
                        inferred.createStatement(
                            inferred.createResource(statement.resource.uri),
                            inferred.createProperty(inverse.namedProperty.iri.toString()),
                            inferred.createResource(statement.subject.uri),
                        ),
                        asserted,
                        inferred,
                        provenance,
                        activity,
                        ontologyVersion,
                    )
                }
            }
    }

    /**
     * 직접 진술과 중복되지 않는 하나의 entailment와 결정적 provenance 레코드를 추가한다.
     *
     * 동일 문장이 여러 추론 경로에서 도달해도 Jena Model의 집합 의미로 한 번만 남는다.
     * provenance IRI는 subject·predicate·object의 안정적인 UUID이므로 재색인 결과가 결정적이다.
     * 추론 사실은 원문의 직접 인용이 아니므로 document, chunk, quote를 만들어 넣지 않는다.
     *
     * @param statement 추가 후보인 추론 RDF 문장
     * @param asserted 중복 여부를 확인할 직접 진술 모델
     * @param inferred 추론 문장을 누적할 모델
     * @param provenance 추론 출처를 누적할 모델
     * @param activity 이 문장을 생성한 reasoning activity
     * @param ontologyVersion 추론에 사용한 version IRI
     */
    private fun addInferred(
        statement: org.apache.jena.rdf.model.Statement,
        asserted: Model,
        inferred: Model,
        provenance: Model,
        activity: org.apache.jena.rdf.model.Resource,
        ontologyVersion: String,
    ) {
        if (asserted.contains(statement)) return
        inferred.add(statement)
        val assertionId =
            UUID.nameUUIDFromBytes(
                "${statement.subject}|${statement.predicate}|${statement.`object`}"
                    .toByteArray(StandardCharsets.UTF_8),
            )
        provenance
            .createResource("${RdfKnowledgeGraphVocabulary.ASSERTION_NAMESPACE}$assertionId")
            .addProperty(RdfKnowledgeGraphVocabulary.RDF_TYPE, RdfKnowledgeGraphVocabulary.RDF_STATEMENT)
            .addProperty(
                RdfKnowledgeGraphVocabulary.RDF_TYPE,
                RdfKnowledgeGraphVocabulary.INFERRED_STATEMENT_PROVENANCE,
            ).addProperty(RdfKnowledgeGraphVocabulary.RDF_SUBJECT, statement.subject)
            .addProperty(RdfKnowledgeGraphVocabulary.RDF_PREDICATE, statement.predicate)
            .addProperty(RdfKnowledgeGraphVocabulary.RDF_OBJECT, statement.`object`)
            .addLiteral(RdfKnowledgeGraphVocabulary.ASSERTION_KIND, "INFERRED")
            .addLiteral(RdfKnowledgeGraphVocabulary.ONTOLOGY_VERSION, ontologyVersion)
            .addProperty(RdfKnowledgeGraphVocabulary.WAS_GENERATED_BY, activity)
    }

    /**
     * Jena RDF 모델을 OWL API가 읽을 수 있는 Turtle 바이트로 직렬화한다.
     *
     * 메모리 스트림은 메서드 안에서 닫히며 원본 Model은 변경하지 않는다. 이 변환은 ABox를
     * HermiT 결합 ontology에 추가하기 위한 Adapter 내부 경계다.
     *
     * @return 현재 모델 전체를 표현하는 Turtle UTF-8 호환 바이트
     */
    private fun Model.asTurtle(): ByteArray =
        ByteArrayOutputStream().use { output ->
            RDFDataMgr.write(output, this, Lang.TURTLE)
            output.toByteArray()
        }
}
