package dev.study.airag.application.service

import dev.study.airag.application.dto.command.RegisterKnowledgeDocumentCommand
import dev.study.airag.application.dto.command.RetryKnowledgeDocumentIndexingCommand
import dev.study.airag.application.dto.result.RegisteredKnowledgeDocumentResult
import dev.study.airag.application.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.application.outbox.OutboxEnvelope
import dev.study.airag.application.port.`in`.RegisterKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.RetryKnowledgeDocumentIndexingUseCase
import dev.study.airag.application.port.out.CorrelationIdGenerator
import dev.study.airag.application.port.out.EventIdGenerator
import dev.study.airag.application.port.out.KnowledgeDocumentPort
import dev.study.airag.application.port.out.OutboxEventPort
import dev.study.airag.domain.model.KnowledgeDocument
import dev.study.airag.domain.vo.DocumentId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 문서 변경과 색인 요청 기록이 서로 유실되지 않도록 등록과 재시도를 처리한다. */
@Service
class RegisterKnowledgeDocumentService(
    private val documentPort: KnowledgeDocumentPort,
    private val outboxEventPort: OutboxEventPort,
    private val eventIdGenerator: EventIdGenerator,
    private val correlationIdGenerator: CorrelationIdGenerator,
    private val clock: Clock,
) : RegisterKnowledgeDocumentUseCase,
    RetryKnowledgeDocumentIndexingUseCase {
    /** 새 원본 문서와 최초 색인 요청을 같은 트랜잭션에 저장한다. */
    @Transactional
    override fun register(command: RegisterKnowledgeDocumentCommand): RegisteredKnowledgeDocumentResult {
        val document =
            KnowledgeDocument.register(
                id              = DocumentId.newId(),
                title           = command.title,
                originalContent = command.content,
                metadata        = command.metadata,
                now             = clock.instant(),
            )
        documentPort.save(document)

        val correlationId = correlationIdGenerator.nextId()
        val envelopes =
            document.pullDomainEvents().map { event ->
                OutboxEnvelope(eventIdGenerator.nextId(), correlationId, event)
            }
        outboxEventPort.appendAll(envelopes)

        return RegisteredKnowledgeDocumentResult.from(document)
    }

    /**
     * 실패 상태를 대기로 되돌리고 새 이벤트 식별자로 색인 요청을 저장한다.
     *
     * 등록되지 않았거나 실패 상태가 아닌 문서는 재시도하지 않는다.
     */
    @Transactional
    override fun retry(command: RetryKnowledgeDocumentIndexingCommand): RegisteredKnowledgeDocumentResult {
        val id = DocumentId.from(command.documentId)
        val document = documentPort.findById(id) ?: throw KnowledgeDocumentNotFoundException(command.documentId)
        val now = clock.instant()
        document.requestRetry(now)
        documentPort.save(document)

        val correlationId = correlationIdGenerator.nextId()
        val envelopes =
            document.pullDomainEvents().map { event ->
                OutboxEnvelope(eventIdGenerator.nextId(), correlationId, event)
            }
        outboxEventPort.appendAll(envelopes)

        return RegisteredKnowledgeDocumentResult.from(document)
    }
}
