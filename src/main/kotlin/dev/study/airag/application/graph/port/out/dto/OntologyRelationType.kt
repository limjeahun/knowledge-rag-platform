package dev.study.airag.application.graph.port.out.dto

/**
 * 두 개체 사이에 허용할 방향성 있는 관계의 계약이다.
 *
 * sourceTypes와 targetTypes는 LLM이 문법적으로 그럴듯하지만 업무상 성립하지 않는 간선을
 * 만드는 것을 차단한다. 예를 들어 STORES_IN 관계의 대상은 DATA_STORE로 제한할 수 있다.
 */
data class OntologyRelationType(
    val code: String,
    val description: String,
    val sourceTypes: Set<String>,
    val targetTypes: Set<String>,
)
