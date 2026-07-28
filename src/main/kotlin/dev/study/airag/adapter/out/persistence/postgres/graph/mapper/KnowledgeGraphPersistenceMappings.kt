package dev.study.airag.adapter.out.persistence.postgres.graph.mapper

import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphEntityEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphEntityEvidenceEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphProjectionEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphRelationEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphRelationEvidenceEntity
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEvidence
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEvidenceView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphRelationView
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphEntity
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphRelation
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.UUID

internal fun KnowledgeGraphProjection.toEntity(
    candidate: ProjectedGraphEntity,
    objectMapper: ObjectMapper,
) = KnowledgeGraphEntityEntity(
    id = entityId(candidate.key),
    ontologyVersion = ontologyVersion,
    entityType = candidate.key.type,
    canonicalName = candidate.name,
    normalizedName = candidate.key.normalizedName,
    aliasesJson = objectMapper.writeKnowledgeGraphAliases(candidate.aliases),
    createdAt = projectedAt,
    updatedAt = projectedAt,
)

internal fun KnowledgeGraphProjection.toEntityEvidence(
    candidate: ProjectedGraphEntity,
    evidence: KnowledgeGraphEvidence,
) = KnowledgeGraphEntityEvidenceEntity(
    id = entityEvidenceId(candidate, evidence),
    entityId = entityId(candidate.key),
    documentId = documentId.value,
    documentVersion = documentVersion,
    chunkId = evidence.chunkId,
    evidenceQuote = evidence.quote,
    confidence = evidence.confidence,
)

internal fun KnowledgeGraphProjection.toRelation(candidate: ProjectedGraphRelation) =
    KnowledgeGraphRelationEntity(
        id = relationId(candidate),
        ontologyVersion = ontologyVersion,
        relationType = candidate.type,
        sourceEntityId = entityId(candidate.source),
        targetEntityId = entityId(candidate.target),
        createdAt = projectedAt,
        updatedAt = projectedAt,
    )

internal fun KnowledgeGraphProjection.toRelationEvidence(
    candidate: ProjectedGraphRelation,
    evidence: KnowledgeGraphEvidence,
) = KnowledgeGraphRelationEvidenceEntity(
    id = relationEvidenceId(candidate, evidence),
    relationId = relationId(candidate),
    documentId = documentId.value,
    documentVersion = documentVersion,
    chunkId = evidence.chunkId,
    evidenceQuote = evidence.quote,
    confidence = evidence.confidence,
)

internal fun KnowledgeGraphProjection.toEntity() =
    KnowledgeGraphProjectionEntity(
        documentId = documentId.value,
        documentVersion = documentVersion,
        ontologyVersion = ontologyVersion,
        entityCount = entities.size,
        relationCount = relations.size,
        projectedAt = projectedAt,
    )

internal fun KnowledgeGraphEntityEntity.toView(
    evidence: List<KnowledgeGraphEntityEvidenceEntity>,
    objectMapper: ObjectMapper,
) = KnowledgeGraphEntityView(
    entityId = id.toString(),
    ontologyVersion = ontologyVersion,
    type = entityType,
    name = canonicalName,
    aliases = objectMapper.readKnowledgeGraphAliases(aliasesJson),
    evidence = evidence.map { it.toView() },
)

internal fun KnowledgeGraphRelationEntity.toView(
    evidence: List<KnowledgeGraphRelationEvidenceEntity>,
    entityNames: Map<UUID, String>,
) = KnowledgeGraphRelationView(
    relationId = id.toString(),
    ontologyVersion = ontologyVersion,
    type = relationType,
    sourceEntityId = sourceEntityId.toString(),
    sourceName = entityNames.getValue(sourceEntityId),
    targetEntityId = targetEntityId.toString(),
    targetName = entityNames.getValue(targetEntityId),
    evidence = evidence.map { it.toView() },
)

internal fun ObjectMapper.mergeKnowledgeGraphAliases(
    entity: KnowledgeGraphEntityEntity,
    candidate: ProjectedGraphEntity,
): String {
    val aliases =
        readKnowledgeGraphAliases(entity.aliasesJson) +
            candidate.aliases +
            candidate.name -
            entity.canonicalName
    return writeKnowledgeGraphAliases(aliases)
}

private fun KnowledgeGraphEntityEvidenceEntity.toView() =
    KnowledgeGraphEvidenceView(
        documentId = documentId.toString(),
        documentVersion = documentVersion,
        chunkId = chunkId,
        quote = evidenceQuote,
        confidence = confidence,
    )

private fun KnowledgeGraphRelationEvidenceEntity.toView() =
    KnowledgeGraphEvidenceView(
        documentId = documentId.toString(),
        documentVersion = documentVersion,
        chunkId = chunkId,
        quote = evidenceQuote,
        confidence = confidence,
    )

private fun ObjectMapper.readKnowledgeGraphAliases(json: String): Set<String> =
    readValue(json, object : TypeReference<Set<String>>() {})

private fun ObjectMapper.writeKnowledgeGraphAliases(aliases: Set<String>): String = writeValueAsString(aliases)
