package dev.study.airag.adapter.out.persistence.postgres.graph.repository

import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphRelationEvidenceEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KnowledgeGraphRelationEvidenceRepository : JpaRepository<KnowledgeGraphRelationEvidenceEntity, UUID> {
    fun deleteAllByDocumentId(documentId: UUID)

    fun findAllByRelationIdIn(relationIds: Collection<UUID>): List<KnowledgeGraphRelationEvidenceEntity>
}
