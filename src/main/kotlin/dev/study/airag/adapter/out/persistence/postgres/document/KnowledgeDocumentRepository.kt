package dev.study.airag.adapter.out.persistence.postgres.document

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KnowledgeDocumentRepository : JpaRepository<KnowledgeDocumentEntity, UUID>
