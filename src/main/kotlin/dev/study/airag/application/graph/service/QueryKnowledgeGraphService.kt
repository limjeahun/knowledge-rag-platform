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
 * ontology code 검증과 retrieval 상한은 Application이 소유하고 실제 SPARQL 전략은 Outbound Port 뒤에 둔다.
 * 그래프 기능이 비활성화된 경우 relevant fact 조회만 빈 결과로 단락한다.
 */
@Service
class QueryKnowledgeGraphService(
    private val ontologyPort: KnowledgeOntologyPort,
    private val queryPort: KnowledgeGraphQueryPort,
    private val retrievalPolicy: KnowledgeGraphRetrievalPolicy,
) : SearchKnowledgeGraphUseCase,
    GetKnowledgeEntityNeighborhoodUseCase,
    FindRelevantKnowledgeGraphFactsUseCase {
    /**
     * 검색값을 정규화하고 선택적 type code를 현재 배포 ontology로 검증한 뒤 개체를 조회한다.
     *
     * 알 수 없는 type을 저장 Adapter까지 전달하지 않아 빈 결과와 잘못된 요청을 구분한다.
     * Outbound view는 순수 mapper를 통해 프로토콜 독립 Application result로 변환한다.
     *
     * @param query Web 또는 다른 Inbound Adapter가 만든 이름·type·limit 검색 조건
     * @return 이름과 원문 evidence가 포함된 개체 검색 결과
     * @throws IllegalArgumentException type code가 현재 ontology에 존재하지 않는 경우
     */
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
     *
     * 깊이와 limit 자체는 Query 생성 시 검증되어 있으며 저장 기술별 탐색은 Port에 위임한다.
     * 중심 개체 absence는 Application의 명시적 not-found 예외로 바꿔 Web와 MCP가 동일한 의미를
     * 사용하게 한다.
     *
     * @param query 중심 entity ID, 탐색 깊이와 반환 상한
     * @return 중심 개체, 발견된 개체와 방향성 관계
     * @throws KnowledgeGraphEntityNotFoundException 활성 그래프에 중심 개체가 없는 경우
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
     *
     * 비활성 상태에서는 Fuseki를 호출하지 않고 빈 목록을 반환하여 기존 Vector RAG 동작을
     * 유지한다. 호출자가 더 큰 limit을 요청해도 배포 설정의 최대 사실 수를 넘기지 않는다.
     *
     * @param query 자연어 질문과 호출자가 요청한 사실 상한
     * @return asserted/inferred 구분과 직접 evidence가 포함된 GraphRAG context
     */
    @Transactional(readOnly = true)
    override fun findRelevantFacts(query: FindRelevantKnowledgeGraphFactsQuery): List<KnowledgeGraphFactResult> {
        if (!retrievalPolicy.enabled) return emptyList()
        val boundedQuery =
            query.copy(
                limit = minOf(query.limit, retrievalPolicy.maxFacts),
                seedChunkIds =
                    query.seedChunkIds
                        .distinct()
                        .take(retrievalPolicy.maxSeedChunks),
                maxHops = minOf(query.maxHops, retrievalPolicy.maxHops),
            )
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
