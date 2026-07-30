package dev.study.airag.application.graph.exception

/** 요청한 중심 개체가 현재 활성 지식 그래프에 없음을 나타내는 Application 예외다. */
class KnowledgeGraphEntityNotFoundException(
    entityId: String,
) : RuntimeException("지식 그래프 개체를 찾을 수 없습니다: $entityId")
