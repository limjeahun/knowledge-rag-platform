package dev.study.airag.adapter.out.persistence.postgres.graph

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

interface KnowledgeGraphEntityEvidenceRepository : JpaRepository<KnowledgeGraphEntityEvidenceEntity, UUID> {
    fun deleteAllByDocumentId(documentId: UUID)

    fun findAllByEntityIdIn(entityIds: Collection<UUID>): List<KnowledgeGraphEntityEvidenceEntity>
}

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

interface KnowledgeGraphRelationEvidenceRepository : JpaRepository<KnowledgeGraphRelationEvidenceEntity, UUID> {
    fun deleteAllByDocumentId(documentId: UUID)

    fun findAllByRelationIdIn(relationIds: Collection<UUID>): List<KnowledgeGraphRelationEvidenceEntity>
}

interface KnowledgeGraphProjectionRepository : JpaRepository<KnowledgeGraphProjectionEntity, UUID>
