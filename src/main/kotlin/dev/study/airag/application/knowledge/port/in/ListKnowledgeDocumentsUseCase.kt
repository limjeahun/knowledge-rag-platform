package dev.study.airag.application.knowledge.port.`in`

import dev.study.airag.application.knowledge.dto.result.KnowledgeDocumentResult

/** 원문 본문을 노출하지 않고 등록된 지식 문서의 상태 목록을 조회한다. */
fun interface ListKnowledgeDocumentsUseCase {
    fun list(): List<KnowledgeDocumentResult>
}
