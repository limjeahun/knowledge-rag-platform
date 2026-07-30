package dev.study.airag.application.graph.dto.result

/**
 * 중심 개체와 제한된 hop 안에서 발견한 개체·관계를 묶은 Application 조회 결과다.
 *
 * [center]는 항상 [entities]에 포함되며 Adapter와 Use Case가 적용한 depth/limit 밖의
 * 그래프는 노출하지 않는다.
 */
data class KnowledgeGraphNeighborhoodResult(
    val center: KnowledgeGraphEntityResult,
    val entities: List<KnowledgeGraphEntityResult>,
    val relations: List<KnowledgeGraphRelationResult>,
)
