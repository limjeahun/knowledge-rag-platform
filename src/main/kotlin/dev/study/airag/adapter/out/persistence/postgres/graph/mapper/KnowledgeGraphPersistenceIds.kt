package dev.study.airag.adapter.out.persistence.postgres.graph.mapper

import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityKey
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEvidence
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphEntity
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphRelation
import java.nio.charset.StandardCharsets
import java.util.UUID

internal fun KnowledgeGraphProjection.entityId(key: KnowledgeGraphEntityKey): UUID =
    stableUuid("entity|$ontologyVersion|${key.type}|${key.normalizedName}")

internal fun KnowledgeGraphProjection.relationId(relation: ProjectedGraphRelation): UUID =
    stableUuid(
        "relation|$ontologyVersion|${relation.type}|${entityId(relation.source)}|${entityId(relation.target)}",
    )

internal fun KnowledgeGraphProjection.entityEvidenceId(
    entity: ProjectedGraphEntity,
    evidence: KnowledgeGraphEvidence,
): UUID = evidenceId("entity-evidence|${entityId(entity.key)}", evidence)

internal fun KnowledgeGraphProjection.relationEvidenceId(
    relation: ProjectedGraphRelation,
    evidence: KnowledgeGraphEvidence,
): UUID = evidenceId("relation-evidence|${relationId(relation)}", evidence)

private fun KnowledgeGraphProjection.evidenceId(
    owner: String,
    evidence: KnowledgeGraphEvidence,
): UUID =
    stableUuid(
        "$owner|$documentId|$documentVersion|${evidence.chunkId}|${evidence.quote}",
    )

private fun stableUuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
