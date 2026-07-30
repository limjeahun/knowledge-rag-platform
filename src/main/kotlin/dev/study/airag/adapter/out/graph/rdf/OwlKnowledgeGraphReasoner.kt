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
    /** asserted RDF가 TBox와 일관될 때만 inferred/provenance 모델을 생성한다. */
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

    private fun Model.asTurtle(): ByteArray =
        ByteArrayOutputStream().use { output ->
            RDFDataMgr.write(output, this, Lang.TURTLE)
            output.toByteArray()
        }
}
