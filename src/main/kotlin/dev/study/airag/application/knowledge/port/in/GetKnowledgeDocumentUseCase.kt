package dev.study.airag.application.knowledge.port.`in`

import dev.study.airag.application.knowledge.dto.result.KnowledgeDocumentResult

fun interface GetKnowledgeDocumentUseCase {
    /** 
     * 원본 본문을 제외한 문서 정보와 현재 색인 상태를 조회한다. 
     */
    fun get(documentId: String): KnowledgeDocumentResult
}
