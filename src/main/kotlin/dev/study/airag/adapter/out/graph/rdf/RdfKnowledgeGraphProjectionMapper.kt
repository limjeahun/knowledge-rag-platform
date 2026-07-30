package dev.study.airag.adapter.out.graph.rdf

import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityKey
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEvidence
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphRelation
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * 검증된 애플리케이션 그래프를 asserted RDF와 statement-level provenance로 변환한다.
 *
 * 개체·관계 ID는 ontology version과 의미 자연 키로 결정적으로 생성한다. 같은 의미의 개체는
 * 여러 문서에서 동일 IRI를 사용하지만, evidence ID에는 문서 버전·chunk·quote가 포함되어
 * 각 원문의 기원을 잃지 않는다. 이 Adapter는 inferred 문장을 생성하지 않는다.
 */
@Component
class RdfKnowledgeGraphProjectionMapper(
    private val vocabulary: OwlRdfVocabularyIndex,
) {
    /**
     * 검증된 Application projection을 새 asserted 모델과 provenance 모델로 분리한다.
     *
     * 개체 타입과 관계 triple은 asserted에, 각 triple을 원문 quote와 연결하는 reified
     * statement는 provenance에 기록한다. 동일 의미 키와 ontology version은 문서가 달라도
     * 같은 개체 IRI를 만들고, 원문 근거는 문서 버전별 별도 assertion IRI를 가진다.
     *
     * @param projection ontology code, 정규화된 개체 키와 검증 완료 evidence를 가진 문서 프로젝션
     * @return 서로 독립적인 asserted 및 statement-provenance Jena 모델
     * @throws IllegalStateException ontology code를 실제 OWL class/property IRI로 해석할 수 없는 경우
     */
    fun map(projection: KnowledgeGraphProjection): RdfProjectionModels {
        val asserted = ModelFactory.createDefaultModel()
        val provenance = ModelFactory.createDefaultModel()
        val activity =
            provenance
                .createResource(extractionActivityIri(projection))
                .addProperty(
                    RdfKnowledgeGraphVocabulary.RDF_TYPE,
                    RdfKnowledgeGraphVocabulary.PROV_ACTIVITY,
                ).addLiteral(
                    RdfKnowledgeGraphVocabulary.GENERATED_AT_TIME,
                    provenance.createTypedLiteral(projection.projectedAt.toString(), XSDDatatype.XSDdateTime),
                )

        projection.entities.forEach { entity ->
            val entityId = entityId(projection, entity.key)
            val subject =
                asserted
                    .createResource("${RdfKnowledgeGraphVocabulary.ENTITY_NAMESPACE}$entityId")
                    .addProperty(RdfKnowledgeGraphVocabulary.RDF_TYPE, vocabulary.classFor(entity.key.type))
                    .addLiteral(RdfKnowledgeGraphVocabulary.ENTITY_ID, entityId.toString())
                    .addLiteral(RdfKnowledgeGraphVocabulary.ONTOLOGY_VERSION, projection.ontologyVersion)
                    .addLiteral(RdfKnowledgeGraphVocabulary.PREF_LABEL, entity.name)
            entity.aliases.forEach { alias ->
                subject.addLiteral(RdfKnowledgeGraphVocabulary.ALT_LABEL, alias)
            }
            entity.evidence.forEach { evidence ->
                addEvidence(
                    provenance = provenance,
                    projection = projection,
                    statementSubject = subject,
                    statementPredicate = RdfKnowledgeGraphVocabulary.RDF_TYPE,
                    statementObject = vocabulary.classFor(entity.key.type),
                    evidence = evidence,
                    assertionId = evidenceId("entity|$entityId", projection, evidence),
                    relationId = null,
                    activity = activity,
                )
            }
        }

        projection.relations.forEach { relation ->
            val source = asserted.createResource(entityIri(projection, relation.source))
            val target = asserted.createResource(entityIri(projection, relation.target))
            val predicate = vocabulary.propertyFor(relation.type)
            asserted.add(source, predicate, target)
            val relationId = relationId(projection, relation)
            relation.evidence.forEach { evidence ->
                addEvidence(
                    provenance = provenance,
                    projection = projection,
                    statementSubject = source,
                    statementPredicate = predicate,
                    statementObject = target,
                    evidence = evidence,
                    assertionId = evidenceId("relation|$relationId", projection, evidence),
                    relationId = relationId,
                    activity = activity,
                )
            }
        }
        return RdfProjectionModels(asserted, provenance)
    }

    /**
     * 하나의 asserted statement를 정확한 원문 위치와 연결하는 RDF reification을 추가한다.
     *
     * 개체 타입 statement에는 relation ID가 없고 관계 statement에만 안정적인 relation ID를
     * 기록한다. `prov:wasDerivedFrom`은 문서·청크 IRI를, `prov:wasGeneratedBy`는 이번 문서
     * 버전의 추출 activity를 가리킨다.
     *
     * @param provenance statement evidence를 추가할 대상 모델
     * @param projection 문서 ID·버전·ontology version을 제공하는 현재 프로젝션
     * @param statementSubject 근거가 설명하는 asserted triple의 subject
     * @param statementPredicate 근거가 설명하는 asserted triple의 predicate
     * @param statementObject 근거가 설명하는 asserted triple의 object
     * @param evidence 검증 완료된 chunk ID, 정확한 quote와 confidence
     * @param assertionId 문서 근거 단위의 결정적 assertion UUID
     * @param relationId 관계 근거인 경우의 결정적 relation UUID, 개체 타입 근거이면 `null`
     * @param activity 이번 추출 실행을 나타내는 PROV activity
     */
    private fun addEvidence(
        provenance: Model,
        projection: KnowledgeGraphProjection,
        statementSubject: Resource,
        statementPredicate: Property,
        statementObject: RDFNode,
        evidence: KnowledgeGraphEvidence,
        assertionId: UUID,
        relationId: UUID?,
        activity: Resource,
    ) {
        val documentId = projection.documentId.toString()
        val chunkIri = "${RdfKnowledgeGraphVocabulary.CHUNK_NAMESPACE}$documentId:${evidence.chunkId}"
        provenance
            .createResource("${RdfKnowledgeGraphVocabulary.ASSERTION_NAMESPACE}$assertionId")
            .addProperty(RdfKnowledgeGraphVocabulary.RDF_TYPE, RdfKnowledgeGraphVocabulary.RDF_STATEMENT)
            .addProperty(RdfKnowledgeGraphVocabulary.RDF_TYPE, RdfKnowledgeGraphVocabulary.STATEMENT_EVIDENCE)
            .addProperty(RdfKnowledgeGraphVocabulary.RDF_SUBJECT, statementSubject)
            .addProperty(RdfKnowledgeGraphVocabulary.RDF_PREDICATE, statementPredicate)
            .addProperty(RdfKnowledgeGraphVocabulary.RDF_OBJECT, statementObject)
            .addLiteral(RdfKnowledgeGraphVocabulary.DOCUMENT_ID, documentId)
            .addLiteral(RdfKnowledgeGraphVocabulary.DOCUMENT_VERSION, projection.documentVersion)
            .addLiteral(RdfKnowledgeGraphVocabulary.CHUNK_ID, evidence.chunkId)
            .addLiteral(RdfKnowledgeGraphVocabulary.QUOTE, evidence.quote)
            .addLiteral(RdfKnowledgeGraphVocabulary.CONFIDENCE, evidence.confidence)
            .addLiteral(RdfKnowledgeGraphVocabulary.ASSERTION_KIND, "ASSERTED")
            .addLiteral(RdfKnowledgeGraphVocabulary.ONTOLOGY_VERSION, projection.ontologyVersion)
            .addProperty(RdfKnowledgeGraphVocabulary.WAS_DERIVED_FROM, provenance.createResource(chunkIri))
            .addProperty(RdfKnowledgeGraphVocabulary.WAS_GENERATED_BY, activity)
            .also { assertion ->
                relationId?.let { assertion.addLiteral(RdfKnowledgeGraphVocabulary.RELATION_ID, it.toString()) }
            }
    }

    /**
     * 개체 의미 키를 runtime RDF entity namespace의 안정적인 IRI로 변환한다.
     *
     * @return `urn:airag:entity:{stableUuid}` 형태의 절대 IRI
     */
    private fun entityIri(
        projection: KnowledgeGraphProjection,
        key: KnowledgeGraphEntityKey,
    ): String = "${RdfKnowledgeGraphVocabulary.ENTITY_NAMESPACE}${entityId(projection, key)}"

    /**
     * ontology version, 타입, 정규화 이름으로 의미상 동일한 개체의 UUID를 생성한다.
     *
     * 문서 ID를 포함하지 않으므로 여러 문서에서 같은 ontology 타입과 정규화 이름을 가진
     * 개체는 하나의 IRI로 합쳐진다. ontology version이 달라지면 의미 계약이 달라져 ID도 바뀐다.
     */
    private fun entityId(
        projection: KnowledgeGraphProjection,
        key: KnowledgeGraphEntityKey,
    ): UUID = stableUuid("entity|${projection.ontologyVersion}|${key.type}|${key.normalizedName}")

    /**
     * ontology version, 관계 타입, 양 끝 개체 ID로 방향성 관계의 안정적인 UUID를 생성한다.
     *
     * source와 target 순서를 보존하므로 반대 방향 관계는 별개의 ID가 된다.
     */
    private fun relationId(
        projection: KnowledgeGraphProjection,
        relation: ProjectedGraphRelation,
    ): UUID =
        stableUuid(
            "relation|${projection.ontologyVersion}|${relation.type}|" +
                "${entityId(projection, relation.source)}|${entityId(projection, relation.target)}",
        )

    /**
     * statement 소유자와 정확한 문서 근거를 조합해 assertion provenance UUID를 생성한다.
     *
     * 같은 statement라도 문서 버전, 청크 또는 quote가 다르면 별도 근거 레코드가 된다.
     */
    private fun evidenceId(
        owner: String,
        projection: KnowledgeGraphProjection,
        evidence: KnowledgeGraphEvidence,
    ): UUID =
        stableUuid(
            "$owner|${projection.documentId}|${projection.documentVersion}|${evidence.chunkId}|${evidence.quote}",
        )

    /**
     * 한 문서 버전의 LLM 추출 실행을 식별하는 PROV activity IRI를 만든다.
     *
     * 재시도해도 같은 문서 버전은 같은 activity IRI를 사용하여 프로젝션이 멱등적이다.
     */
    private fun extractionActivityIri(projection: KnowledgeGraphProjection): String =
        "${RdfKnowledgeGraphVocabulary.ACTIVITY_NAMESPACE}extraction:" +
            "${projection.documentId}:v${projection.documentVersion}"

    /**
     * 의미 키 문자열을 플랫폼과 실행 시점에 독립적인 name-based UUID로 변환한다.
     *
     * 무작위 UUID를 사용하지 않아 같은 입력 projection의 RDF IRI가 재색인에서도 유지된다.
     */
    private fun stableUuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
}
