package dev.study.airag.application.graph.service

import dev.study.airag.application.graph.dto.command.ReindexKnowledgeDocumentsForOntologyCommand
import dev.study.airag.application.graph.dto.result.OntologyReindexRequestResult
import dev.study.airag.application.graph.port.`in`.ReindexKnowledgeDocumentsForOntologyUseCase
import dev.study.airag.application.graph.port.out.KnowledgeGraphProjectionRegistryPort
import dev.study.airag.application.graph.port.out.KnowledgeOntologyPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphReprojectionCriteria
import dev.study.airag.application.knowledge.outbox.OutboxEnvelope
import dev.study.airag.application.knowledge.port.out.CorrelationIdGenerator
import dev.study.airag.application.knowledge.port.out.EventIdGenerator
import dev.study.airag.application.knowledge.port.out.KnowledgeDocumentPort
import dev.study.airag.application.knowledge.port.out.OutboxEventPort
import dev.study.airag.domain.model.DocumentIndexingStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * OWL version 변경으로 의미 프로젝션이 오래된 문서를 기존 비동기 색인 흐름에 다시 접수한다.
 *
 * Fuseki를 직접 다시 쓰지 않고 `KnowledgeDocument -> Domain Event -> Outbox -> Kafka -> 기존
 * 색인 Use Case` 경로를 사용한다. 따라서 PostgreSQL 상태, Milvus 교체, SHACL/HermiT 검증,
 * Fuseki 저장과 프로젝션 이력이 최초 색인과 같은 규칙으로 처리된다.
 */
@Service
class ReindexKnowledgeDocumentsForOntologyService(
    private val ontologyPort: KnowledgeOntologyPort,
    private val projectionRegistryPort: KnowledgeGraphProjectionRegistryPort,
    private val documentPort: KnowledgeDocumentPort,
    private val outboxEventPort: OutboxEventPort,
    private val eventIdGenerator: EventIdGenerator,
    private val correlationIdGenerator: CorrelationIdGenerator,
    private val clock: Clock,
) : ReindexKnowledgeDocumentsForOntologyUseCase {
    /**
     * 현재 ontology version과 다른 활성 프로젝션을 조회하고 재색인 이벤트를 원자적으로 기록한다.
     *
     * registry 후보를 얻은 뒤 PostgreSQL 원본 문서를 다시 읽는다. 동시 작업으로 문서가
     * `INDEXED`가 아니게 된 경우 Aggregate 상태를 덮어쓰지 않고 skipped 결과에 넣는다.
     * 실제 접수 문서마다 새 correlation ID를 만들며 모든 Domain Event는 문서 상태 변경과 같은
     * 로컬 트랜잭션의 Outbox에 저장한다.
     *
     * @param command 한 번에 접수할 최대 문서 수
     * @return 현재 ontology version과 실제 접수·건너뜀 문서 ID
     */
    @Transactional
    override fun requestReindexing(
        command: ReindexKnowledgeDocumentsForOntologyCommand,
    ): OntologyReindexRequestResult {
        val ontologyVersion = ontologyPort.load().version
        val candidates =
            projectionRegistryPort
                .findReprojectionCandidates(
                    KnowledgeGraphReprojectionCriteria(ontologyVersion, command.limit),
                ).distinct()
        val requested = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val envelopes = mutableListOf<OutboxEnvelope>()
        val requestedAt = clock.instant()

        candidates.forEach { documentId ->
            val document = documentPort.findById(documentId)
            if (document == null || document.status != DocumentIndexingStatus.INDEXED) {
                skipped += documentId.toString()
                return@forEach
            }

            document.requestReindexing(requestedAt)
            documentPort.save(document)
            val correlationId = correlationIdGenerator.nextId()
            document.pullDomainEvents().forEach { event ->
                envelopes += OutboxEnvelope(eventIdGenerator.nextId(), correlationId, event)
            }
            requested += documentId.toString()
        }

        if (envelopes.isNotEmpty()) {
            outboxEventPort.appendAll(envelopes)
        }
        return OntologyReindexRequestResult(ontologyVersion, requested, skipped)
    }
}
