package dev.study.airag.application.graph.port.`in`

import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.model.KnowledgeDocument

/** 현재 문서 버전과 청크에서 재생성 가능한 지식 그래프 프로젝션을 만든다. */
fun interface ProjectKnowledgeGraphUseCase {
    fun project(
        document: KnowledgeDocument,
        chunks: List<KnowledgeChunk>,
    )
}
