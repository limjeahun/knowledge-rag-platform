package dev.study.airag.domain.model

/** 현재 문서가 전달받은 버전의 색인 요청을 어떻게 처리해야 하는지 나타낸다. */
enum class DocumentIndexingDecision {
    INDEX,
    ALREADY_INDEXED,
    VERSION_MISMATCH,
    DOCUMENT_DELETED,
}
