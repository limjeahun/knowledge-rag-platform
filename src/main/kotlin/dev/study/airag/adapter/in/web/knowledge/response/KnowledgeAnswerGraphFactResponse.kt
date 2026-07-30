package dev.study.airag.adapter.`in`.web.knowledge.response

data class KnowledgeAnswerGraphFactResponse(
    val relationId: String,
    val ontologyVersion: String,
    val assertionKind: String,
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
)
