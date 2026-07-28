package dev.study.airag.application.knowledge.port.`in`

import dev.study.airag.application.knowledge.dto.command.IndexKnowledgeDocumentCommand

fun interface IndexKnowledgeDocumentUseCase {
    /**
     * 요청된 문서 버전으로 검색 근거를 만들고 문서 상태를 확정한다.
     *
     * 이미 완료된 이벤트와 현재 원본보다 오래된 요청은 다시 색인하지 않는다.
     * 색인을 완료하지 못하면 실패 상태를 기록하고 예외를 전파한다.
     */
    fun index(command: IndexKnowledgeDocumentCommand)
}
