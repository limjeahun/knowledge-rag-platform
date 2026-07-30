package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.ProjectedGraphEntity
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphRelation

/** 모든 batch를 검증·병합한 뒤 RDF 저장 경계로 전달할 수 있는 최종 그래프다. */
data class ValidatedKnowledgeGraph(
    val entities: List<ProjectedGraphEntity>,
    val relations: List<ProjectedGraphRelation>,
)
