package dev.study.airag.application.knowledge.service

import dev.study.airag.application.graph.dto.query.FindRelevantKnowledgeGraphFactsQuery
import dev.study.airag.application.graph.port.`in`.FindRelevantKnowledgeGraphFactsUseCase
import dev.study.airag.application.knowledge.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.knowledge.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.knowledge.dto.result.KnowledgeAnswerResult
import dev.study.airag.application.knowledge.dto.result.KnowledgeDocumentResult
import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit
import dev.study.airag.application.knowledge.exception.KnowledgeAnswerGenerationException
import dev.study.airag.application.knowledge.exception.KnowledgeAnswerGenerationFailure
import dev.study.airag.application.knowledge.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.application.knowledge.port.`in`.AnswerKnowledgeQuestionUseCase
import dev.study.airag.application.knowledge.port.`in`.GetKnowledgeDocumentUseCase
import dev.study.airag.application.knowledge.port.`in`.ListKnowledgeDocumentsUseCase
import dev.study.airag.application.knowledge.port.`in`.SearchKnowledgeUseCase
import dev.study.airag.application.knowledge.port.out.GenerateKnowledgeAnswerPort
import dev.study.airag.application.knowledge.port.out.KnowledgeDocumentPort
import dev.study.airag.application.knowledge.port.out.KnowledgeIndexPort
import dev.study.airag.application.knowledge.port.out.dto.KnowledgeAnswerGenerationRequest
import dev.study.airag.domain.vo.DocumentId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 문서 상태를 조회하고 저장된 지식에서 검색하거나 근거 기반 답변을 만든다.
 *
 * 답변은 Milvus 문서 근거와 Fuseki asserted/inferred 사실을 각각 한 번 조회해 생성과 응답에
 * 재사용한다. 그래프 조회 활성화 여부는 graph Use Case의 policy가 결정하므로 이 서비스는
 * 저장 기술이나 feature flag를 알지 않는다.
 */
