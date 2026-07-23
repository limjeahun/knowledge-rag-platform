package dev.study.airag.adapter.out.persistence.postgres.outbox

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEventEntity, UUID> {
    fun findByPublishedAtIsNullOrderByOccurredAtAsc(pageable: Pageable): List<OutboxEventEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
        """
        UPDATE OutboxEventEntity event
           SET event.publishedAt = :deliveredAt,
               event.publishAttempts = event.publishAttempts + 1,
               event.lastError = null
         WHERE event.eventId = :eventId
        """,
    )
    fun markDelivered(
        @Param("eventId") eventId: UUID,
        @Param("deliveredAt") deliveredAt: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
        """
        UPDATE OutboxEventEntity event
           SET event.publishAttempts = event.publishAttempts + 1,
               event.lastError = :error
         WHERE event.eventId = :eventId
        """,
    )
    fun markFailed(
        @Param("eventId") eventId: UUID,
        @Param("error") error: String,
    ): Int
}
