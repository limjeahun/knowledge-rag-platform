package dev.study.airag.application.port.out

import dev.study.airag.application.port.out.dto.ExtractedKnowledgeGraph
import dev.study.airag.application.port.out.dto.KnowledgeGraphExtractionRequest

/** 문서 청크에서 온톨로지에 맞는 개체와 관계 후보를 추출하는 외부 AI 능력이다. */
fun interface ExtractKnowledgeGraphPort {
    fun extract(request: KnowledgeGraphExtractionRequest): ExtractedKnowledgeGraph
}
