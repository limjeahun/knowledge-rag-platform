package dev.study.airag.adapter.out.persistence.postgres.graph.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/** source와 target 방향을 가진 전역 관계이며, 사실 근거는 별도 evidence 행이 소유한다. */
@Entity
@Table(
    name = "knowledge_graph_relations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_knowledge_graph_relation_identity",
            columnNames = ["ontology_version", "relation_type", "source_entity_id", "target_entity_id"],
        ),
    ],
)
class KnowledgeGraphRelationEntity(
    @field:Id
    @field:Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @field:Column(name = "ontology_version", nullable = false, length = 120)
    var ontologyVersion: String,
    @field:Column(name = "relation_type", nullable = false, length = 120)
    var relationType: String,
    @field:Column(name = "source_entity_id", nullable = false)
    var sourceEntityId: UUID,
    @field:Column(name = "target_entity_id", nullable = false)
    var targetEntityId: UUID,
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
