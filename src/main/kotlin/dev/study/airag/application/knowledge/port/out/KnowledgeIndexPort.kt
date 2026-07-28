package dev.study.airag.application.knowledge.port.out

import dev.study.airag.application.knowledge.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit
import dev.study.airag.application.knowledge.port.out.dto.KnowledgeIndexReplacement
import dev.study.airag.domain.vo.DocumentId

/** 원본 문서에서 다시 만들 수 있는 검색 근거를 저장하고 조회한다. */
interface KnowledgeIndexPort {
    /** 문서의 이전 검색 근거를 모두 제거하고 현재 버전의 근거로 교체한다. */
    fun replace(replacement: KnowledgeIndexReplacement)

    /** 최소 유사도를 충족하는 근거를 관련도 순으로 최대 `topK`개 반환한다. */
    fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit>

    /** 특정 문서의 모든 버전에 해당하는 검색 근거를 제거한다. */
    fun remove(documentId: DocumentId)
}
