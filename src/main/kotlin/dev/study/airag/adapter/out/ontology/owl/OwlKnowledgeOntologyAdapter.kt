package dev.study.airag.adapter.out.ontology.owl

import dev.study.airag.application.graph.port.out.KnowledgeOntologyPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology
import dev.study.airag.application.graph.port.out.dto.OntologyEntityType
import dev.study.airag.application.graph.port.out.dto.OntologyRelationType
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLClassExpression
import org.semanticweb.owlapi.model.OWLEntity
import org.semanticweb.owlapi.model.OWLLiteral
import org.semanticweb.owlapi.model.OWLOntology
import org.springframework.stereotype.Component

/**
 * OWL class와 object property의 형식 의미론을 LLM 추출용 애플리케이션 문법으로 투영한다.
 *
 * `core:extractable true`와 유일한 `core:code`가 있는 용어만 모델에 노출한다. 관계의 허용
 * source/target code는 JSON 목록이 아니라 HermiT가 domain/range class expression에 대해
 * 계산한 subclass entailment에서 얻는다. 다국어 설명은 기존 프롬프트 언어를 유지하기 위해
 * 영어 `rdfs:comment`를 우선하고, 영어가 없을 때 다른 설명이나 IRI short form을 사용한다.
 */
@Component
class OwlKnowledgeOntologyAdapter(
    private val catalog: OwlOntologyCatalog,
) : KnowledgeOntologyPort {
    private val ontology: KnowledgeOntology by lazy(::projectOntology)

    override fun load(): KnowledgeOntology = ontology

    private fun projectOntology(): KnowledgeOntology {
        val snapshot = catalog.load()
        val root = snapshot.rootOntology
        val closure = root.importsClosure().toList()
        val classes =
            closure
                .flatMap { it.classesInSignature().toList() }
                .distinctBy { it.iri }
                .filter { it.isExtractable(closure) }
        val codes = classes.associateWith { it.requireCode(closure) }
        val reasoner = ReasonerFactory().createReasoner(root)
        return try {
            val entityTypes =
                classes
                    .map { type ->
                        OntologyEntityType(
                            code = codes.getValue(type),
                            description = type.description(closure),
                        )
                    }.sortedBy(OntologyEntityType::code)
            val relationTypes =
                closure
                    .flatMap { it.objectPropertiesInSignature().toList() }
                    .distinctBy { it.iri }
                    .filter { it.isExtractable(closure) }
                    .map { property ->
                        OntologyRelationType(
                            code = property.requireCode(closure),
                            description = property.description(closure),
                            sourceTypes =
                                allowedTypes(
                                    classes,
                                    codes,
                                    closure
                                        .flatMap { it.objectPropertyDomainAxioms(property).toList() }
                                        .map { it.domain },
                                    reasoner::isEntailed,
                                ),
                            targetTypes =
                                allowedTypes(
                                    classes,
                                    codes,
                                    closure
                                        .flatMap { it.objectPropertyRangeAxioms(property).toList() }
                                        .map { it.range },
                                    reasoner::isEntailed,
                                ),
                        )
                    }.sortedBy(OntologyRelationType::code)
            KnowledgeOntology(snapshot.version, entityTypes, relationTypes)
        } finally {
            reasoner.dispose()
        }
    }

    private fun allowedTypes(
        classes: List<OWLClass>,
        codes: Map<OWLClass, String>,
        boundaries: List<OWLClassExpression>,
        isEntailed: (org.semanticweb.owlapi.model.OWLAxiom) -> Boolean,
    ): Set<String> {
        require(boundaries.size == 1) {
            "추출 관계의 OWL domain/range는 정확히 하나의 class expression이어야 합니다."
        }
        val dataFactory = OWLManager.getOWLDataFactory()
        return classes
            .filter { candidate ->
                isEntailed(dataFactory.getOWLSubClassOfAxiom(candidate, boundaries.single()))
            }.mapTo(linkedSetOf()) { codes.getValue(it) }
    }

    private fun OWLEntity.isExtractable(closure: List<OWLOntology>): Boolean =
        annotationLiterals(closure, EXTRACTABLE_IRI).any { it.parseBoolean() }

    private fun OWLEntity.requireCode(closure: List<OWLOntology>): String =
        annotationLiterals(closure, CODE_IRI)
            .singleOrNull()
            ?.literal
            ?.takeIf(String::isNotBlank)
            ?: error("추출 가능한 OWL entity에는 고유 code annotation이 필요합니다: $iri")

    private fun OWLEntity.description(closure: List<OWLOntology>): String {
        val comments = annotationLiterals(closure, RDFS_COMMENT_IRI)
        val preferred = comments.firstOrNull { it.lang == "en" } ?: comments.firstOrNull()
        return preferred?.literal ?: iri.shortForm
    }

    private fun OWLEntity.annotationLiterals(
        closure: List<OWLOntology>,
        propertyIri: IRI,
    ): List<OWLLiteral> =
        closure
            .flatMap { ontology -> ontology.annotationAssertionAxioms(iri).toList() }
            .filter { axiom -> axiom.property.iri == propertyIri }
            .map { axiom -> axiom.annotation.value }
            .filterIsInstance<OWLLiteral>()

    private companion object {
        val CODE_IRI: IRI = IRI.create("urn:airag:ontology:knowledge-core#code")
        val EXTRACTABLE_IRI: IRI = IRI.create("urn:airag:ontology:knowledge-core#extractable")
        val RDFS_COMMENT_IRI: IRI = IRI.create("http://www.w3.org/2000/01/rdf-schema#comment")
    }
}
