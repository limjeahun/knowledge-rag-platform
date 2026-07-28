package dev.study.airag.application.knowledge.exception

/** 요청한 식별자로 등록된 지식 문서를 찾을 수 없음을 나타낸다. */
class KnowledgeDocumentNotFoundException(
    documentId: String,
) : NoSuchElementException("지식 문서를 찾을 수 없습니다: $documentId")
