package dev.study.airag.application.port.out.dto

/**
 * 그래프 저장소가 반환하는 기술 중립 조회 모델이다.
 *
 * JPA Entity를 애플리케이션이나 REST로 노출하지 않도록 별도 모델을 사용한다.
 */
data class StoredKnowledgeGraphEntity(
    val entityId: String,
    val ontologyVersion: String,
    val type: String,
    val name: String,
    val aliases: Set<String>,
    val evidence: List<StoredKnowledgeGraphEvidence>,
)

data class StoredKnowledgeGraphRelation(
    val relationId: String,
    val ontologyVersion: String,
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<StoredKnowledgeGraphEvidence>,
)

data class StoredKnowledgeGraphEvidence(
    val documentId: String,
    val documentVersion: Long,
    val chunkId: String,
    val quote: String,
    val confidence: Double,
)

data class StoredKnowledgeGraphNeighborhood(
    val center: StoredKnowledgeGraphEntity,
    val entities: List<StoredKnowledgeGraphEntity>,
    val relations: List<StoredKnowledgeGraphRelation>,
)
