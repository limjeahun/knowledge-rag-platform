package dev.study.airag.adapter.out.persistence.postgres.processedmessage

import dev.study.airag.application.knowledge.port.out.DocumentIndexingCompletionPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.util.UUID

/** 색인 Consumer의 기술 식별자를 소유하고 이벤트별 완료 사실을 PostgreSQL에 보존한다. */
@Component
class PostgresDocumentIndexingCompletionAdapter(
    private val repository: ProcessedMessageRepository,
    @Value($$"${app.knowledge.consumer-name}") private val consumerName: String,
) : DocumentIndexingCompletionPort {
    override fun claim(eventId: UUID): Boolean {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "문서 색인 처리 권한 확인은 활성 트랜잭션 안에서 수행해야 합니다."
        }
        repository.acquireProcessingLock(consumerName, eventId)
        // Lock 대기 이후 시작한 별도 조회에서 최신 READ COMMITTED snapshot으로 완료 여부를 확인한다.
        return !repository.existsById(ProcessedMessageId(consumerName, eventId))
    }

    override fun complete(
        eventId: UUID,
        completedAt: Instant,
    ) {
        val id = ProcessedMessageId(consumerName, eventId)
        if (!repository.existsById(id)) {
            repository.save(ProcessedMessageEntity(id, completedAt))
        }
    }
}
