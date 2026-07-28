package dev.study.airag.application.dto.query

/** 한 개체에서 제한된 단계만 이동하여 연결된 지식과 provenance를 조회한다. */
data class GetKnowledgeEntityNeighborhoodQuery(
    val entityId: String,
    val depth: Int = 1,
    val limit: Int = 50,
)
