package dev.study.airag.adapter.out.vector.milvus

import dev.study.airag.application.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.dto.result.KnowledgeSearchHit
import dev.study.airag.application.port.out.KnowledgeIndexPort
import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.vo.DocumentId
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * 문서 근거를 유사도 검색할 수 있도록 저장한다.
 *
 * 원본 문서와 색인 상태는 소유하지 않으며, 저장된 근거는 원본으로부터 다시 만들 수 있다.
 */
@Component
class SpringAiMilvusKnowledgeIndexAdapter(
    private val vectorStore: VectorStore,
) : KnowledgeIndexPort {
    /** 이전 버전의 근거가 검색되지 않도록 모든 기존 근거를 제거한 후 새 버전을 저장한다. */
    override fun replace(
        documentId: DocumentId,
        documentVersion: Long,
        chunks: List<KnowledgeChunk>,
    ) {
        remove(documentId)
        vectorStore.add(chunks.map { it.toVectorDocument() })
    }

    /** 질의문과 검색 범위를 적용해 조건을 충족한 문서 근거를 반환한다. */
    override fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit> {
        val request =
            SearchRequest
                .builder()
                .query(query.query)
                .topK(query.topK)
                .similarityThreshold(query.similarityThreshold)
                .build()
        return vectorStore.similaritySearch(request).map { it.toSearchHit() }
    }

    /** 문서 식별자가 같은 모든 버전의 검색 근거를 제거한다. */
    override fun remove(documentId: DocumentId) {
        val expression = FilterExpressionBuilder().eq("document_id", documentId.toString()).build()
        vectorStore.delete(expression)
    }

    private fun KnowledgeChunk.toVectorDocument(): Document {
        val vectorMetadata = metadata.mapValues { it.value as Any }.toMutableMap()
        vectorMetadata["document_id"] = documentId.toString()
        vectorMetadata["document_version"] = documentVersion
        vectorMetadata["chunk_index"] = chunkIndex
        vectorMetadata["chunk_id"] = chunkId
        vectorMetadata["title"] = title
        return Document
            .builder()
            .id(UUID.nameUUIDFromBytes(chunkId.toByteArray(StandardCharsets.UTF_8)).toString())
            .text(content)
            .metadata(vectorMetadata)
            .build()
    }

    private fun Document.toSearchHit(): KnowledgeSearchHit {
        fun stringMetadata(key: String) = metadata[key]?.toString().orEmpty()

        fun longMetadata(key: String) =
            (metadata[key] as? Number)?.toLong()
                ?: stringMetadata(key).toLongOrNull()
                ?: 0L

        fun intMetadata(key: String) =
            (metadata[key] as? Number)?.toInt()
                ?: stringMetadata(key).toIntOrNull()
                ?: 0

        val reserved = setOf("document_id", "document_version", "chunk_index", "chunk_id", "title")
        return KnowledgeSearchHit(
            chunkId = stringMetadata("chunk_id").ifBlank { id },
            documentId = stringMetadata("document_id"),
            documentVersion = longMetadata("document_version"),
            chunkIndex = intMetadata("chunk_index"),
            title = stringMetadata("title"),
            content = text.orEmpty(),
            score = score,
            metadata = metadata.filterKeys { it !in reserved }.mapValues { it.value.toString() },
        )
    }
}
