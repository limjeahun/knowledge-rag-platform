package dev.study.airag.application.graph.port.`in`

import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.model.KnowledgeDocument

/** 현재 문서 버전과 청크에서 재생성 가능한 지식 그래프 프로젝션을 만든다. */
fun interface ProjectKnowledgeGraphUseCase {
    /**
     * 색인 중인 문서와 같은 버전의 청크를 ontology 기반 그래프로 교체한다.
     *
     * 기능 비활성화 시 아무 외부 변경도 하지 않는다. 활성화 시 실패를 숨기지 않아 호출자가
     * 문서를 INDEXED로 확정하지 못하게 해야 한다.
     *
     * @param document 현재 색인 상태와 버전을 소유한 Aggregate
     * @param chunks 해당 문서 버전의 순서 있는 원문 조각
     */
    fun project(
        document: KnowledgeDocument,
        chunks: List<KnowledgeChunk>,
    )
}
