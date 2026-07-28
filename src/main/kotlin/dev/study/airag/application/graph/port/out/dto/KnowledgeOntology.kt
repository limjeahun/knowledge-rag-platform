package dev.study.airag.application.graph.port.out.dto

/**
 * 그래프 추출기가 사용할 수 있는 지식 표현의 문법이다.
 *
 * 온톨로지는 실제 문서에서 발견된 사실을 저장하지 않는다. 대신 어떤 종류의 개체와 관계를
 * 허용할지 정의하여, 모델이 임의의 타입을 만들어 지식 그래프의 의미를 흐리는 일을 막는다.
 * 코드 enum이 아니라 버전이 있는 외부 정의로 두므로 업무 어휘를 바꿀 때 애플리케이션을
 * 재컴파일하지 않고도 새 버전의 그래프를 재생성할 수 있다.
 */
data class KnowledgeOntology(
    val version: String,
    val entityTypes: List<OntologyEntityType>,
    val relationTypes: List<OntologyRelationType>,
) {
    val entityTypesByCode: Map<String, OntologyEntityType> = entityTypes.associateBy { it.code }
    val relationTypesByCode: Map<String, OntologyRelationType> = relationTypes.associateBy { it.code }
}
