package dev.study.airag.application.graph.port.out.dto

/** 여러 문서에서 같은 개체를 합칠 때 사용하는 의미 기반 자연 키다. */
data class KnowledgeGraphEntityKey(
    val type: String,
    val normalizedName: String,
)
