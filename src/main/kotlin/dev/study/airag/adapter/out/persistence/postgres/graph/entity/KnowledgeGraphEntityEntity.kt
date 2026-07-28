package dev.study.airag.adapter.out.persistence.postgres.graph.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * 여러 문서가 함께 참조할 수 있는 전역 그래프 개체다.
 *
 * 원문 사실은 이 행이 아니라 별도 evidence 행에 있다. 같은 ontology 버전·타입·정규화 이름을
 * 공유하는 문서는 하나의 개체에 각자의 evidence를 추가한다.
 */
@Entity
@Table(
    name = "knowledge_graph_entities",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_knowledge_graph_entity_identity",
            columnNames = ["ontology_version", "entity_type", "normalized_name"],
        ),
    ],
)
class KnowledgeGraphEntityEntity(
    @field:Id
    @field:Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @field:Column(name = "ontology_version", nullable = false, length = 120)
    var ontologyVersion: String,
    @field:Column(name = "entity_type", nullable = false, length = 120)
    var entityType: String,
    @field:Column(name = "canonical_name", nullable = false, length = 300)
    var canonicalName: String,
    @field:Column(name = "normalized_name", nullable = false, length = 300)
    var normalizedName: String,
    @field:Column(name = "aliases_json", nullable = false, columnDefinition = "TEXT")
    var aliasesJson: String,
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
