package dev.study.airag.application.port.`in`

import dev.study.airag.application.dto.result.KnowledgeDocumentResult

fun interface ListKnowledgeDocumentsUseCase {
    fun list(): List<KnowledgeDocumentResult>
}