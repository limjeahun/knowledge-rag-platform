package dev.study.airag.adapter.out.persistence.postgres.graph.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

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
