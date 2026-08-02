package dev.study.airag.application.graph.dto.command

/**
 * 현재 배포 온톨로지와 다른 활성 프로젝션을 재색인하도록 요청하는 운영 명령이다.
 *
 * 한 트랜잭션과 Outbox에 너무 많은 문서를 넣지 않도록 [limit]으로 한 번의 처리량을 제한한다.
 */
data class ReindexKnowledgeDocumentsForOntologyCommand(
    val limit: Int = 100,
) {
    init {
        require(limit in 1..1_000) { "온톨로지 재색인 요청 개수는 1 이상 1,000 이하여야 합니다." }
    }
}
