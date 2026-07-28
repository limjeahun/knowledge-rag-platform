package dev.study.airag.application.knowledge.service

import dev.study.airag.application.knowledge.dto.command.IndexKnowledgeDocumentCommand
import dev.study.airag.application.knowledge.exception.DocumentIndexingAlreadyInProgressException
import dev.study.airag.application.knowledge.port.`in`.IndexKnowledgeDocumentUseCase
import dev.study.airag.application.knowledge.port.out.DocumentIndexingLockPort
import org.springframework.stereotype.Service

/**
 * 같은 색인 이벤트의 동시 실행을 제한한 상태에서 문서 색인 업무를 시작한다.
 *
 * 트랜잭션 업무가 커밋된 뒤 Redis lease를 해제하기 위해 잠금 조정과 업무 트랜잭션을 분리한다.
 */
@Service
class IndexKnowledgeDocumentService(
    private val documentIndexingLockPort: DocumentIndexingLockPort,
    private val documentIndexingWorkflow: DocumentIndexingWorkflow,
) : IndexKnowledgeDocumentUseCase {
    override fun index(command: IndexKnowledgeDocumentCommand) {
        val lease =
            documentIndexingLockPort.tryAcquire(command.eventId)
                ?: throw DocumentIndexingAlreadyInProgressException(command.eventId)
        try {
            documentIndexingWorkflow.index(command)
        } finally {
            documentIndexingLockPort.release(lease)
        }
    }
}
