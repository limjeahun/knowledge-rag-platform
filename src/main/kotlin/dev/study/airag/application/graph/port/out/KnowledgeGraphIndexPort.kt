package dev.study.airag.application.graph.port.out

import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjectionReceipt
import dev.study.airag.domain.vo.DocumentId

/**
 * 원문에서 재생성 가능한 지식 그래프 프로젝션의 교체와 제거 경계다.
 *
 * 구현체는 ontology·SHACL·asserted·inferred·provenance 저장을 하나의 외부 transaction으로
 * 확정하고 성공한 named graph 정보를 receipt로 반환해야 한다.
 */
interface KnowledgeGraphIndexPort {
    /**
     * 같은 문서의 이전 버전 evidence를 제거하고 현재 프로젝션으로 교체한다.
     *
     * document-scoped named graph를 교체하며, 활성 union graph는 다른 문서의 projection을
     * 보존한 상태로 다시 계산해야 한다.
     *
     * @param projection 검증 완료된 문서 버전 전체 그래프
     * @return 저장된 graph 위치와 ontology 식별 정보를 담은 registry receipt
     */
    fun replace(projection: KnowledgeGraphProjection): KnowledgeGraphProjectionReceipt

    /**
     * 삭제된 문서가 제공하던 evidence와 활성 graph pointer를 제거한다.
     *
     * @param documentId 제거할 원본 문서 식별자
     */
    fun remove(documentId: DocumentId)
}
