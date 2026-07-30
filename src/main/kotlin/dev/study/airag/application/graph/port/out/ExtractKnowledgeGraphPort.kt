package dev.study.airag.application.graph.port.out

import dev.study.airag.application.graph.port.out.dto.ExtractedKnowledgeGraph
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphExtractionRequest

/** 문서 청크에서 온톨로지에 맞는 개체와 관계 후보를 추출하는 외부 AI 능력이다. */
fun interface ExtractKnowledgeGraphPort {
    /**
     * 허용 ontology 문법과 원문 청크만 사용하여 개체·관계 후보를 생성한다.
     *
     * 반환값은 아직 신뢰된 사실이 아니며 Application validator가 type, endpoint, quote와
     * confidence를 별도로 검증해야 한다.
     *
     * @param request 문서 버전, 허용 ontology 타입과 한 batch의 원문 청크
     * @return 외부 모델이 제안한 검증 전 후보
     */
    fun extract(request: KnowledgeGraphExtractionRequest): ExtractedKnowledgeGraph
}
