package dev.study.airag.adapter.out.persistence.postgres.graph.repository

import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphRelationEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphRelationEvidenceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface KnowledgeGraphRelationRepository : JpaRepository<KnowledgeGraphRelationEntity, UUID> {
    fun findByOntologyVersionAndRelationTypeAndSourceEntityIdAndTargetEntityId(
        ontologyVersion: String,
        relationType: String,
        sourceEntityId: UUID,
        targetEntityId: UUID,
    ): KnowledgeGraphRelationEntity?

    @Query(
        """
        select relation from KnowledgeGraphRelationEntity relation
        where relation.sourceEntityId in :entityIds or relation.targetEntityId in :entityIds
        order by relation.relationType, relation.id
        """,
    )
    fun findAdjacent(entityIds: Collection<UUID>): List<KnowledgeGraphRelationEntity>

    @Query(
        """
        select relation from KnowledgeGraphRelationEntity relation
        where not exists (
            select evidence.id from KnowledgeGraphRelationEvidenceEntity evidence
            where evidence.relationId = relation.id
        )
        """,
    )
    fun findOrphans(): List<KnowledgeGraphRelationEntity>
}
