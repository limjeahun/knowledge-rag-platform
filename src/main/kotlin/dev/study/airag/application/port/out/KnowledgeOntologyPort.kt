package dev.study.airag.application.port.out

import dev.study.airag.application.port.out.dto.KnowledgeOntology

/** 현재 배포에서 사용할 버전형 온톨로지를 제공한다. */
fun interface KnowledgeOntologyPort {
    fun load(): KnowledgeOntology
}
