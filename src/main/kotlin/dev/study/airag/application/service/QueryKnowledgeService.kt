package dev.study.airag.application.service

import dev.study.airag.application.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.dto.result.KnowledgeAnswerResult
import dev.study.airag.application.dto.result.KnowledgeDocumentResult
import dev.study.airag.application.dto.result.KnowledgeSearchHit
import dev.study.airag.application.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.application.port.`in`.AnswerKnowledgeQuestionUseCase
import dev.study.airag.application.port.`in`.GetKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.SearchKnowledgeUseCase
import dev.study.airag.application.port.out.GenerateKnowledgeAnswerPort
import dev.study.airag.application.port.out.KnowledgeDocumentPort
import dev.study.airag.application.port.out.KnowledgeIndexPort
import dev.study.airag.domain.vo.DocumentId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 문서 상태를 조회하고 저장된 지식에서 검색하거나 근거 기반 답변을 만든다. */
@Service
class QueryKnowledgeService(
    private val documentPort: KnowledgeDocumentPort,
    private val knowledgeIndexPort: KnowledgeIndexPort,
    private val generateAnswerPort: GenerateKnowledgeAnswerPort,
) : GetKnowledgeDocumentUseCase,
    SearchKnowledgeUseCase,
    AnswerKnowledgeQuestionUseCase {
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

    /** 검색 조건을 검증하고 조건을 충족한 문서 근거를 반환한다. */
    override fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit> {
        validate(query.query, query.topK, query.similarityThreshold)
        return knowledgeIndexPort.search(query)
    }

    /**
     * 검색 결과를 그대로 답변 생성에 사용하여 반환 출처와 실제 근거를 일치시킨다.
     */
    override fun answer(query: AnswerKnowledgeQuestionQuery): KnowledgeAnswerResult {
        validate(query.question, query.topK, query.similarityThreshold)
        val sources =
            knowledgeIndexPort.search(
                SearchKnowledgeQuery(query.question, query.topK, query.similarityThreshold),
            )
        val answer = generateAnswerPort.generate(query.question, sources)
        return KnowledgeAnswerResult(query.question, answer, sources)
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
