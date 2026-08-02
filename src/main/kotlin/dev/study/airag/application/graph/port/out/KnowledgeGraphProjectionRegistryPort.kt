package dev.study.airag.application.graph.port.out

import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjectionReceipt
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphReprojectionCriteria
import dev.study.airag.domain.vo.DocumentId

/**
 * 재생성 가능한 그래프 프로젝션의 활성 버전과 생성 조건을 PostgreSQL에 기록한다.
 *
 * 이 registry는 원문이나 RDF graph의 기준 저장소가 아니라 ontology 변경·재색인·감사를 위한
 * deployment history다.
 */
interface KnowledgeGraphProjectionRegistryPort {
    /**
     * 외부 graph 저장이 성공한 projection을 문서의 유일한 활성 버전으로 기록한다.
     *
     * 같은 문서 버전과 ontology version의 재호출은 중복 이력이 아니라 멱등 활성화여야 한다.
     */
    fun activate(receipt: KnowledgeGraphProjectionReceipt)

    /**
     * 문서 삭제 시 활성 projection을 물리 삭제하지 않고 이력 보존 상태로 전환한다.
     *
     * 활성 이력이 없는 문서는 성공적인 no-op으로 처리할 수 있다.
     */
    fun retire(documentId: DocumentId)

    /**
     * 현재 배포 ontology version과 다른 ACTIVE 프로젝션의 문서 ID를 오래된 순서로 조회한다.
     *
     * 이 메서드는 후보만 반환한다. 문서 상태 전이와 Outbox 기록은 Application Service가
     * PostgreSQL 원본 문서를 다시 확인한 뒤 같은 트랜잭션에서 수행한다.
     */
    fun findReprojectionCandidates(criteria: KnowledgeGraphReprojectionCriteria): List<DocumentId>
}