@Service
class QueryKnowledgeService(
    private val documentPort: KnowledgeDocumentPort,
    private val knowledgeIndexPort: KnowledgeIndexPort,
    private val generateAnswerPort: GenerateKnowledgeAnswerPort,
    private val graphFactsUseCase: FindRelevantKnowledgeGraphFactsUseCase,
) : GetKnowledgeDocumentUseCase,
    ListKnowledgeDocumentsUseCase,
    SearchKnowledgeUseCase,
    AnswerKnowledgeQuestionUseCase {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 문자열 ID를 Domain VO로 변환해 원본 본문을 제외한 문서 상태를 조회한다.
     *
     * 원본 content는 목록·상태 API에 불필요하고 민감할 수 있어 Result에 포함하지 않는다.
     *
     * @param documentId 외부 경계에서 받은 UUID 문자열
     * @return 문서 식별 정보, version, 색인 상태와 상태 변경 시각
     * @throws IllegalArgumentException UUID 형식이 유효하지 않은 경우
     * @throws KnowledgeDocumentNotFoundException 문서가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    override fun get(documentId: String): KnowledgeDocumentResult {
        val document =
            documentPort.findById(DocumentId.from(documentId))
                ?: throw KnowledgeDocumentNotFoundException(documentId)
        return KnowledgeDocumentResult(
            documentId = document.id.toString(),
            title = document.title,
            version = document.version,
            status = document.status,
            failureReason = document.failureReason,
            registeredAt = document.registeredAt,
            indexedAt = document.indexedAt,
        )
    }

    /**
     * 모든 문서를 원본 본문 없이 조회 Result로 변환한다.
     *
     * Repository가 반환한 순서를 보존하며 정렬이나 pagination 정책을 임의로 추가하지 않는다.
     *
     * @return 현재 저장된 모든 문서의 식별 정보와 색인 상태
     */
    @Transactional(readOnly = true)
    override fun list(): List<KnowledgeDocumentResult> =
        documentPort.findAll().map { document ->
            KnowledgeDocumentResult(
                documentId = document.id.toString(),
                title = document.title,
                version = document.version,
                status = document.status,
                failureReason = document.failureReason,
                registeredAt = document.registeredAt,
                indexedAt = document.indexedAt,
            )
        }

    /**
     * 검증된 벡터 검색 Query를 검색 인덱스 Port에 전달한다.
     *
     * @return score와 문서·청크 추적 정보가 포함된 Milvus 독립 검색 결과
     */
    override fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit> = knowledgeIndexPort.search(query)

    /**
     * vector source와 graph fact를 결합해 답변하고 길이 초과 시 문서 근거만 줄여 한 번 재시도한다.
     *
     * 그래프 사실은 축약 과정에서도 보존하여 ontology entailment가 최초 시도와 재시도 사이에
     * 달라지지 않게 한다.
     *
     * @param query 질문, vector topK와 similarity threshold
     * @return 자연어 답변과 생성에 실제 사용한 vector source 및 graph fact
     * @throws KnowledgeAnswerGenerationException 재시도 불가능한 공급자 오류 또는 재시도 실패
     */
    override fun answer(query: AnswerKnowledgeQuestionQuery): KnowledgeAnswerResult {
        val sources =
            knowledgeIndexPort.search(
                SearchKnowledgeQuery(query.question, query.topK, query.similarityThreshold),
            )
        val graphFacts =
            graphFactsUseCase
                .findRelevantFacts(
                    FindRelevantKnowledgeGraphFactsQuery(query.question, MAX_GRAPH_FACTS),
                )
        val request = KnowledgeAnswerGenerationRequest(query.question, sources, graphFacts)
        return try {
            KnowledgeAnswerResult(
                query.question,
                generateAnswerPort.generate(request),
                sources,
                graphFacts,
            )
        } catch (exception: KnowledgeAnswerGenerationException) {
            if (exception.failure != KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED) {
                throw exception
            }
            retryWithReducedSources(request)
        }
    }

    /**
     * 근거를 축소해 AI 답변 길이 초과로 인해 발생한 예외를 재시도한다.
     *
     * 최초 요청에서 이미 조회한 graph fact는 그대로 재사용하며 외부 저장소를 다시 조회하지
     * 않는다. 반환 Result에는 재시도에서 실제 사용한 축소 source를 넣어 답변 근거와 일치시킨다.
     *
     * @param request 최초 답변 시도와 동일한 질문·검색 결과·graph fact
     * @return 한 번의 재시도로 생성한 답변과 실제 사용 근거
     */
    private fun retryWithReducedSources(request: KnowledgeAnswerGenerationRequest): KnowledgeAnswerResult {
        val reducedSources = reduceSources(request.sources)
        logger.warn(
            "AI 답변 길이 초과로 근거를 축소해 한 번 재시도합니다. sourceCount={}, retrySourceCount={}",
            request.sources.size,
            reducedSources.size,
        )
        val answer =
            generateAnswerPort.generate(
                request.copy(sources = reducedSources),
            )
        return KnowledgeAnswerResult(request.question, answer, reducedSources, request.graphFacts)
    }

    /**
     * 답변 context 길이를 줄이되 최소 한 개의 비어 있지 않은 문서 근거를 보존한다.
     *
     * source가 여러 개면 앞쪽의 절반을 유지하여 검색 순위를 보존한다. 하나뿐이면 content를
     * 절반으로 자르고 원문이 비어 있지 않았던 경우 최소 한 글자를 유지한다.
     *
     * @param sources 검색 점수 순서의 문서 근거
     * @return 재시도용 축소 근거
     */
    private fun reduceSources(sources: List<KnowledgeSearchHit>): List<KnowledgeSearchHit> {
        if (sources.size > 1) {
            return sources.take((sources.size + 1) / 2)
        }
        return sources.map { source ->
            source.copy(content = source.content.take((source.content.length / 2).coerceAtLeast(1)))
        }
    }

    private companion object {
        const val MAX_GRAPH_FACTS = 100
    }
}
