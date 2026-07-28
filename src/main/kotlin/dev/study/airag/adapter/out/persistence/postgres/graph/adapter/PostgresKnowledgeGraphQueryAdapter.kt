package dev.study.airag.adapter.out.persistence.postgres.graph.adapter

import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphEntityEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphRelationEntity
import dev.study.airag.adapter.out.persistence.postgres.graph.mapper.toView
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphEntityEvidenceRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphEntityRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphRelationEvidenceRepository
import dev.study.airag.adapter.out.persistence.postgres.graph.repository.KnowledgeGraphRelationRepository
import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.port.out.KnowledgeGraphQueryPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphNeighborhoodView
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** PostgreSQL 그래프 프로젝션에서 개체 검색과 제한된 이웃 탐색을 수행한다. */
@Component
class PostgresKnowledgeGraphQueryAdapter(
    private val entityRepository: KnowledgeGraphEntityRepository,
    private val entityEvidenceRepository: KnowledgeGraphEntityEvidenceRepository,
    private val relationRepository: KnowledgeGraphRelationRepository,
    private val relationEvidenceRepository: KnowledgeGraphRelationEvidenceRepository,
    private val objectMapper: ObjectMapper,
) : KnowledgeGraphQueryPort {
    override fun searchEntities(query: SearchKnowledgeGraphQuery): List<KnowledgeGraphEntityView> {
        val entities =
            entityRepository.search(
                query.text,
                query.type,
                PageRequest.of(0, query.limit),
            )
        return mapEntities(entities)
    }

    override fun findNeighborhood(query: GetKnowledgeEntityNeighborhoodQuery): KnowledgeGraphNeighborhoodView? {
        val centerId = runCatching { UUID.fromString(query.entityId) }.getOrNull() ?: return null
        val center = entityRepository.findById(centerId).orElse(null) ?: return null
        val visited = linkedSetOf(centerId)
        var frontier = setOf(centerId)
        val relations = linkedMapOf<UUID, KnowledgeGraphRelationEntity>()

        repeat(query.depth) {
            if (frontier.isEmpty() || visited.size >= query.limit || relations.size >= query.limit) return@repeat
            val next = linkedSetOf<UUID>()
            for (relation in relationRepository.findAdjacent(frontier)) {
                if (relations.size >= query.limit || visited.size >= query.limit) break
                if (relation.id in relations) continue
                val endpoints = setOf(relation.sourceEntityId, relation.targetEntityId)
                val unvisited = endpoints - visited
                if (visited.size + unvisited.size > query.limit) continue

                relations[relation.id] = relation
                visited += unvisited
                next += unvisited
            }
            frontier = next
        }

        val entities = entityRepository.findAllById(visited).associateBy { it.id }
        val entityViews = mapEntities(entities.values.toList()).associateBy { it.entityId }
        val relationEvidence =
            relationEvidenceRepository
                .findAllByRelationIdIn(relations.keys)
                .groupBy { it.relationId }
        val entityNames = entities.mapValues { it.value.canonicalName }
        val relationViews =
            relations.values.map { relation ->
                relation.toView(relationEvidence[relation.id].orEmpty(), entityNames)
            }
        return KnowledgeGraphNeighborhoodView(
            center = entityViews.getValue(center.id.toString()),
            entities = entityViews.values.toList(),
            relations = relationViews,
        )
    }

    private fun mapEntities(entities: List<KnowledgeGraphEntityEntity>): List<KnowledgeGraphEntityView> {
        if (entities.isEmpty()) return emptyList()
        val evidenceByEntity =
            entityEvidenceRepository
                .findAllByEntityIdIn(entities.map { it.id })
                .groupBy { it.entityId }
        return entities.map { entity ->
            entity.toView(evidenceByEntity[entity.id].orEmpty(), objectMapper)
        }
    }
}
