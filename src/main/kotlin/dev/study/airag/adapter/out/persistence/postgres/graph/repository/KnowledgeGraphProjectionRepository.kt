package dev.study.airag.adapter.out.persistence.postgres.graph.repository

import dev.study.airag.adapter.out.persistence.postgres.graph.entity.KnowledgeGraphProjectionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KnowledgeGraphProjectionRepository : JpaRepository<KnowledgeGraphProjectionEntity, UUID>
