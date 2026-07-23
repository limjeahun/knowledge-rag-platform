package dev.study.airag.domain.model

import dev.study.airag.domain.vo.DocumentId

/**
 * 검색 결과와 답변의 근거로 사용하는 지식 문서의 일부다.
 *
 * 청크 식별자는 문서 버전과 순서로 결정되며 metadata는 원본 문서에서 상속한다.
 */
data class KnowledgeChunk(
    val chunkId: String,
    val documentId: DocumentId,
    val documentVersion: Long,
    val chunkIndex: Int,
    val title: String,
    val content: String,
    val metadata: Map<String, String>,
)
