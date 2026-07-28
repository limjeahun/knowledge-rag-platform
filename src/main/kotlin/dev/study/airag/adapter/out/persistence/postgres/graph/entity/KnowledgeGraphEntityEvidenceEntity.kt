package dev.study.airag.adapter.out.persistence.postgres.graph.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

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
