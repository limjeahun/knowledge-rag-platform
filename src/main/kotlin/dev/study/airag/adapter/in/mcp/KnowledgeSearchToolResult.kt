package dev.study.airag.adapter.`in`.mcp

import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit

data class KnowledgeSearchToolResult(
    val hits: List<KnowledgeSearchHit>,
)
