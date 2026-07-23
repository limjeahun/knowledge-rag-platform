package dev.study.airag.application.port.`in`

import dev.study.airag.application.dto.command.RetryKnowledgeDocumentIndexingCommand
import dev.study.airag.application.dto.result.RegisteredKnowledgeDocumentResult

fun interface RetryKnowledgeDocumentIndexingUseCase {
    /**
     * 마지막 색인에 실패한 문서를 다시 대기열에 등록한다.
     *
     * 등록되지 않았거나 실패 상태가 아닌 문서는 재시도할 수 없다.
     */
    fun retry(command: RetryKnowledgeDocumentIndexingCommand): RegisteredKnowledgeDocumentResult
}
