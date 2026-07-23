package dev.study.airag.adapter.`in`.messaging

import dev.study.airag.application.dto.command.IndexKnowledgeDocumentCommand
import dev.study.airag.application.port.`in`.IndexKnowledgeDocumentUseCase
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/** Kafka 색인 요청을 Application Command로 번역한다. */
@Component
class KafkaDocumentIndexingConsumer(
    private val indexKnowledgeDocumentUseCase: IndexKnowledgeDocumentUseCase,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [$$"${app.knowledge.indexing-topic}"])
    fun consume(message: DocumentIndexingMessage) {
        indexKnowledgeDocumentUseCase.index(
            IndexKnowledgeDocumentCommand(
                eventId = message.eventId,
                documentId = message.documentId,
                documentVersion = message.documentVersion,
            ),
        )
        meterRegistry.counter("knowledge.indexing.consumed").increment()
        logger.info(
            "Document indexing event completed: eventId={}, documentId={}",
            message.eventId,
            message.documentId,
        )
    }
}
