package dev.study.airag.config

import dev.study.airag.application.knowledge.exception.DocumentIndexingAlreadyInProgressException
import dev.study.airag.application.knowledge.exception.DocumentIndexingFailedException
import dev.study.airag.application.knowledge.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.application.knowledge.port.out.CorrelationIdGenerator
import dev.study.airag.application.knowledge.port.out.EventIdGenerator
import dev.study.airag.domain.exception.InvalidDocumentStateTransitionException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.BackOff
import org.springframework.util.backoff.FixedBackOff
import java.time.Clock
import java.util.UUID

@Configuration
class ApplicationConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun eventIdGenerator(): EventIdGenerator = EventIdGenerator { UUID.randomUUID() }

    @Bean
    fun correlationIdGenerator(): CorrelationIdGenerator = CorrelationIdGenerator { UUID.randomUUID() }

    /**
     * 일시적인 색인 실패는 1초 간격으로 두 번 다시 처리한다.
     *
     * 재시도를 모두 사용한 메시지는 topic, partition, offset과 최종 원인을 기록하여 운영자가 추적할 수 있게 한다.
     */
    @Bean
    fun kafkaErrorHandler(): DefaultErrorHandler = knowledgeIndexingErrorHandler(FixedBackOff(1_000L, 2L))

    /** 색인 실패의 복구 가능성에 따라 Kafka 재전달 여부를 명시적으로 분류한다. */
    internal fun knowledgeIndexingErrorHandler(backOff: BackOff): DefaultErrorHandler {
        val logger = LoggerFactory.getLogger("KafkaIndexingErrorHandler")
        val errorHandler =
            DefaultErrorHandler(
                { record: ConsumerRecord<*, *>, exception: Exception ->
                    logger.error(
                        "문서 색인이 최종 실패했습니다. topic={}, partition={}, offset={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        exception,
                    )
                },
                backOff,
            )
        errorHandler.addRetryableExceptions(
            DocumentIndexingAlreadyInProgressException::class.java,
            DocumentIndexingFailedException::class.java,
        )
        errorHandler.addNotRetryableExceptions(
            KnowledgeDocumentNotFoundException::class.java,
            InvalidDocumentStateTransitionException::class.java,
            IllegalArgumentException::class.java,
        )
        return errorHandler
    }
}
