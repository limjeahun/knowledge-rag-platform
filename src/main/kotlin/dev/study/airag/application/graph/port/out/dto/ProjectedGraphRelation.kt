package dev.study.airag.application.graph.port.out.dto

/**
 * ontology domain/range와 원문 quote 검증을 통과해 저장 가능한 방향성 관계다.
 *
 * [source]와 [target]은 같은 projection의 검증 완료 개체를 반드시 가리켜야 한다.
 */
data class ProjectedGraphRelation(
    val type: String,
    val source: KnowledgeGraphEntityKey,
    val target: KnowledgeGraphEntityKey,
    val evidence: List<KnowledgeGraphEvidence>,
)
