package dev.study.airag.application.graph.port.out.dto

/**
 * ontology와 원문 검증을 통과해 저장 가능한 하나의 의미 개체다.
 *
 * [key]는 여러 문서의 동일 개체를 합치는 자연 키이고 [evidence]는 이 문서 버전이 개체
 * 타입을 직접 뒷받침하는 출처다.
 */
data class ProjectedGraphEntity(
    val key: KnowledgeGraphEntityKey,
    val name: String,
    val aliases: Set<String>,
    val evidence: List<KnowledgeGraphEvidence>,
)
