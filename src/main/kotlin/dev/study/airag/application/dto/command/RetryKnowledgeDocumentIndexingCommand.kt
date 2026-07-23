package dev.study.airag.application.dto.command

/** 실패한 지식 문서의 재색인을 요청한다. */
data class RetryKnowledgeDocumentIndexingCommand(
    val documentId: String,
)
