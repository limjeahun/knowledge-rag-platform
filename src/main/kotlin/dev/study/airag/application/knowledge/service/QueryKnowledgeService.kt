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

    /** 원본 본문을 제외한 문서 정보와 현재 색인 상태를 조회한다. */
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

    /** 검색 조건을 검증하고 조건을 충족한 문서 근거를 반환한다. */
    override fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit> = knowledgeIndexPort.search(query)

    /**
     * vector source와 graph fact를 결합해 답변하고 길이 초과 시 문서 근거만 줄여 한 번 재시도한다.
     *
     * 그래프 사실은 축약 과정에서도 보존하여 ontology entailment가 최초 시도와 재시도 사이에
     * 달라지지 않게 한다.
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
