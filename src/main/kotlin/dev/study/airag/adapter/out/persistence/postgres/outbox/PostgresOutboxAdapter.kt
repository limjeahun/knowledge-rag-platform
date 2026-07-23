package dev.study.airag.adapter.out.persistence.postgres.outbox

import dev.study.airag.application.model.outbox.OutboxEnvelope
import dev.study.airag.application.port.out.OutboxEventPort
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** 문서 변경과 함께 후속 처리 요청을 보존하고 전달 성공 여부를 추적한다. */
@Component
class PostgresOutboxAdapter(
    private val repository: OutboxEventRepository,
    private val mapper: OutboxEventMapper,
) : OutboxEventPort {
    /** 문서 저장과 같은 트랜잭션에서 후속 처리 요청을 미완료 상태로 기록한다. */
    override fun append(envelope: OutboxEnvelope) {
        repository.save(mapper.toEntity(envelope))
    }

    /** 성공 기록이 없는 요청을 오래된 순서로 최대 [limit]개 조회한다. */
    override fun findPending(limit: Int): List<OutboxEnvelope> =
        repository
            .findByPublishedAtIsNullOrderByOccurredAtAsc(PageRequest.of(0, limit))
            .map(mapper::toEnvelope)

    /** 전달 완료 시각을 기록하고 이전 실패 원인을 제거한다. */
    override fun markDelivered(
        eventId: UUID,
        deliveredAt: Instant,
    ) {
        repository.markDelivered(eventId, deliveredAt)
    }

    /** 실패 원인을 2,000자로 제한해 보존하고 발행 시도 횟수를 증가시킨다. */
    override fun markFailed(
        eventId: UUID,
        error: String,
    ) {
        repository.markFailed(eventId, error.take(2_000))
    }
}
