package dev.study.airag.application.graph.mapper

import dev.study.airag.application.graph.dto.result.KnowledgeGraphEntityResult
import dev.study.airag.application.graph.dto.result.KnowledgeGraphEvidenceResult
import dev.study.airag.application.graph.dto.result.KnowledgeGraphNeighborhoodResult
import dev.study.airag.application.graph.dto.result.KnowledgeGraphRelationResult
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEvidenceView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphNeighborhoodView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphRelationView

/**
 * Outbound Adapter view를 프로토콜 독립적인 Application result로 복사하는 순수 변환 함수다.
 *
 * 관계 방향, assertion kind와 evidence를 그대로 보존하며 조회나 정책 판단을 수행하지 않는다.
 */
internal fun KnowledgeGraphNeighborhoodView.toResult() =
    KnowledgeGraphNeighborhoodResult(
        center = center.toResult(),
        entities = entities.map { it.toResult() },
        relations = relations.map { it.toResult() },
    )

private fun KnowledgeGraphEntityView.toResult() =
    KnowledgeGraphEntityResult(
        entityId = entityId,
        ontologyVersion = ontologyVersion,
        type = type,
        name = name,
        aliases = aliases,
        evidence = evidence.map { it.toResult() },
    )

private fun KnowledgeGraphRelationView.toResult() =
    KnowledgeGraphRelationResult(
        relationId = relationId,
        ontologyVersion = ontologyVersion,
        type = type,
        sourceEntityId = sourceEntityId,
        sourceName = sourceName,
        targetEntityId = targetEntityId,
        targetName = targetName,
        evidence = evidence.map { it.toResult() },
        assertionKind = assertionKind,
    )

private fun KnowledgeGraphEvidenceView.toResult() =
    KnowledgeGraphEvidenceResult(
        documentId = documentId,
        documentVersion = documentVersion,
        chunkId = chunkId,
        quote = quote,
        confidence = confidence,
    )

internal fun List<KnowledgeGraphEntityView>.toResults() = map { it.toResult() }
