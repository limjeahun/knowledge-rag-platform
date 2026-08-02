package dev.study.airag.adapter.`in`.scheduling

import dev.study.airag.application.graph.dto.command.ReindexKnowledgeDocumentsForOntologyCommand
import dev.study.airag.application.graph.port.`in`.ReindexKnowledgeDocumentsForOntologyUseCase
import dev.study.airag.config.graph.KnowledgeGraphProperties
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 배포 OWL version과 다른 활성 프로젝션을 제한된 배치로 재색인 Use Case에 전달한다.
 *
 * 외부에 관리 HTTP endpoint를 노출하지 않는다. 기본값은 비활성이며 운영자가 graph 기능과
 * ontology reindex 설정을 모두 켠 경우에만 원본 문서 상태와 Outbox를 변경한다.
 */
@Component
class KnowledgeGraphOntologyReindexScheduler(
    private val reindexUseCase: ReindexKnowledgeDocumentsForOntologyUseCase,
    private val properties: KnowledgeGraphProperties,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 현재 ontology와 다른 활성 프로젝션을 설정된 상한만큼 비동기 색인 흐름에 재접수한다.
     *
     * 후보가 없으면 성공적인 no-op이다. 후보 조회 이후 문서 상태가 달라진 건은 Use Case가
     * 건너뛰며, 요청 수와 건너뜀 수를 별도 counter로 기록해 반복적인 상태 경합을 관찰한다.
     */
    @Scheduled(cron = "\${app.knowledge.graph.ontology-reindex-cron:0 0 * * * *}")
    fun requestStaleProjectionReindexing() {
        if (!properties.enabled || !properties.ontologyReindexEnabled) return

        val result =
            reindexUseCase.requestReindexing(
                ReindexKnowledgeDocumentsForOntologyCommand(properties.ontologyReindexBatchSize),
            )
        meterRegistry
            .counter("knowledge.graph.ontology.reindex.requested")
            .increment(result.requestedDocumentIds.size.toDouble())
        meterRegistry
            .counter("knowledge.graph.ontology.reindex.skipped")
            .increment(result.skippedDocumentIds.size.toDouble())
        if (result.requestedDocumentIds.isNotEmpty() || result.skippedDocumentIds.isNotEmpty()) {
            logger.info(
                "온톨로지 변경 재색인을 접수했습니다. ontologyVersion={}, requested={}, skipped={}",
                result.ontologyVersion,
                result.requestedDocumentIds.size,
                result.skippedDocumentIds.size,
            )
        }
    }
}
