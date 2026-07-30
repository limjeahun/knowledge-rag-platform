package dev.study.airag.adapter.out.persistence.postgres.graph.registry

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** 문서별 활성·은퇴 Fuseki projection 이력을 조회하고 저장하는 Spring Data 경계다. */
interface KnowledgeGraphProjectionRunRepository : JpaRepository<KnowledgeGraphProjectionRunEntity, UUID> {
    /**
     * 한 문서에서 지정 상태인 projection 이력을 모두 조회한다.
     *
     * ACTIVE unique index가 정상인 경우 최대 한 건이지만 Adapter가 방어적으로 전체를 폐기할 수
     * 있도록 List 계약을 유지한다.
     */
    fun findAllByDocumentIdAndStatus(
        documentId: UUID,
        status: String,
    ): List<KnowledgeGraphProjectionRunEntity>
}
