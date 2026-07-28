package dev.study.airag.adapter.out.persistence.postgres.graph.repository

import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphEntityEvidenceEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KnowledgeGraphEntityEvidenceRepository : JpaRepository<KnowledgeGraphEntityEvidenceEntity, UUID> {
    fun deleteAllByDocumentId(documentId: UUID)

    fun findAllByEntityIdIn(entityIds: Collection<UUID>): List<KnowledgeGraphEntityEvidenceEntity>
}
