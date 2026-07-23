package dev.study.airag.adapter.out.messaging.kafka

import dev.study.airag.application.model.publication.DocumentIndexingPublication
import dev.study.airag.application.port.out.PublishDocumentIndexingPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** 같은 문서의 색인 요청 순서가 유지되도록 문서 식별자를 기준으로 메시지를 발행한다. */
@Component
class KafkaDocumentIndexingPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    @Value("\${app.knowledge.indexing-topic}") private val topic: String,
) : PublishDocumentIndexingPort {
    /**
     * 브로커의 발행 확인을 최대 10초 기다린다.
     *
     * 실패 응답이나 timeout은 Outbox 발행 실패로 기록할 수 있도록 호출자에게 전파한다.
     */
    override fun publish(publication: DocumentIndexingPublication) {
        val message =
            DocumentIndexingPublishMessage(
                eventId = publication.eventId,
                correlationId = publication.correlationId,
                occurredAt = publication.occurredAt,
                documentId = publication.documentId.toString(),
                documentVersion = publication.documentVersion,
            )
        kafkaTemplate.send(topic, publication.documentId.toString(), message).get(10, TimeUnit.SECONDS)
    }
}
