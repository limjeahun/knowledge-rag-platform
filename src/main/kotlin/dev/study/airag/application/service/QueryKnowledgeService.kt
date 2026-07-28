package dev.study.airag.application.service

import dev.study.airag.application.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.dto.result.KnowledgeAnswerResult
import dev.study.airag.application.dto.result.KnowledgeDocumentResult
import dev.study.airag.application.dto.result.KnowledgeSearchHit
import dev.study.airag.application.exception.KnowledgeAnswerGenerationException
import dev.study.airag.application.exception.KnowledgeAnswerGenerationFailure
import dev.study.airag.application.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.application.port.`in`.AnswerKnowledgeQuestionUseCase
import dev.study.airag.application.port.`in`.GetKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.ListKnowledgeDocumentsUseCase
import dev.study.airag.application.port.`in`.SearchKnowledgeUseCase
import dev.study.airag.application.port.out.GenerateKnowledgeAnswerPort
import dev.study.airag.application.port.out.KnowledgeDocumentPort
import dev.study.airag.application.port.out.KnowledgeIndexPort
import dev.study.airag.domain.vo.DocumentId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 문서 상태를 조회하고 저장된 지식에서 검색하거나 근거 기반 답변을 만든다. */
@Service
class QueryKnowledgeService(
    private val documentPort: KnowledgeDocumentPort,
    private val knowledgeIndexPort: KnowledgeIndexPort,
    private val generateAnswerPort: GenerateKnowledgeAnswerPort,
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
    override fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit> {
        validate(query.query, query.topK, query.similarityThreshold)
        return knowledgeIndexPort.search(query)
    }

    /** 검색 결과로 답변하고 길이 초과 시 근거를 축소해 한 번만 재시도한다. */
    override fun answer(query: AnswerKnowledgeQuestionQuery): KnowledgeAnswerResult {
        validate(query.question, query.topK, query.similarityThreshold)
        val sources =
            knowledgeIndexPort.search(
                SearchKnowledgeQuery(query.question, query.topK, query.similarityThreshold),
            )
        return try {
            KnowledgeAnswerResult(
                query.question,
                generateAnswerPort.generate(query.question, sources),
                sources,
            )
        } catch (exception: KnowledgeAnswerGenerationException) {
            if (exception.failure != KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED) {
                throw exception
            }
            retryWithReducedSources(query.question, sources)
        }
    }

    /**
     * 근거를 축소해 AI 답변 길이 초과로 인해 발생한 예외를 재시도한다.
     */
    private fun retryWithReducedSources(
        question: String,
        sources: List<KnowledgeSearchHit>,
    ): KnowledgeAnswerResult {
        val reducedSources = reduceSources(sources)
        logger.warn(
            "AI 답변 길이 초과로 근거를 축소해 한 번 재시도합니다. sourceCount={}, retrySourceCount={}",
            sources.size,
            reducedSources.size,
        )
        val answer = generateAnswerPort.generate(question, reducedSources)
        return KnowledgeAnswerResult(question, answer, reducedSources)
    }

    private fun reduceSources(sources: List<KnowledgeSearchHit>): List<KnowledgeSearchHit> {
        if (sources.size > 1) {
            return sources.take((sources.size + 1) / 2)
        }
        return sources.map { source ->
            source.copy(content = source.content.take((source.content.length / 2).coerceAtLeast(1)))
        }
    }

    private fun validate(
        text: String,
        topK: Int,
        threshold: Double,
    ) {
        require(text.isNotBlank()) { "검색어나 질문은 비어 있을 수 없습니다." }
        require(topK in 1..20) { "topK는 1 이상 20 이하이어야 합니다." }
        require(threshold in 0.0..1.0) { "similarityThreshold는 0.0 이상 1.0 이하이어야 합니다." }
    }
}
