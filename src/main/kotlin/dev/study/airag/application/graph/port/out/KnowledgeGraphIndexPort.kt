package dev.study.airag.application.graph.port.out

import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.domain.vo.DocumentId

/** 원문에서 재생성 가능한 지식 그래프 프로젝션의 교체와 제거 경계다. */
interface KnowledgeGraphIndexPort {
    /**
     * 같은 문서의 이전 버전 evidence를 제거하고 현재 프로젝션으로 교체한다.
     *
     * 다른 문서도 근거로 사용하는 전역 개체와 관계는 유지하고, 어떤 문서에서도 더 이상
     * 증명되지 않는 고아 관계와 개체만 정리해야 한다.
     */
    fun replace(projection: KnowledgeGraphProjection)

    /** 삭제된 문서가 제공하던 evidence와 그 결과 생긴 고아 그래프 요소를 제거한다. */
    fun remove(documentId: DocumentId)
}
