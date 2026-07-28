package dev.study.airag.adapter.out.persistence.postgres.graph

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

/** 한 개체가 실제 문서 어느 구절에서 발견됐는지 보존하는 provenance 행이다. */
@Entity
@Table(name = "knowledge_graph_entity_evidence")
class KnowledgeGraphEntityEvidenceEntity(
    @field:Id
    @field:Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @field:Column(name = "entity_id", nullable = false)
    var entityId: UUID,
    @field:Column(name = "document_id", nullable = false)
    var documentId: UUID,
    @field:Column(name = "document_version", nullable = false)
    var documentVersion: Long,
    @field:Column(name = "chunk_id", nullable = false, length = 500)
    var chunkId: String,
    @field:Column(name = "evidence_quote", nullable = false, columnDefinition = "TEXT")
    var evidenceQuote: String,
    @field:Column(name = "confidence", nullable = false)
    var confidence: Double,
)

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

/** 관계를 주장한 문서 버전과 원문 인용을 보존하는 provenance 행이다. */
@Entity
@Table(name = "knowledge_graph_relation_evidence")
class KnowledgeGraphRelationEvidenceEntity(
    @field:Id
    @field:Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @field:Column(name = "relation_id", nullable = false)
    var relationId: UUID,
    @field:Column(name = "document_id", nullable = false)
    var documentId: UUID,
    @field:Column(name = "document_version", nullable = false)
    var documentVersion: Long,
    @field:Column(name = "chunk_id", nullable = false, length = 500)
    var chunkId: String,
    @field:Column(name = "evidence_quote", nullable = false, columnDefinition = "TEXT")
    var evidenceQuote: String,
    @field:Column(name = "confidence", nullable = false)
    var confidence: Double,
)

/**
 * 문서별 그래프 생성 완료 표식이다.
 *
 * 이 행은 업무 원본 상태가 아니라 어떤 문서 버전과 ontology로 파생 그래프를 만들었는지
 * 운영자가 확인하고 전체 재색인 대상을 판단하기 위한 프로젝션 metadata다.
 */
@Entity
@Table(name = "knowledge_graph_projections")
class KnowledgeGraphProjectionEntity(
    @field:Id
    @field:Column(name = "document_id", nullable = false, updatable = false)
    var documentId: UUID,
    @field:Column(name = "document_version", nullable = false)
    var documentVersion: Long,
    @field:Column(name = "ontology_version", nullable = false, length = 120)
    var ontologyVersion: String,
    @field:Column(name = "entity_count", nullable = false)
    var entityCount: Int,
    @field:Column(name = "relation_count", nullable = false)
    var relationCount: Int,
    @field:Column(name = "projected_at", nullable = false)
    var projectedAt: Instant,
)
