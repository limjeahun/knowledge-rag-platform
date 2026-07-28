package dev.study.airag.application.exception

/** LLM 후보가 ontology 또는 원문 provenance 계약을 위반했음을 나타낸다. */
class InvalidKnowledgeGraphExtractionException(
    message: String,
) : RuntimeException(message)

class KnowledgeGraphEntityNotFoundException(
    entityId: String,
) : RuntimeException("지식 그래프 개체를 찾을 수 없습니다: $entityId")
