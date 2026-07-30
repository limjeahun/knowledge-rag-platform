package dev.study.airag.adapter.out.graph.rdf

import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection

/**
 * 문서 버전과 ontology checksum으로부터 생성한 Fuseki named graph IRI 묶음이다.
 *
 * 문서 ID·버전이 같아도 ontology checksum이 바뀌면 다른 IRI가 만들어져 의미 계약이 다른
 * 프로젝션이 섞이지 않는다. catalog의 [projection]은 문서별 활성 graph pointer를 보관한다.
 */
data class RdfGraphNames(
    val projection: String,
    val asserted: String,
    val inferred: String,
    val provenance: String,
    val ontology: String,
    val shapes: String,
) {
    /** 문서 교체 또는 삭제 시 함께 다루어야 하는 문서 전용 graph만 반환한다. */
    fun documentGraphs(): List<String> = listOf(asserted, inferred, provenance)

    companion object {
        /** 사용자 입력을 path로 사용하지 않고 URN 규칙으로 결정적인 graph IRI를 생성한다. */
        fun from(
            projection: KnowledgeGraphProjection,
            ontologyChecksum: String,
        ): RdfGraphNames {
            val documentToken = projection.documentId.toString()
            val ontologyToken = ontologyChecksum.take(16)
            val base = "urn:airag:graph:document:$documentToken:v${projection.documentVersion}:$ontologyToken"
            return RdfGraphNames(
                projection = "${RdfKnowledgeGraphVocabulary.PROJECTION_NAMESPACE}$documentToken",
                asserted = "$base:asserted",
                inferred = "$base:inferred",
                provenance = "$base:provenance",
                ontology = "urn:airag:graph:ontology:$ontologyToken",
                shapes = "urn:airag:graph:shapes:$ontologyToken",
            )
        }
    }
}
