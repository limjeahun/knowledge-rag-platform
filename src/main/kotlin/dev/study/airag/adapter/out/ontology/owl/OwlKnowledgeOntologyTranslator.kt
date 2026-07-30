package dev.study.airag.adapter.out.ontology.owl

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
import org.semanticweb.owlapi.model.OWLObjectProperty
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.reasoner.OWLReasoner
import org.springframework.stereotype.Component

/**
 * OWL TBox의 형식 의미론을 LLM 추출용 Application ontology 문법으로 번역한다.
 *
 * 이 클래스는 Outbound Adapter 내부의 Anti-Corruption Layer다. OWL class·object property와
 * HermiT Reasoner 같은 기술 타입을 이 경계 안에 가두고, Application에는 안정적인 code와
 * 허용 source/target 집합만 반환한다. `core:extractable true`인 용어만 노출하며 관계 끝점은
 * 수동 목록이 아니라 OWL domain/range에 대한 subclass entailment로 계산한다.
 */
@Component
internal class OwlKnowledgeOntologyTranslator(
    private val catalog: OwlOntologyCatalog,
) {
    /**
     * 검증된 OWL snapshot 전체를 하나의 불변 [KnowledgeOntology]로 번역한다.
     *
     * import closure와 추출 가능한 타입을 먼저 준비하고 [withReasoner] 안에서 EntityType과
     * RelationType을 모두 List와 Set으로 물질화한다. 따라서 Reasoner가 폐기된 뒤 실행될
     * Sequence나 callback이 반환 객체 안에 남지 않는다.
     *
     * @return ontology version과 code 기반 개체·관계 타입 계약
     * @throws IllegalStateException 추출 가능 용어의 code annotation이 없거나 중복된 경우
     * @throws IllegalArgumentException 관계의 domain/range가 정확히 하나로 정의되지 않은 경우
     */
    fun translate(): KnowledgeOntology {
        val snapshot = catalog.load()
        val closure = loadImportClosure(snapshot.rootOntology)

        return withReasoner(snapshot.rootOntology) { reasoner ->
            val context = buildContext(closure, reasoner)
            KnowledgeOntology(
                version = snapshot.version,
                entityTypes = projectEntityTypes(context),
                relationTypes = projectRelationTypes(context),
            )
        }
    }

    /**
     * 루트 ontology 자신을 포함하는 import closure를 즉시 평가해 List로 고정한다.
     *
     * 이후 모든 annotation과 공리 탐색은 이 동일한 목록을 사용하여 Core와 Domain ontology가
     * 서로 다른 검색 범위를 사용하지 않게 한다.
     *
     * @param root 설정에서 선택되고 profile 검증을 통과한 루트 ontology
     * @return 루트와 모든 재귀 import ontology
     */
    private fun loadImportClosure(root: OWLOntology): List<OWLOntology> = root.importsClosure().toList()

    /**
     * 한 번의 번역에서 공유할 OWL 탐색 결과와 살아 있는 Reasoner를 Context로 조립한다.
     *
     * 추출 가능한 class와 code 매핑을 한 번만 계산하여 Entity와 Relation 변환이 동일한 후보
     * 집합을 사용하게 한다.
     *
     * @param closure 현재 루트의 완전히 평가된 import closure
     * @param reasoner 현재 번역 블록 안에서만 유효한 HermiT Reasoner
     * @return 번역 전용 private Context
     */
    private fun buildContext(
        closure: List<OWLOntology>,
        reasoner: OWLReasoner,
    ): OwlProjectionContext {
        val classes = findExtractableClasses(closure)
        return OwlProjectionContext(
            closure = closure,
            extractableClasses = classes,
            codesByClass = classes.associateWith { type -> type.requireCode(closure) },
            reasoner = reasoner,
        )
    }

    /**
     * import closure에서 LLM 추출 문법에 노출할 named OWL class를 중복 없이 찾는다.
     *
     * Sequence는 중간 List 생성을 줄이기 위해 이 탐색 메서드 내부에서만 사용하고 마지막
     * `toList()`에서 즉시 평가한다.
     *
     * @param closure class 선언과 annotation을 검색할 import closure
     * @return IRI 기준으로 중복 제거된 extractable class 목록
     */
    private fun findExtractableClasses(closure: List<OWLOntology>): List<OWLClass> =
        closure
            .asSequence()
            .flatMap { ontology -> ontology.classesInSignature().iterator().asSequence() }
            .distinctBy(OWLClass::getIRI)
            .filter { type -> type.isExtractable(closure) }
            .toList()

    /**
     * 추출 가능한 OWL class를 code와 설명만 가진 Application EntityType으로 변환한다.
     *
     * 결과를 code 순으로 정렬하여 ontology 문서의 statement 순서가 달라져도 LLM prompt와
     * 테스트 결과가 결정적으로 유지된다.
     *
     * @param context class 후보와 code 매핑을 가진 현재 번역 Context
     * @return code 순으로 정렬된 EntityType 목록
     */
    private fun projectEntityTypes(context: OwlProjectionContext): List<OntologyEntityType> =
        context.extractableClasses
            .map { type ->
                OntologyEntityType(
                    code = context.codesByClass.getValue(type),
                    description = type.description(context.closure),
                )
            }.sortedBy(OntologyEntityType::code)

    /**
     * 모든 extractable object property를 방향성과 끝점 제약이 있는 RelationType으로 변환한다.
     *
     * 각 관계의 상세 계산은 [projectRelationType]에 위임하고 최종 결과를 code 순으로 정렬한다.
     *
     * @param context OWL closure, class code와 살아 있는 Reasoner
     * @return code 순으로 정렬되고 완전히 물질화된 RelationType 목록
     */
    private fun projectRelationTypes(context: OwlProjectionContext): List<OntologyRelationType> =
        findExtractableObjectProperties(context.closure)
            .map { property -> projectRelationType(property, context) }
            .sortedBy(OntologyRelationType::code)

    /**
     * import closure에서 LLM 추출 대상으로 표시된 named object property를 중복 없이 찾는다.
     *
     * @param closure property 선언과 annotation을 검색할 import closure
     * @return IRI 기준으로 중복 제거된 extractable object property 목록
     */
    private fun findExtractableObjectProperties(closure: List<OWLOntology>): List<OWLObjectProperty> =
        closure
            .asSequence()
            .flatMap { ontology -> ontology.objectPropertiesInSignature().iterator().asSequence() }
            .distinctBy(OWLObjectProperty::getIRI)
            .filter { property -> property.isExtractable(closure) }
            .toList()

    /**
     * 한 OWL object property를 Application RelationType 하나로 번역한다.
     *
     * code와 설명은 annotation에서 읽고 source/target은 각각 domain/range entailment 결과를
     * 사용한다. property의 방향을 바꾸거나 inverse 관계를 여기서 합성하지 않는다.
     *
     * @param property 번역할 extractable named object property
     * @param context annotation과 entailment 계산에 사용할 현재 Context
     * @return code, 설명과 허용 source/target 타입을 가진 RelationType
     */
    private fun projectRelationType(
        property: OWLObjectProperty,
        context: OwlProjectionContext,
    ): OntologyRelationType =
        OntologyRelationType(
            code = property.requireCode(context.closure),
            description = property.description(context.closure),
            sourceTypes = findAllowedSourceTypes(property, context),
            targetTypes = findAllowedTargetTypes(property, context),
        )

    /**
     * object property의 유일한 OWL domain expression을 만족하는 개체 type code를 계산한다.
     *
     * @param property source 제약을 읽을 object property
     * @param context import closure와 Reasoner를 가진 현재 Context
     * @return domain의 하위 타입으로 entail되는 extractable class code 집합
     */
    private fun findAllowedSourceTypes(
        property: OWLObjectProperty,
        context: OwlProjectionContext,
    ): Set<String> {
        val boundaries =
            context.closure
                .flatMap { ontology -> ontology.objectPropertyDomainAxioms(property).toList() }
                .map { axiom -> axiom.domain }
        return findAllowedTypes(boundaries, context)
    }

    /**
     * object property의 유일한 OWL range expression을 만족하는 개체 type code를 계산한다.
     *
     * @param property target 제약을 읽을 object property
     * @param context import closure와 Reasoner를 가진 현재 Context
     * @return range의 하위 타입으로 entail되는 extractable class code 집합
     */
    private fun findAllowedTargetTypes(
        property: OWLObjectProperty,
        context: OwlProjectionContext,
    ): Set<String> {
        val boundaries =
            context.closure
                .flatMap { ontology -> ontology.objectPropertyRangeAxioms(property).toList() }
                .map { axiom -> axiom.range }
        return findAllowedTypes(boundaries, context)
    }

    /**
     * OWL class expression 경계를 만족하는 모든 extractable class code를 계산한다.
     *
     * 각 후보 class `C`에 대해 HermiT가 `C SubClassOf boundary`를 entail하는지 확인한다.
     * union class expression과 상속 계층을 Kotlin 조건으로 복제하지 않으므로 OWL이 의미의
     * 단일 기준으로 유지된다.
     *
     * @param boundaries 한 관계에 선언된 domain 또는 range class expression
     * @param context 후보 class, code와 Reasoner를 가진 현재 Context
     * @return class 탐색 순서를 보존하는 허용 Application type code 집합
     * @throws IllegalArgumentException boundary expression이 정확히 하나가 아닌 경우
     */
    private fun findAllowedTypes(
        boundaries: List<OWLClassExpression>,
        context: OwlProjectionContext,
    ): Set<String> {
        require(boundaries.size == 1) {
            "추출 관계의 OWL domain/range는 정확히 하나의 class expression이어야 합니다."
        }
        val boundary = boundaries.single()
        val dataFactory = OWLManager.getOWLDataFactory()
        return context.extractableClasses
            .filter { candidate ->
                context.reasoner.isEntailed(
                    dataFactory.getOWLSubClassOfAxiom(candidate, boundary),
                )
            }.mapTo(linkedSetOf()) { candidate -> context.codesByClass.getValue(candidate) }
    }

    /**
     * import closure 전체에서 `core:extractable true` annotation이 있는지 확인한다.
     *
     * @param closure 동일 entity IRI의 annotation을 검색할 import closure
     * @return 하나 이상의 boolean true annotation이 있으면 `true`
     */
    private fun OWLEntity.isExtractable(closure: List<OWLOntology>): Boolean =
        annotationLiterals(closure, EXTRACTABLE_IRI).any(OWLLiteral::parseBoolean)

    /**
     * OWL entity의 유일한 비어 있지 않은 `core:code`를 Application 식별자로 읽는다.
     *
     * code가 없거나 여러 개면 임의 값을 선택하지 않고 배포 오류로 실패시킨다.
     *
     * @param closure code annotation을 검색할 import closure
     * @return 공백이 아닌 유일한 code
     * @throws IllegalStateException code를 하나로 확정할 수 없는 경우
     */
    private fun OWLEntity.requireCode(closure: List<OWLOntology>): String =
        annotationLiterals(closure, CODE_IRI)
            .singleOrNull()
            ?.literal
            ?.takeIf(String::isNotBlank)
            ?: error("추출 가능한 OWL entity에는 고유 code annotation이 필요합니다: $iri")

    /**
     * LLM prompt에 사용할 사람이 읽는 용어 설명을 선택한다.
     *
     * 영어 `rdfs:comment`, 첫 번째 다른 언어 comment, IRI short form 순으로 선택한다.
     *
     * @param closure 다국어 annotation을 검색할 import closure
     * @return 비어 있지 않은 용어 설명
     */
    private fun OWLEntity.description(closure: List<OWLOntology>): String {
        val comments = annotationLiterals(closure, RDFS_COMMENT_IRI)
        return comments.firstOrNull { literal -> literal.lang == "en" }?.literal
            ?: comments.firstOrNull()?.literal
            ?: iri.shortForm
    }

    /**
     * 동일 entity IRI에 연결된 특정 annotation property의 literal 값만 수집한다.
     *
     * @param closure annotation assertion을 검색할 import closure
     * @param propertyIri 조회할 annotation property IRI
     * @return ontology와 assertion 탐색 순서를 보존한 literal 목록
     */
    private fun OWLEntity.annotationLiterals(
        closure: List<OWLOntology>,
        propertyIri: IRI,
    ): List<OWLLiteral> =
        closure
            .flatMap { ontology -> ontology.annotationAssertionAxioms(iri).toList() }
            .filter { axiom -> axiom.property.iri == propertyIri }
            .map { axiom -> axiom.annotation.value }
            .filterIsInstance<OWLLiteral>()

    /**
     * HermiT Reasoner를 생성하고 블록의 성공·실패와 관계없이 같은 메서드에서 폐기한다.
     *
     * 블록은 Reasoner에 의존하는 Sequence나 callback을 반환해서는 안 되고 결과를 즉시
     * 물질화해야 한다. [translate]는 이 계약에 따라 List와 Set만 반환한다.
     *
     * @param ontology 분류할 루트 ontology
     * @param block Reasoner가 살아 있는 동안 즉시 실행할 번역 작업
     * @return 블록 안에서 완전히 계산된 결과
     */
    private fun <T> withReasoner(
        ontology: OWLOntology,
        block: (OWLReasoner) -> T,
    ): T {
        val reasoner = ReasonerFactory().createReasoner(ontology)
        return try {
            block(reasoner)
        } finally {
            reasoner.dispose()
        }
    }

    /**
     * 한 번의 OWL 번역 안에서만 공유하는 기술 전용 계산 Context다.
     *
     * [reasoner]는 상태와 생명주기를 가진 객체라 복사·동등성 비교 대상이 아니다. 따라서
     * `data class`가 아닌 private 일반 class로 두고 [withReasoner] 블록 밖으로 노출하지 않는다.
     */
    private class OwlProjectionContext(
        val closure: List<OWLOntology>,
        val extractableClasses: List<OWLClass>,
        val codesByClass: Map<OWLClass, String>,
        val reasoner: OWLReasoner,
    )

    private companion object {
        val CODE_IRI: IRI = IRI.create("urn:airag:ontology:knowledge-core#code")
        val EXTRACTABLE_IRI: IRI = IRI.create("urn:airag:ontology:knowledge-core#extractable")
        val RDFS_COMMENT_IRI: IRI = IRI.create("http://www.w3.org/2000/01/rdf-schema#comment")
    }
}
