package dev.study.airag.adapter.`in`.web.graph.response

import io.swagger.v3.oas.annotations.media.Schema

/** ontology 분류와 원문 provenance를 함께 반환하는 지식 그래프 개체다. */
data class KnowledgeGraphEntityResponse(
    @field:Schema(description = "그래프 개체의 안정적인 UUID")
    val entityId: String,
    @field:Schema(description = "이 개체를 해석하는 ontology 버전")
    val ontologyVersion: String,
    @field:Schema(description = "ontology에 선언된 개체 타입", example = "TECHNOLOGY")
    val type: String,
    @field:Schema(description = "대표 이름")
    val name: String,
    @field:Schema(description = "문서에서 확인된 다른 명칭")
    val aliases: Set<String>,
    @field:Schema(description = "이 개체를 증명하는 원문 목록")
    val evidence: List<KnowledgeGraphEvidenceResponse>,
)

/** 방향성 관계와 양 끝점, 원문 provenance를 함께 반환한다. */
data class KnowledgeGraphRelationResponse(
    val relationId: String,
    val ontologyVersion: String,
    @field:Schema(example = "USES")
    val type: String,
    val sourceEntityId: String,
    val sourceName: String,
    val targetEntityId: String,
    val targetName: String,
    val evidence: List<KnowledgeGraphEvidenceResponse>,
)

/** 추출된 지식이 실제 어느 문서 버전과 청크 구절에서 나왔는지 보여주는 출처다. */
data class KnowledgeGraphEvidenceResponse(
    val documentId: String,
    val documentVersion: Long,
    val chunkId: String,
    val quote: String,
    val confidence: Double,
)

data class KnowledgeGraphNeighborhoodResponse(
    val center: KnowledgeGraphEntityResponse,
    val entities: List<KnowledgeGraphEntityResponse>,
    val relations: List<KnowledgeGraphRelationResponse>,
)
