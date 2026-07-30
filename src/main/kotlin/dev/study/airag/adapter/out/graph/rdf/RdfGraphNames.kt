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
    /**
     * 문서 교체 또는 삭제 시 함께 다루어야 하는 문서 전용 graph만 반환한다.
     *
     * ontology와 shapes graph는 같은 checksum을 사용하는 다른 문서와 공유할 수 있으므로
     * 포함하지 않는다.
     *
     * @return asserted, inferred, provenance 순서의 graph IRI 목록
     */
    fun documentGraphs(): List<String> = listOf(asserted, inferred, provenance)

    companion object {
        /**
         * 문서 버전과 ontology checksum으로 결정적인 graph IRI 묶음을 생성한다.
         *
         * checksum은 graph IRI 가독성을 위해 앞 16자리만 사용하지만 registry receipt에는 전체
         * SHA-256 값을 보존한다. 문서 ID는 Domain VO가 검증한 UUID이므로 사용자 문자열을
         * SPARQL graph IRI에 직접 삽입하지 않는다.
         *
         * @param projection 문서 ID, 버전과 ontology version을 가진 저장 대상
         * @param ontologyChecksum TBox와 SHACL shapes 전체의 SHA-256
         * @return catalog pointer와 문서·schema named graph IRI
         */
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
