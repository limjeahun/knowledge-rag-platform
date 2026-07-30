package dev.study.airag.application.graph.service

import dev.study.airag.application.graph.dto.query.FindRelevantKnowledgeGraphFactsQuery
import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.dto.result.KnowledgeGraphEntityResult
import dev.study.airag.application.graph.dto.result.KnowledgeGraphFactResult
import dev.study.airag.application.graph.dto.result.KnowledgeGraphNeighborhoodResult
import dev.study.airag.application.graph.exception.KnowledgeGraphEntityNotFoundException
import dev.study.airag.application.graph.mapper.toResult
import dev.study.airag.application.graph.mapper.toResults
import dev.study.airag.application.graph.policy.KnowledgeGraphRetrievalPolicy
import dev.study.airag.application.graph.port.`in`.FindRelevantKnowledgeGraphFactsUseCase
import dev.study.airag.application.graph.port.`in`.GetKnowledgeEntityNeighborhoodUseCase
import dev.study.airag.application.graph.port.`in`.SearchKnowledgeGraphUseCase
import dev.study.airag.application.graph.port.out.KnowledgeGraphQueryPort
import dev.study.airag.application.graph.port.out.KnowledgeOntologyPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 지식 그래프를 이름으로 찾거나 개체 중심의 제한된 이웃 구조로 조회한다.
 *
 * ontology code 검증과 retrieval 상한은 Application이 소유하고 실제 SPARQL 전략은 Outbound
 * Port 뒤에 둔다. 그래프 기능이 비활성화된 경우 relevant fact 조회만 빈 결과로 단락한다.
 */
@Service
class QueryKnowledgeGraphService(
    private val ontologyPort: KnowledgeOntologyPort,
    private val queryPort: KnowledgeGraphQueryPort,
    private val retrievalPolicy: KnowledgeGraphRetrievalPolicy,
) : SearchKnowledgeGraphUseCase,
    GetKnowledgeEntityNeighborhoodUseCase,
    FindRelevantKnowledgeGraphFactsUseCase {
    /** ontology에 존재하는 선택적 type code를 검증한 뒤 개체 검색 결과를 반환한다. */
    @Transactional(readOnly = true)
    override fun search(query: SearchKnowledgeGraphQuery): List<KnowledgeGraphEntityResult> {
        val normalizedQuery = query.normalized()
        if (normalizedQuery.type != null) {
            require(normalizedQuery.type in ontologyPort.load().entityTypesByCode) {
                "온톨로지에 없는 개체 타입입니다: ${normalizedQuery.type}"
            }
        }
        return queryPort.searchEntities(normalizedQuery).toResults()
    }

    /**
     * 무제한 그래프 순회가 큰 조회와 순환을 만들지 않도록 깊이와 반환 건수를 제한한다.
     */
    @Transactional(readOnly = true)
    override fun getNeighborhood(query: GetKnowledgeEntityNeighborhoodQuery): KnowledgeGraphNeighborhoodResult {
        val neighborhood =
            queryPort.findNeighborhood(query)
                ?: throw KnowledgeGraphEntityNotFoundException(query.entityId)
        return neighborhood.toResult()
    }

    /**
     * Hybrid GraphRAG가 활성화된 경우에만 질문 관련 사실을 조회하고 policy 상한을 적용한다.
     */
    @Transactional(readOnly = true)
    override fun findRelevantFacts(query: FindRelevantKnowledgeGraphFactsQuery): List<KnowledgeGraphFactResult> {
        if (!retrievalPolicy.enabled) return emptyList()
        val boundedQuery = query.copy(limit = minOf(query.limit, retrievalPolicy.maxFacts))
        return queryPort.findRelevantFacts(boundedQuery).map { fact ->
            KnowledgeGraphFactResult(
                relationId = fact.relationId,
                ontologyVersion = fact.ontologyVersion,
                assertionKind = fact.assertionKind,
                type = fact.type,
                sourceEntityId = fact.sourceEntityId,
                sourceName = fact.sourceName,
                targetEntityId = fact.targetEntityId,
                targetName = fact.targetName,
                evidence =
                    fact.evidence.map {
                        dev.study.airag.application.graph.dto.result.KnowledgeGraphEvidenceResult(
                            documentId = it.documentId,
                            documentVersion = it.documentVersion,
                            chunkId = it.chunkId,
                            quote = it.quote,
                            confidence = it.confidence,
                        )
                    },
            )
        }
    }
}
