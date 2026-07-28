package dev.study.airag.application.graph.port.out.dto

import dev.study.airag.domain.vo.DocumentId
import java.time.Instant

/**
 * 온톨로지와 원문 근거 검증을 통과하여 저장 가능한 파생 지식 그래프다.
 *
 * PostgreSQL 원문이 기준 데이터이고 이 프로젝션은 언제든 다시 만들 수 있다. 따라서 문서
 * 버전 교체 시 기존 evidence를 제거한 뒤 이 값으로 원자적으로 교체해야 한다.
 */
data class KnowledgeGraphProjection(
    val documentId: DocumentId,
    val documentVersion: Long,
    val ontologyVersion: String,
    val entities: List<ProjectedGraphEntity>,
    val relations: List<ProjectedGraphRelation>,
    val projectedAt: Instant,
)
