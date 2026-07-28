package dev.study.airag.adapter.out.persistence.postgres.graph.adapter

import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphEntityEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.mapper.mergeKnowledgeGraphAliases
import dev.study.airag.adapter.out.persistence.postgres.graph.mapper.toEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.mapper.toEntityEvidence
import dev.study.airag.adapter.out.persistence.postgres.graph.mapper.toRelation
import dev.study.airag.adapter.out.persistence.postgres.graph.mapper.toRelationEvidence
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphEntityEvidenceRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphEntityRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphProjectionRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphRelationEvidenceRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphRelationRepository
import dev.study.airag.application.graph.port.out.KnowledgeGraphIndexPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphEntity
import dev.study.airag.domain.vo.DocumentId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * 문서별 지식 그래프 프로젝션을 하나의 PostgreSQL 트랜잭션으로 교체하거나 제거한다.
 */
@Component
class PostgresKnowledgeGraphIndexAdapter(
    private val entityRepository: KnowledgeGraphEntityRepository,
    private val entityEvidenceRepository: KnowledgeGraphEntityEvidenceRepository,
    private val relationRepository: KnowledgeGraphRelationRepository,
    private val relationEvidenceRepository: KnowledgeGraphRelationEvidenceRepository,
    private val projectionRepository: KnowledgeGraphProjectionRepository,
    private val objectMapper: ObjectMapper,
) : KnowledgeGraphIndexPort {
    @Transactional
    override fun replace(projection: KnowledgeGraphProjection) {
        removeDocumentEvidence(projection.documentId.value)

        val savedEntities =
            projection.entities.associate { candidate ->
                candidate.key to upsertEntity(projection, candidate)
            }
        projection.entities.forEach { candidate ->
            entityEvidenceRepository.saveAll(
                candidate.evidence.map { evidence ->
                    projection.toEntityEvidence(candidate, evidence)
                },
            )
        }

        projection.relations.forEach { candidate ->
            val sourceId = savedEntities.getValue(candidate.source).id
            val targetId = savedEntities.getValue(candidate.target).id
            val relation =
                relationRepository
                    .findByOntologyVersionAndRelationTypeAndSourceEntityIdAndTargetEntityId(
                        projection.ontologyVersion,
                        candidate.type,
                        sourceId,
                        targetId,
                    ) ?: relationRepository.save(projection.toRelation(candidate))
            relation.updatedAt = projection.projectedAt
            relationEvidenceRepository.saveAll(
                candidate.evidence.map { evidence ->
                    projection.toRelationEvidence(candidate, evidence)
                },
            )
        }
        projectionRepository.save(projection.toEntity())
    }

    @Transactional
    override fun remove(documentId: DocumentId) {
        removeDocumentEvidence(documentId.value)
    }

    private fun upsertEntity(
        projection: KnowledgeGraphProjection,
        candidate: ProjectedGraphEntity,
    ): KnowledgeGraphEntityEntity {
        val existing =
            entityRepository.findByOntologyVersionAndEntityTypeAndNormalizedName(
                projection.ontologyVersion,
                candidate.key.type,
                candidate.key.normalizedName,
            )
        if (existing == null) {
            return entityRepository.save(projection.toEntity(candidate, objectMapper))
        }
        existing.aliasesJson = objectMapper.mergeKnowledgeGraphAliases(existing, candidate)
        existing.updatedAt = projection.projectedAt
        return existing
    }

    private fun removeDocumentEvidence(documentId: UUID) {
        relationEvidenceRepository.deleteAllByDocumentId(documentId)
        entityEvidenceRepository.deleteAllByDocumentId(documentId)
        projectionRepository.deleteById(documentId)

        relationRepository.deleteAll(relationRepository.findOrphans())
        entityRepository.deleteAll(entityRepository.findOrphans())
    }
}
