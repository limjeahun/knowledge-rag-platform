package dev.study.airag.application.service

import dev.study.airag.application.dto.command.DeleteKnowledgeDocumentCommand
import dev.study.airag.application.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.application.outbox.OutboxEnvelope
import dev.study.airag.application.port.`in`.DeleteKnowledgeDocumentUseCase
import dev.study.airag.application.port.out.CorrelationIdGenerator
import dev.study.airag.application.port.out.EventIdGenerator
import dev.study.airag.application.port.out.KnowledgeDocumentPort
import dev.study.airag.application.port.out.OutboxEventPort
import dev.study.airag.domain.vo.DocumentId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 삭제된 문서가 더 이상 검색 결과와 답변 근거에 포함되지 않도록 처리한다. */
@Service
class DeleteKnowledgeDocumentService(
    private val documentPort: KnowledgeDocumentPort,
    private val outboxEventPort: OutboxEventPort,
    private val eventIdGenerator: EventIdGenerator,
    private val correlationIdGenerator: CorrelationIdGenerator,
    private val clock: Clock,
) : DeleteKnowledgeDocumentUseCase {
    /**
     * 문서의 삭제 상태를 먼저 저장한 뒤 해당 문서의 모든 검색 근거를 제거한다.
     *
     * 등록되지 않은 문서는 삭제할 수 없다.
     */
    @Transactional
    override fun delete(command: DeleteKnowledgeDocumentCommand) {
        val id = DocumentId.from(command.documentId)
        val document = documentPort.findById(id) ?: throw KnowledgeDocumentNotFoundException(command.documentId)
        val now = clock.instant()
        if (!document.markDeleted(now)) return
        documentPort.save(document)

        val correlationId = correlationIdGenerator.nextId()
        val envelopes =
            document.pullDomainEvents().map { event ->
                OutboxEnvelope(eventIdGenerator.nextId(), correlationId, event)
            }
        outboxEventPort.appendAll(envelopes)
    }
}
