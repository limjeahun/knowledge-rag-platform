package dev.study.airag.application.port.out.dto

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

/** 여러 문서에서 같은 개체를 합칠 때 사용하는 의미 기반 자연 키다. */
data class KnowledgeGraphEntityKey(
    val type: String,
    val normalizedName: String,
)

data class ProjectedGraphEntity(
    val key: KnowledgeGraphEntityKey,
    val name: String,
    val aliases: Set<String>,
    val evidence: List<KnowledgeGraphEvidence>,
)

data class ProjectedGraphRelation(
    val type: String,
    val source: KnowledgeGraphEntityKey,
    val target: KnowledgeGraphEntityKey,
    val evidence: List<KnowledgeGraphEvidence>,
)

/**
 * 지식 그래프의 각 사실을 원본 문서로 역추적하기 위한 provenance다.
 *
 * 점수는 모델 확신도의 기록일 뿐 사실 여부를 대신하지 않는다. quote가 실제 chunk에
 * 포함되는지 확인하는 provenance 검증과 ontology 규칙 검증을 모두 통과해야 저장된다.
 */
data class KnowledgeGraphEvidence(
    val chunkId: String,
    val quote: String,
    val confidence: Double,
)
