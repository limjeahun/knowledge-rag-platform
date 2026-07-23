package dev.study.airag.application.dto.command

/** 검색과 답변에 사용할 원본 지식을 등록한다. */
data class RegisterKnowledgeDocumentCommand(
    val title: String,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
)
