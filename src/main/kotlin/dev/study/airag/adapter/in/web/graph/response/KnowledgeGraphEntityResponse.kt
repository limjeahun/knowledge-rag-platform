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
