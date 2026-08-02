package dev.study.airag.adapter.out.persistence.postgres.graph.registry

import dev.study.airag.application.graph.port.out.KnowledgeGraphProjectionRegistryPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjectionReceipt
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphReprojectionCriteria
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
    /**
     * Fuseki 저장이 성공한 receipt를 현재 문서의 유일한 활성 projection 이력으로 확정한다.
     *
     * ontology version을 먼저 upsert하고 기존 ACTIVE projection을 RETIRED로 바꾼 뒤 flush한다.
     * 이는 문서당 ACTIVE 한 건을 강제하는 부분 unique index와 새 행의 활성화가 충돌하지 않게
     * 한다. run ID는 문서·버전·ontology·backend로 결정되므로 동일 요청 재처리는 기존 행을
     * 다시 활성화하는 멱등 동작이다.
     *
     * @param receipt 이미 commit된 Fuseki graph와 ontology 식별 정보
     */
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

    /**
     * 삭제된 문서의 현재 ACTIVE projection을 현재 시각 기준 RETIRED로 전환한다.
     *
     * RDF 삭제는 [KnowledgeGraphIndexPort] 구현의 책임이며 이 메서드는 감사 이력만 변경한다.
     * 활성 행이 없으면 성공적인 no-op이다.
     *
     * @param documentId 폐기할 원본 문서의 Domain 식별자
     */
    @Transactional
    override fun retire(documentId: DocumentId) {
        retireActive(documentId, clock.instant())
    }

    /**
     * 현재 OWL version과 다른 ACTIVE 이력의 문서를 오래된 활성화 순서로 반환한다.
     *
     * 동일 문서의 ACTIVE 이력은 DB 부분 unique index가 한 건으로 제한한다. Adapter는 Port가
     * 요청한 상한까지만 Domain ID로 변환하며 RDF 본문이나 JPA entity를 외부로 노출하지 않는다.
     *
     * @param criteria 현재 ontology version과 후보 상한
     * @return 현재 ontology로 다시 투영해야 할 문서 식별자
     */
    @Transactional(readOnly = true)
    override fun findReprojectionCandidates(criteria: KnowledgeGraphReprojectionCriteria): List<DocumentId> =
        projectionRepository
            .findTop1000ByStatusAndOntologyVersionIriNotOrderByActivatedAtAsc(
                ACTIVE,
                criteria.currentOntologyVersion,
            ).asSequence()
            .map { DocumentId.from(it.documentId.toString()) }
            .take(criteria.limit)
            .toList()

    /**
     * receipt의 OWL 배포 정보를 version IRI 기준으로 등록하거나 최신 checksum으로 갱신한다.
     *
     * RDF 본문은 저장하지 않고 ontology IRI, version IRI, 전체 checksum과 format만 보존한다.
     * 같은 version IRI에서 checksum이 달라진 경우에도 현재 구현은 값을 갱신하므로 운영
     * 배포 규칙에서 의미 변경 시 version IRI를 올려야 한다.
     *
     * @param receipt Fuseki projection이 실제 사용한 ontology 정보
     */
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

    /**
     * 한 문서에 현재 ACTIVE로 기록된 모든 projection을 지정 시각에 RETIRED로 바꾼다.
     *
     * 정상 스키마에서는 최대 한 건이지만 손상이나 이전 데이터가 있어도 모두 정리한다.
     * 호출자가 새 ACTIVE를 저장하려면 unique index 충돌 방지를 위해 이후 flush해야 한다.
     *
     * @param documentId 대상 문서 식별자
     * @param retiredAt 활성 상태를 종료한 업무 시각
     */
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

    /** projection 자연 키를 재시도와 무관하게 동일한 PostgreSQL UUID로 변환한다. */
    private fun stableUuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))

    private companion object {
        const val ACTIVE = "ACTIVE"
        const val RETIRED = "RETIRED"
        const val FUSEKI = "FUSEKI"
        const val OWL = "OWL"
    }
}
