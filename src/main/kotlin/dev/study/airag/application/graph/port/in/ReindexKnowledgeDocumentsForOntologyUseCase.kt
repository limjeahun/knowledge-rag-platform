package dev.study.airag.application.graph.port.`in`

import dev.study.airag.application.graph.dto.command.ReindexKnowledgeDocumentsForOntologyCommand
import dev.study.airag.application.graph.dto.result.OntologyReindexRequestResult

/**
 * 이전 온톨로지로 활성화된 문서를 현재 색인 파이프라인에 다시 접수하는 내부 운영 능력이다.
 *
 * 이 Port는 HTTP 관리 API를 의미하지 않는다. 스케줄러·관리 CLI처럼 접근 통제가 가능한
 * Inbound Adapter가 필요할 때 같은 계약을 사용할 수 있다.
 */
fun interface ReindexKnowledgeDocumentsForOntologyUseCase {
    /**
     * 현재 배포된 OWL version과 활성 그래프 이력을 비교해 제한된 문서의 재색인을 요청한다.
     *
     * @return 실제 접수된 문서와 동시 상태 변경으로 건너뛴 문서
     */
    fun requestReindexing(command: ReindexKnowledgeDocumentsForOntologyCommand): OntologyReindexRequestResult
}
