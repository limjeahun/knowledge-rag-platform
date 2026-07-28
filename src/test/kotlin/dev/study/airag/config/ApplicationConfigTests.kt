package dev.study.airag.config

import dev.study.airag.application.knowledge.exception.DocumentIndexingAlreadyInProgressException
import dev.study.airag.application.knowledge.exception.DocumentIndexingFailedException
import dev.study.airag.application.knowledge.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.domain.exception.InvalidDocumentStateTransitionException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.util.backoff.FixedBackOff
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationConfigTests {
    private val consumer = mock(Consumer::class.java)
    private val container = mock(MessageListenerContainer::class.java)

    @Test
    fun `Kafka error handler retries recoverable indexing failures`() {
        val errorHandler = ApplicationConfig().knowledgeIndexingErrorHandler(FixedBackOff(0L, 2L))

        assertFalse(
            errorHandler.handleOne(
                DocumentIndexingAlreadyInProgressException(UUID.randomUUID()),
                record(0L),
                consumer,
                container,
            ),
        )
        assertFalse(
            errorHandler.handleOne(
                DocumentIndexingFailedException("Embedding failed", IllegalStateException("Ollama unavailable")),
                record(1L),
                consumer,
                container,
            ),
        )
    }

    @Test
    fun `Kafka error handler skips retries for permanent indexing failures`() {
        val errorHandler = ApplicationConfig().knowledgeIndexingErrorHandler(FixedBackOff(0L, 2L))

        assertTrue(
            errorHandler.handleOne(
                KnowledgeDocumentNotFoundException("missing-document"),
                record(0L),
                consumer,
                container,
            ),
        )
        assertTrue(
            errorHandler.handleOne(
                InvalidDocumentStateTransitionException("Invalid state"),
                record(1L),
                consumer,
                container,
            ),
        )
        assertTrue(
            errorHandler.handleOne(
                IllegalArgumentException("Invalid event payload"),
                record(2L),
                consumer,
                container,
            ),
        )
    }

    private fun record(offset: Long) = ConsumerRecord("knowledge.document-indexing.v1", 0, offset, "key", "value")
}
