package dev.study.airag.adapter.out.persistence.postgres.graph.repository

import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphEntityEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphEntityEvidenceEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphRelationEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface KnowledgeGraphEntityRepository : JpaRepository<KnowledgeGraphEntityEntity, UUID> {
    fun findByOntologyVersionAndEntityTypeAndNormalizedName(
        ontologyVersion: String,
        entityType: String,
        normalizedName: String,
    ): KnowledgeGraphEntityEntity?

    @Query(
        """
        select entity from KnowledgeGraphEntityEntity entity
        where (
            lower(entity.canonicalName) like lower(concat('%', :text, '%'))
            or lower(entity.aliasesJson) like lower(concat('%', :text, '%'))
        )
          and (:entityType is null or entity.entityType = :entityType)
        order by entity.canonicalName, entity.id
        """,
    )
    fun search(
        text: String,
        entityType: String?,
        pageable: Pageable,
    ): List<KnowledgeGraphEntityEntity>

    @Query(
        """
        select entity from KnowledgeGraphEntityEntity entity
        where not exists (
            select evidence.id from KnowledgeGraphEntityEvidenceEntity evidence
            where evidence.entityId = entity.id
        )
        and not exists (
            select relation.id from KnowledgeGraphRelationEntity relation
            where relation.sourceEntityId = entity.id or relation.targetEntityId = entity.id
        )
        """,
    )
    fun findOrphans(): List<KnowledgeGraphEntityEntity>
}
