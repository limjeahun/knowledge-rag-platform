package dev.study.airag.application.dto.result

/**
 * 질문과 관련성이 있다고 판단된 문서 근거다.
 *
 * chunkId와 문서 버전으로 출처를 추적하며 검색 엔진이 점수를 제공하지 않으면 score는 `null`이다.
 */
data class KnowledgeSearchHit(
    val chunkId: String,
    val documentId: String,
    val documentVersion: Long,
    val chunkIndex: Int,
    val title: String,
    val content: String,
    val score: Double?,
    val metadata: Map<String, String>,
)
