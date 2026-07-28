package dev.study.airag.application.graph.dto.result

/** REST/MCP 표현 형식을 모르는 지식 그래프 개체 조회 결과다. */
data class KnowledgeGraphEntityResult(
    val entityId: String,
    val ontologyVersion: String,
    val type: String,
    val name: String,
    val aliases: Set<String>,
    val evidence: List<KnowledgeGraphEvidenceResult>,
)
