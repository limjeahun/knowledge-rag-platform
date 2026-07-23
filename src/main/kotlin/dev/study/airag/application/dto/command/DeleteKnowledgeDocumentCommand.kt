package dev.study.airag.application.dto.command

/** 지식 문서 삭제와 검색 인덱스 제거를 요청한다. */
data class DeleteKnowledgeDocumentCommand(
    val documentId: String,
)
