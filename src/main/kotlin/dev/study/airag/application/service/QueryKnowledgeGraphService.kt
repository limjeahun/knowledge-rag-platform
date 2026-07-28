package dev.study.airag.application.service

import dev.study.airag.application.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.dto.result.KnowledgeGraphEntityResult
import dev.study.airag.application.dto.result.KnowledgeGraphEvidenceResult
import dev.study.airag.application.dto.result.KnowledgeGraphNeighborhoodResult
import dev.study.airag.application.dto.result.KnowledgeGraphRelationResult
import dev.study.airag.application.exception.KnowledgeGraphEntityNotFoundException
import dev.study.airag.application.port.`in`.GetKnowledgeEntityNeighborhoodUseCase
import dev.study.airag.application.port.`in`.SearchKnowledgeGraphUseCase
import dev.study.airag.application.port.out.KnowledgeGraphQueryPort
import dev.study.airag.application.port.out.KnowledgeOntologyPort
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphEntity
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphEvidence
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphRelation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 지식 그래프를 이름으로 찾거나 개체 중심의 제한된 이웃 구조로 조회한다. */
@Service
class QueryKnowledgeGraphService(
    private val ontologyPort: KnowledgeOntologyPort,
    private val queryPort: KnowledgeGraphQueryPort,
) : SearchKnowledgeGraphUseCase,
    GetKnowledgeEntityNeighborhoodUseCase {
    @Transactional(readOnly = true)
    override fun search(query: SearchKnowledgeGraphQuery): List<KnowledgeGraphEntityResult> {
        require(query.text.isNotBlank()) { "그래프 검색어는 비어 있을 수 없습니다." }
        require(query.limit in 1..100) { "그래프 검색 limit은 1 이상 100 이하이어야 합니다." }
        val normalizedType = query.type?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedType != null) {
            require(normalizedType in ontologyPort.load().entityTypesByCode) {
                "온톨로지에 없는 개체 타입입니다: $normalizedType"
            }
        }
        return queryPort.searchEntities(query.text.trim(), normalizedType, query.limit).map { it.toResult() }
    }

    /**
     * 무제한 그래프 순회가 큰 조회와 순환을 만들지 않도록 깊이와 반환 건수를 제한한다.
     */
    @Transactional(readOnly = true)
    override fun getNeighborhood(query: GetKnowledgeEntityNeighborhoodQuery): KnowledgeGraphNeighborhoodResult {
        require(query.entityId.isNotBlank()) { "그래프 개체 ID는 비어 있을 수 없습니다." }
        require(query.depth in 1..2) { "그래프 탐색 depth는 1 이상 2 이하이어야 합니다." }
        require(query.limit in 1..100) { "그래프 탐색 limit은 1 이상 100 이하이어야 합니다." }
        val neighborhood =
            queryPort.findNeighborhood(query.entityId, query.depth, query.limit)
                ?: throw KnowledgeGraphEntityNotFoundException(query.entityId)
        return KnowledgeGraphNeighborhoodResult(
            center = neighborhood.center.toResult(),
            entities = neighborhood.entities.map { it.toResult() },
            relations = neighborhood.relations.map { it.toResult() },
        )
    }

    private fun StoredKnowledgeGraphEntity.toResult() =
        KnowledgeGraphEntityResult(entityId, ontologyVersion, type, name, aliases, evidence.map { it.toResult() })

    private fun StoredKnowledgeGraphRelation.toResult() =
        KnowledgeGraphRelationResult(
            relationId,
            ontologyVersion,
            type,
            sourceEntityId,
            sourceName,
            targetEntityId,
            targetName,
            evidence.map { it.toResult() },
        )

    private fun StoredKnowledgeGraphEvidence.toResult() =
        KnowledgeGraphEvidenceResult(documentId, documentVersion, chunkId, quote, confidence)
}
