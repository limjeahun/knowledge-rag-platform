package dev.study.airag.application.dto.result

/** REST/MCP 표현 형식을 모르는 지식 그래프 개체 조회 결과다. */
data class KnowledgeGraphEntityResult(
    val entityId: String,
    val ontologyVersion: String,
    val type: String,
    val name: String,
    val aliases: Set<String>,
    val evidence: List<KnowledgeGraphEvidenceResult>,
)

data class KnowledgeGraphRelationResult(
    val relationId: String,
    val ontologyVersion: String,
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<KnowledgeGraphEvidenceResult>,
)

/**
 * 그래프 답변을 원문 청크까지 추적할 수 있게 하는 출처다.
 *
 * documentId와 chunkId가 있으므로 사용자는 관계가 단순 모델 추론인지, 실제 등록 문서에서
 * 추출된 것인지 확인할 수 있다.
 */
data class KnowledgeGraphEvidenceResult(
    val documentId: String,
    val documentVersion: Long,
    val chunkId: String,
    val quote: String,
    val confidence: Double,
)

data class KnowledgeGraphNeighborhoodResult(
    val center: KnowledgeGraphEntityResult,
    val entities: List<KnowledgeGraphEntityResult>,
    val relations: List<KnowledgeGraphRelationResult>,
)
