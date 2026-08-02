package dev.study.airag.application.graph.dto.result

/**
 * 온톨로지 변경에 따른 문서 재색인 접수 결과다.
 *
 * 후보 조회 이후 문서가 삭제되거나 다른 상태로 바뀔 수 있으므로 실제로 Outbox 요청을 만든
 * 문서와 건너뛴 문서를 분리한다.
 */
data class OntologyReindexRequestResult(
    val ontologyVersion: String,
    val requestedDocumentIds: List<String>,
    val skippedDocumentIds: List<String>,
)
