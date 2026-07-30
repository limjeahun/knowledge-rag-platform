package dev.study.airag.adapter.out.persistence.postgres.graph.registry

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 문서 버전별 Fuseki projection 활성 이력을 보존하는 JPA entity다.
 *
 * graph 본문을 PostgreSQL에 중복 저장하지 않고 ontology version과 named graph IRI만 기록한다.
 * 동일 문서에는 ACTIVE 행이 하나만 존재하며 이전 행은 RETIRED로 남겨 생성 이력을 추적한다.
 */
@Entity
@Table(name = "knowledge_graph_projection_runs")
class KnowledgeGraphProjectionRunEntity(
    @Id
    val id: UUID,
    @Column(name = "document_id", nullable = false)
    val documentId: UUID,
    @Column(name = "document_version", nullable = false)
    val documentVersion: Long,
    @Column(name = "ontology_version_iri", length = 500, nullable = false)
    val ontologyVersionIri: String,
    @Column(name = "backend", length = 30, nullable = false)
    val backend: String,
    @Column(name = "graph_names_json", nullable = false)
    var graphNamesJson: String,
    @Column(name = "status", length = 30, nullable = false)
    var status: String,
    @Column(name = "projected_at", nullable = false)
    var projectedAt: Instant,
    @Column(name = "activated_at")
    var activatedAt: Instant?,
    @Column(name = "retired_at")
    var retiredAt: Instant?,
)
