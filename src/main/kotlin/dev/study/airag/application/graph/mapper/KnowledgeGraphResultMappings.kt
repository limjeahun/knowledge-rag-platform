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
 *
 * @receiver 저장 기술에서 반환한 중심 개체와 제한된 이웃 view
 * @return Web·MCP가 각자 응답 형태로 변환할 수 있는 Application result
 */
internal fun KnowledgeGraphNeighborhoodView.toResult() =
    KnowledgeGraphNeighborhoodResult(
        center = center.toResult(),
        entities = entities.map { it.toResult() },
        relations = relations.map { it.toResult() },
    )

/** 개체의 ontology 타입, 별칭과 모든 provenance를 손실 없이 Application result로 복사한다. */
private fun KnowledgeGraphEntityView.toResult() =
    KnowledgeGraphEntityResult(
        entityId = entityId,
        ontologyVersion = ontologyVersion,
        type = type,
        name = name,
        aliases = aliases,
        evidence = evidence.map { it.toResult() },
    )

/** 방향성 관계와 assertion kind를 유지한 채 evidence를 Application result로 변환한다. */
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

/** 저장소 독립 evidence view의 문서·청크·quote·confidence를 동일 의미의 result로 복사한다. */
private fun KnowledgeGraphEvidenceView.toResult() =
    KnowledgeGraphEvidenceResult(
        documentId = documentId,
        documentVersion = documentVersion,
        chunkId = chunkId,
        quote = quote,
        confidence = confidence,
    )

/**
 * 검색된 개체 view 목록을 입력 순서를 유지하여 Application result 목록으로 변환한다.
 *
 * 빈 목록은 그대로 빈 목록이 되며 별도의 조회나 정렬을 수행하지 않는다.
 */
internal fun List<KnowledgeGraphEntityView>.toResults() = map { it.toResult() }
