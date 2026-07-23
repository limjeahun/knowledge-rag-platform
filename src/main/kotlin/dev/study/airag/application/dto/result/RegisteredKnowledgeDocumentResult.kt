package dev.study.airag.application.dto.result

import dev.study.airag.domain.model.DocumentIndexingStatus
import dev.study.airag.domain.model.KnowledgeDocument

/** 문서 등록 또는 재색인 요청이 접수된 직후의 문서 식별자와 상태다. */
data class RegisteredKnowledgeDocumentResult(
    val documentId: String,
    val status: DocumentIndexingStatus,
) {
    companion object {
        // 문서 도메인 모델을 결과 DTO로 변환한다.
        fun from(document: KnowledgeDocument): RegisteredKnowledgeDocumentResult =
            RegisteredKnowledgeDocumentResult(document.id.toString(), document.status)
    }
}
