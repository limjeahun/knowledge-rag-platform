package dev.study.airag.application.port.out

import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphEntity
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphNeighborhood

/** 저장 기술을 노출하지 않고 개체 검색과 제한된 이웃 탐색을 제공한다. */
interface KnowledgeGraphQueryPort {
    fun searchEntities(
        text: String,
        type: String?,
        limit: Int,
    ): List<StoredKnowledgeGraphEntity>

    fun findNeighborhood(
        entityId: String,
        depth: Int,
        limit: Int,
    ): StoredKnowledgeGraphNeighborhood?
}
