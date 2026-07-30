package dev.study.airag.application.graph.port.out

import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology

/** 현재 배포에서 사용할 버전형 온톨로지를 제공한다. */
fun interface KnowledgeOntologyPort {
    /**
     * 외부 ontology 형식을 노출하지 않고 추출 가능한 code 기반 의미 계약을 제공한다.
     *
     * @return version, 개체 타입과 각 관계의 허용 source/target 집합
     */
    fun load(): KnowledgeOntology
}
