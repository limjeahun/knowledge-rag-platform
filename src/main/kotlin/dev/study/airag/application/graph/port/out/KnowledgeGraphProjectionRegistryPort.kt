package dev.study.airag.application.graph.port.out

import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjectionReceipt
import dev.study.airag.domain.vo.DocumentId

/**
 * 재생성 가능한 그래프 프로젝션의 활성 버전과 생성 조건을 PostgreSQL에 기록한다.
 *
 * 이 registry는 원문이나 RDF graph의 기준 저장소가 아니라 ontology 변경·재색인·감사를 위한
 * deployment history다.
 */
interface KnowledgeGraphProjectionRegistryPort {
    /** 성공한 projection을 문서의 유일한 활성 버전으로 기록한다. */
    fun activate(receipt: KnowledgeGraphProjectionReceipt)

    /** 문서 삭제 시 활성 projection을 이력 보존 상태로 전환한다. */
    fun retire(documentId: DocumentId)
}
