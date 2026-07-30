package dev.study.airag.adapter.out.persistence.postgres.graph.registry

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** 문서별 활성·은퇴 Fuseki projection 이력을 조회하고 저장하는 Spring Data 경계다. */
interface KnowledgeGraphProjectionRunRepository : JpaRepository<KnowledgeGraphProjectionRunEntity, UUID> {
    fun findAllByDocumentIdAndStatus(
        documentId: UUID,
        status: String,
    ): List<KnowledgeGraphProjectionRunEntity>
}
