package dev.study.airag.adapter.out.persistence.postgres.document

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** 원본 지식과 현재 색인 상태를 하나의 문서 이력으로 영구 보존한다. */
@Entity
@Table(name = "knowledge_documents")
class KnowledgeDocumentEntity(
    @field:Id
    @field:Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @field:Column(name = "title", nullable = false, length = 300)
    var title: String,
    @field:Column(name = "original_content", nullable = false, columnDefinition = "TEXT")
    var originalContent: String,
    @field:Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
    var metadataJson: String,
    @field:Column(name = "document_version", nullable = false)
    var documentVersion: Long,
    @field:Column(name = "indexing_status", nullable = false, length = 20)
    var indexingStatus: String,
    @field:Column(name = "failure_reason", length = 2_000)
    var failureReason: String?,
    @field:Column(name = "registered_at", nullable = false, updatable = false)
    var registeredAt: Instant,
    @field:Column(name = "indexed_at")
    var indexedAt: Instant?,
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
