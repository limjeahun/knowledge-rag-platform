package dev.study.airag.adapter.out.persistence.postgres.graph

import dev.study.airag.application.port.out.KnowledgeGraphIndexPort
import dev.study.airag.application.port.out.KnowledgeGraphQueryPort
import dev.study.airag.application.port.out.dto.KnowledgeGraphEntityKey
import dev.study.airag.application.port.out.dto.KnowledgeGraphEvidence
import dev.study.airag.application.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphEntity
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphEvidence
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphNeighborhood
import dev.study.airag.application.port.out.dto.StoredKnowledgeGraphRelation
import dev.study.airag.domain.vo.DocumentId
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * provenance-aware 지식 그래프를 PostgreSQL read projection으로 저장하고 조회한다.
 *
 * 개체/관계의 전역 identity와 문서별 evidence를 분리한다. 따라서 같은 "Milvus" 개체를
 * 여러 문서가 공유할 수 있고, 한 문서를 삭제해도 다른 문서의 evidence가 남아 있으면
 * 개체와 관계는 유지된다. 이 저장소는 원문 Source of Truth가 아니며 재색인으로 복원된다.
 */
@Component
class PostgresKnowledgeGraphAdapter(
    private val entityRepository: KnowledgeGraphEntityRepository,
    private val entityEvidenceRepository: KnowledgeGraphEntityEvidenceRepository,
    private val relationRepository: KnowledgeGraphRelationRepository,
    private val relationEvidenceRepository: KnowledgeGraphRelationEvidenceRepository,
    private val projectionRepository: KnowledgeGraphProjectionRepository,
    private val objectMapper: ObjectMapper,
) : KnowledgeGraphIndexPort,
    KnowledgeGraphQueryPort {
    /**
     * 같은 문서의 이전 evidence를 지운 뒤 현재 버전의 evidence를 한 트랜잭션으로 저장한다.
     *
     * identity UUID는 ontology와 자연 키로 결정하므로 재시도해도 같은 개체·관계에 수렴한다.
     * 이전 버전 제거 후 새 저장이 실패하면 PostgreSQL 트랜잭션이 전체 작업을 되돌린다.
     */
    @Transactional
    override fun replace(projection: KnowledgeGraphProjection) {
        removeDocumentEvidence(projection.documentId.value)

        val storedEntities =
            projection.entities.associate { candidate ->
                candidate.key to upsertEntity(projection, candidate.key, candidate.name, candidate.aliases)
            }
        projection.entities.forEach { candidate ->
            val entityId = storedEntities.getValue(candidate.key).id
            entityEvidenceRepository.saveAll(
                candidate.evidence.map { evidence ->
                    KnowledgeGraphEntityEvidenceEntity(
                        id = evidenceId("entity", entityId, projection, evidence),
                        entityId = entityId,
                        documentId = projection.documentId.value,
                        documentVersion = projection.documentVersion,
                        chunkId = evidence.chunkId,
                        evidenceQuote = evidence.quote,
                        confidence = evidence.confidence,
                    )
                },
            )
        }

        projection.relations.forEach { candidate ->
            val sourceId = storedEntities.getValue(candidate.source).id
            val targetId = storedEntities.getValue(candidate.target).id
            val relation =
                relationRepository
                    .findByOntologyVersionAndRelationTypeAndSourceEntityIdAndTargetEntityId(
                        projection.ontologyVersion,
                        candidate.type,
                        sourceId,
                        targetId,
                    ) ?: relationRepository.save(
                    KnowledgeGraphRelationEntity(
                        id = relationId(projection.ontologyVersion, candidate.type, sourceId, targetId),
                        ontologyVersion = projection.ontologyVersion,
                        relationType = candidate.type,
                        sourceEntityId = sourceId,
                        targetEntityId = targetId,
                        createdAt = projection.projectedAt,
                        updatedAt = projection.projectedAt,
                    ),
                )
            relation.updatedAt = projection.projectedAt
            relationEvidenceRepository.saveAll(
                candidate.evidence.map { evidence ->
                    KnowledgeGraphRelationEvidenceEntity(
                        id = evidenceId("relation", relation.id, projection, evidence),
                        relationId = relation.id,
                        documentId = projection.documentId.value,
                        documentVersion = projection.documentVersion,
                        chunkId = evidence.chunkId,
                        evidenceQuote = evidence.quote,
                        confidence = evidence.confidence,
                    )
                },
            )
        }
        projectionRepository.save(
            KnowledgeGraphProjectionEntity(
                documentId = projection.documentId.value,
                documentVersion = projection.documentVersion,
                ontologyVersion = projection.ontologyVersion,
                entityCount = projection.entities.size,
                relationCount = projection.relations.size,
                projectedAt = projection.projectedAt,
            ),
        )
    }

    /** 문서 evidence 제거와 고아 정리를 반복 호출해도 같은 최종 상태가 되게 한다. */
    @Transactional
    override fun remove(documentId: DocumentId) {
        removeDocumentEvidence(documentId.value)
    }

    @Transactional(readOnly = true)
    override fun searchEntities(
        text: String,
        type: String?,
        limit: Int,
    ): List<StoredKnowledgeGraphEntity> {
        val entities = entityRepository.search(text, type, PageRequest.of(0, limit))
        return mapEntities(entities)
    }

    /**
     * depth별 frontier만 확장하여 순환 그래프에서도 같은 개체를 반복 방문하지 않는다.
     *
     * limit은 반환 개체와 관계 양쪽의 상한으로 적용해 큰 허브 개체가 무제한 응답을 만들지
     * 않게 한다.
     */
    @Transactional(readOnly = true)
    override fun findNeighborhood(
        entityId: String,
        depth: Int,
        limit: Int,
    ): StoredKnowledgeGraphNeighborhood? {
        val centerId = runCatching { UUID.fromString(entityId) }.getOrNull() ?: return null
        val center = entityRepository.findById(centerId).orElse(null) ?: return null
        val visited = linkedSetOf(centerId)
        var frontier = setOf(centerId)
        val relations = linkedMapOf<UUID, KnowledgeGraphRelationEntity>()

        repeat(depth) {
            if (frontier.isEmpty() || visited.size >= limit || relations.size >= limit) return@repeat
            val adjacent = relationRepository.findAdjacent(frontier)
            val next = linkedSetOf<UUID>()
            for (relation in adjacent) {
                if (relations.size >= limit || visited.size >= limit) break
                if (relation.id in relations) continue
                val endpoints = setOf(relation.sourceEntityId, relation.targetEntityId)
                val unvisited = endpoints - visited
                if (visited.size + unvisited.size > limit) continue

                relations[relation.id] = relation
                visited += unvisited
                next += unvisited
            }
            frontier = next
        }

        val entities = entityRepository.findAllById(visited).associateBy { it.id }
        val storedEntities = mapEntities(entities.values.toList()).associateBy { it.entityId }
        val relationEvidence =
            relationEvidenceRepository
                .findAllByRelationIdIn(relations.keys)
                .groupBy { it.relationId }
        val storedRelations =
            relations.values.map { relation ->
                StoredKnowledgeGraphRelation(
                    relationId = relation.id.toString(),
                    ontologyVersion = relation.ontologyVersion,
                    type = relation.relationType,
                    sourceEntityId = relation.sourceEntityId.toString(),
                    sourceName = entities.getValue(relation.sourceEntityId).canonicalName,
                    targetEntityId = relation.targetEntityId.toString(),
                    targetName = entities.getValue(relation.targetEntityId).canonicalName,
                    evidence = relationEvidence[relation.id].orEmpty().map { it.toStoredEvidence() },
                )
            }
        return StoredKnowledgeGraphNeighborhood(
            center = storedEntities.getValue(center.id.toString()),
            entities = storedEntities.values.toList(),
            relations = storedRelations,
        )
    }

    private fun upsertEntity(
        projection: KnowledgeGraphProjection,
        key: KnowledgeGraphEntityKey,
        name: String,
        aliases: Set<String>,
    ): KnowledgeGraphEntityEntity {
        val existing =
            entityRepository.findByOntologyVersionAndEntityTypeAndNormalizedName(
                projection.ontologyVersion,
                key.type,
                key.normalizedName,
            )
        if (existing == null) {
            return entityRepository.save(
                KnowledgeGraphEntityEntity(
                    id = entityId(projection.ontologyVersion, key),
                    ontologyVersion = projection.ontologyVersion,
                    entityType = key.type,
                    canonicalName = name,
                    normalizedName = key.normalizedName,
                    aliasesJson = objectMapper.writeValueAsString(aliases),
                    createdAt = projection.projectedAt,
                    updatedAt = projection.projectedAt,
                ),
            )
        }
        val mergedAliases = readAliases(existing.aliasesJson) + aliases + name - existing.canonicalName
        existing.aliasesJson = objectMapper.writeValueAsString(mergedAliases)
        existing.updatedAt = projection.projectedAt
        return existing
    }

    private fun removeDocumentEvidence(documentId: UUID) {
        relationEvidenceRepository.deleteAllByDocumentId(documentId)
        entityEvidenceRepository.deleteAllByDocumentId(documentId)
        projectionRepository.deleteById(documentId)

        /*
         * 관계가 개체를 참조하므로 관계 고아를 먼저 지워야 한다. 그 뒤 evidence도 관계도 없는
         * 개체만 제거하면 다른 문서가 공유하는 노드를 실수로 삭제하지 않는다.
         */
        relationRepository.deleteAll(relationRepository.findOrphans())
        entityRepository.deleteAll(entityRepository.findOrphans())
    }

    private fun mapEntities(entities: List<KnowledgeGraphEntityEntity>): List<StoredKnowledgeGraphEntity> {
        if (entities.isEmpty()) return emptyList()
        val evidenceByEntity =
            entityEvidenceRepository
                .findAllByEntityIdIn(entities.map { it.id })
                .groupBy { it.entityId }
        return entities.map { entity ->
            StoredKnowledgeGraphEntity(
                entityId = entity.id.toString(),
                ontologyVersion = entity.ontologyVersion,
                type = entity.entityType,
                name = entity.canonicalName,
                aliases = readAliases(entity.aliasesJson),
                evidence = evidenceByEntity[entity.id].orEmpty().map { it.toStoredEvidence() },
            )
        }
    }

    private fun KnowledgeGraphEntityEvidenceEntity.toStoredEvidence() =
        StoredKnowledgeGraphEvidence(
            documentId.toString(),
            documentVersion,
            chunkId,
            evidenceQuote,
            confidence,
        )

    private fun KnowledgeGraphRelationEvidenceEntity.toStoredEvidence() =
        StoredKnowledgeGraphEvidence(
            documentId.toString(),
            documentVersion,
            chunkId,
            evidenceQuote,
            confidence,
        )

    private fun readAliases(json: String): Set<String> =
        objectMapper.readValue(json, object : TypeReference<Set<String>>() {})

    private fun entityId(
        ontologyVersion: String,
        key: KnowledgeGraphEntityKey,
    ) = stableUuid("entity|$ontologyVersion|${key.type}|${key.normalizedName}")

    private fun relationId(
        ontologyVersion: String,
        type: String,
        sourceId: UUID,
        targetId: UUID,
    ) = stableUuid("relation|$ontologyVersion|$type|$sourceId|$targetId")

    private fun evidenceId(
        kind: String,
        ownerId: UUID,
        projection: KnowledgeGraphProjection,
        evidence: KnowledgeGraphEvidence,
    ) = stableUuid(
        "$kind-evidence|$ownerId|${projection.documentId}|${projection.documentVersion}|" +
            "${evidence.chunkId}|${evidence.quote}",
    )

    private fun stableUuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
}
