package dev.study.airag.application.port.out

import dev.study.airag.application.model.outbox.OutboxEnvelope
import java.time.Instant
import java.util.UUID

/** 문서 변경과 함께 색인 요청을 보존하고 메시지 발행 결과를 추적한다. */
interface OutboxEventPort {
    /**
     * 아직 발행되지 않은 색인 요청을 저장한다.
     *
     * 호출자는 문서 변경과 같은 트랜잭션 안에서 저장해야 한다.
     */
    fun append(envelope: OutboxEnvelope)

    /** 같은 Aggregate 변경에서 발생한 이벤트를 하나의 트랜잭션으로 추가한다. */
    fun appendAll(envelopes: List<OutboxEnvelope>) {
        envelopes.forEach(::append)
    }

    /** 발행되지 않은 이벤트를 발생 시각이 빠른 순서로 최대 [limit]개 반환한다. */
    fun findPending(limit: Int): List<OutboxEnvelope>

    /** 성공적으로 전달된 이벤트를 다시 발행 대상에서 제외한다. */
    fun markDelivered(
        eventId: UUID,
        deliveredAt: Instant,
    )

    /**
     * 실패 횟수와 원인을 기록하되 다음 발행 주기에서 다시 시도할 수 있도록 유지한다.
     */
    fun markFailed(
        eventId: UUID,
        error: String,
    )
}
