package dev.study.airag.application.graph.port.out.dto

import dev.study.airag.domain.vo.DocumentId
import java.time.Instant

/**
 * 그래프 저장 성공 후 Projection Registry가 영속화할 배포·재생성 정보다.
 *
 * ontology IRI, version, checksum과 실제 named graph 목록을 함께 기록해 어떤 의미 계약으로
 * 문서 프로젝션이 생성되었는지 감사하고 재색인 대상을 판단할 수 있게 한다.
 */
data class KnowledgeGraphProjectionReceipt(
    val documentId: DocumentId,
    val documentVersion: Long,
    val ontologyIri: String,
    val ontologyVersion: String,
    val ontologyChecksum: String,
    val graphNames: List<String>,
    val projectedAt: Instant,
)
