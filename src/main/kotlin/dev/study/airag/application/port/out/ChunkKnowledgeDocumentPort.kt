package dev.study.airag.application.port.out

import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.model.KnowledgeDocument

fun interface ChunkKnowledgeDocumentPort {
    /** 원본 내용과 metadata를 순서가 유지되는 답변 근거로 나눈다. */
    fun chunk(document: KnowledgeDocument): List<KnowledgeChunk>
}
