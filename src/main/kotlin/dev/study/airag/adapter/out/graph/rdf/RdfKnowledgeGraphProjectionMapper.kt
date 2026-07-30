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
    /** Application 모델이나 Jena 모델을 변경하지 않고 새 asserted/provenance 모델을 만든다. */
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

    private fun entityIri(
        projection: KnowledgeGraphProjection,
        key: KnowledgeGraphEntityKey,
    ): String = "${RdfKnowledgeGraphVocabulary.ENTITY_NAMESPACE}${entityId(projection, key)}"

    private fun entityId(
        projection: KnowledgeGraphProjection,
        key: KnowledgeGraphEntityKey,
    ): UUID = stableUuid("entity|${projection.ontologyVersion}|${key.type}|${key.normalizedName}")

    private fun relationId(
        projection: KnowledgeGraphProjection,
        relation: ProjectedGraphRelation,
    ): UUID =
        stableUuid(
            "relation|${projection.ontologyVersion}|${relation.type}|" +
                "${entityId(projection, relation.source)}|${entityId(projection, relation.target)}",
        )

    private fun evidenceId(
        owner: String,
        projection: KnowledgeGraphProjection,
        evidence: KnowledgeGraphEvidence,
    ): UUID =
        stableUuid(
            "$owner|${projection.documentId}|${projection.documentVersion}|${evidence.chunkId}|${evidence.quote}",
        )

    private fun extractionActivityIri(projection: KnowledgeGraphProjection): String =
        "${RdfKnowledgeGraphVocabulary.ACTIVITY_NAMESPACE}extraction:" +
            "${projection.documentId}:v${projection.documentVersion}"

    private fun stableUuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
}
