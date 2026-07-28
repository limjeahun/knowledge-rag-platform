package dev.study.airag.application.graph.exception

class KnowledgeGraphEntityNotFoundException(
    entityId: String,
) : RuntimeException("지식 그래프 개체를 찾을 수 없습니다: $entityId")
