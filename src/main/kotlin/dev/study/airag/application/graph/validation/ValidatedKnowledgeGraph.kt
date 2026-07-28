package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.port.out.dto.ProjectedGraphEntity
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphRelation

data class ValidatedKnowledgeGraph(
    val entities: List<ProjectedGraphEntity>,
    val relations: List<ProjectedGraphRelation>,
)
