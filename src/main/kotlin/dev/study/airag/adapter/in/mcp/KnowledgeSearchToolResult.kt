package dev.study.airag.adapter.`in`.mcp

import dev.study.airag.application.dto.result.KnowledgeSearchHit

data class KnowledgeSearchToolResult(
    val hits: List<KnowledgeSearchHit>,
)
