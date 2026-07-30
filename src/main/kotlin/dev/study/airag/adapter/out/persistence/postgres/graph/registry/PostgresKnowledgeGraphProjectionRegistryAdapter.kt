package dev.study.airag.adapter.out.persistence.postgres.graph.registry

import dev.study.airag.application.graph.port.out.KnowledgeGraphProjectionRegistryPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjectionReceipt
import dev.study.airag.domain.vo.DocumentId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.UUID

/**
 * OWL 버전과 문서별 활성 Fuseki RDF 프로젝션을 PostgreSQL에 기록한다.
 *
 * 같은 문서의 기존 ACTIVE 행을 먼저 RETIRED로 바꾸고 flush한 뒤 새 ACTIVE 행을 저장하여
 * 부분 unique index를 지킨다. 안정적인 run ID를 사용하므로 같은 문서 버전·ontology의
 * 재처리는 기존 행을 활성화하는 멱등 동작이 된다. RDF 본문은 저장하지 않는다.
 */
@Component
class PostgresKnowledgeGraphProjectionRegistryAdapter(
    private val ontologyRepository: KnowledgeOntologyVersionRepository,
    private val projectionRepository: KnowledgeGraphProjectionRunRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : KnowledgeGraphProjectionRegistryPort {
    /** Fuseki 저장이 성공한 receipt를 현재 문서의 활성 projection 이력으로 확정한다. */
    @Transactional
    override fun activate(receipt: KnowledgeGraphProjectionReceipt) {
        registerOntology(receipt)
        retireActive(receipt.documentId, receipt.projectedAt)
        projectionRepository.flush()
        val id =
            stableUuid(
                "${receipt.documentId}|${receipt.documentVersion}|${receipt.ontologyVersion}|$FUSEKI",
            )
        val projection =
            projectionRepository.findById(id).orElse(null)
                ?: KnowledgeGraphProjectionRunEntity(
                    id = id,
                    documentId = receipt.documentId.value,
                    documentVersion = receipt.documentVersion,
                    ontologyVersionIri = receipt.ontologyVersion,
                    backend = FUSEKI,
                    graphNamesJson = "[]",
                    status = ACTIVE,
                    projectedAt = receipt.projectedAt,
                    activatedAt = receipt.projectedAt,
                    retiredAt = null,
                )
        projection.graphNamesJson = objectMapper.writeValueAsString(receipt.graphNames)
        projection.status = ACTIVE
        projection.projectedAt = receipt.projectedAt
        projection.activatedAt = receipt.projectedAt
        projection.retiredAt = null
        projectionRepository.save(projection)
    }

    /** 삭제된 문서의 현재 ACTIVE projection을 이력 보존 상태인 RETIRED로 전환한다. */
    @Transactional
    override fun retire(documentId: DocumentId) {
        retireActive(documentId, clock.instant())
    }

    private fun registerOntology(receipt: KnowledgeGraphProjectionReceipt) {
        val ontology =
            ontologyRepository.findById(receipt.ontologyVersion).orElse(null)
                ?: KnowledgeOntologyVersionEntity(
                    versionIri = receipt.ontologyVersion,
                    ontologyIri = receipt.ontologyIri,
                    checksum = receipt.ontologyChecksum,
                    ontologyFormat = OWL,
                    status = ACTIVE,
                    registeredAt = receipt.projectedAt,
                )
        ontology.checksum = receipt.ontologyChecksum
        ontology.ontologyFormat = OWL
        ontology.status = ACTIVE
        ontologyRepository.save(ontology)
    }

    private fun retireActive(
        documentId: DocumentId,
        retiredAt: java.time.Instant,
    ) {
        projectionRepository.findAllByDocumentIdAndStatus(documentId.value, ACTIVE).forEach { current ->
            current.status = RETIRED
            current.retiredAt = retiredAt
            projectionRepository.save(current)
        }
    }

    private fun stableUuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))

    private companion object {
        const val ACTIVE = "ACTIVE"
        const val RETIRED = "RETIRED"
        const val FUSEKI = "FUSEKI"
        const val OWL = "OWL"
    }
}
