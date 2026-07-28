package dev.study.airag.application.knowledge.port.out.dto

import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.vo.DocumentId

/** 한 문서 버전의 기존 검색 근거를 교체할 때 필요한 입력을 하나로 묶는다. */
data class KnowledgeIndexReplacement(
    val documentId: DocumentId,
    val documentVersion: Long,
    val chunks: List<KnowledgeChunk>,
)
