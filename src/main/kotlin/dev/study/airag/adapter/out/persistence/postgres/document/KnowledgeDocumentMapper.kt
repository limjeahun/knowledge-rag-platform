package dev.study.airag.adapter.out.persistence.postgres.document

import dev.study.airag.domain.model.DocumentIndexingStatus
import dev.study.airag.domain.model.KnowledgeDocument
import dev.study.airag.domain.vo.DocumentId
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@Component
class KnowledgeDocumentMapper(
    private val objectMapper: ObjectMapper,
) {
    fun toEntity(document: KnowledgeDocument) =
        KnowledgeDocumentEntity(
            id = document.id.value,
            title = document.title,
            originalContent = document.originalContent,
            metadataJson = objectMapper.writeValueAsString(document.metadata),
            documentVersion = document.version,
            indexingStatus = document.status.name,
            failureReason = document.failureReason,
            registeredAt = document.registeredAt,
            indexedAt = document.indexedAt,
            updatedAt = document.updatedAt,
        )

    /** 저장 당시의 버전, 상태 및 시각을 유지하며 문서를 복원한다. */
    fun toDomain(entity: KnowledgeDocumentEntity) =
        KnowledgeDocument.reconstitute(
            id = DocumentId(entity.id),
            title = entity.title,
            originalContent = entity.originalContent,
            metadata = objectMapper.readValue(entity.metadataJson, object : TypeReference<Map<String, String>>() {}),
            version = entity.documentVersion,
            status = DocumentIndexingStatus.valueOf(entity.indexingStatus),
            failureReason = entity.failureReason,
            registeredAt = entity.registeredAt,
            indexedAt = entity.indexedAt,
            updatedAt = entity.updatedAt,
        )
}
