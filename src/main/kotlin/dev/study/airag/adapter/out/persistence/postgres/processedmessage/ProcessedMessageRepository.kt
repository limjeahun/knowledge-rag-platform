package dev.study.airag.adapter.out.persistence.postgres.processedmessage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.NativeQuery
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ProcessedMessageRepository : JpaRepository<ProcessedMessageEntity, ProcessedMessageId> {
    @NativeQuery(
        """
        WITH acquired_lock AS MATERIALIZED (
            SELECT pg_advisory_xact_lock(
                hashtext(:consumerName),
                hashtext(CAST(:eventId AS text))
            )
        )
        SELECT 1 FROM acquired_lock
        """,
    )
    fun acquireProcessingLock(
        @Param("consumerName") consumerName: String,
        @Param("eventId") eventId: UUID,
    ): Int
}
