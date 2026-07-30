package dev.study.airag.adapter.out.ontology.owl

import dev.study.airag.application.graph.port.out.KnowledgeOntologyPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology
import org.springframework.stereotype.Component

/**
 * OWL 배포 모델을 기술 중립적인 Application ontology 계약으로 제공하는 Outbound Adapter다.
 *
 * Port 경계와 결과 생명주기만 책임지고 실제 OWL 탐색·추론·변환은
 * [OwlKnowledgeOntologyTranslator]에 위임한다. 변환 결과는 최초 요청에서 완전히 생성한 뒤
 * 애플리케이션 수명 동안 재사용하여 모든 문서가 같은 ontology version과 타입 문법을 사용한다.
 */
@Component
internal class OwlKnowledgeOntologyAdapter(
    private val translator: OwlKnowledgeOntologyTranslator,
) : KnowledgeOntologyPort {
    private val ontology: KnowledgeOntology by lazy(translator::translate)

    /**
     * LLM 추출과 Application 검증에서 사용할 현재 배포의 ontology 계약을 반환한다.
     *
     * 첫 호출에서만 OWL 변환을 실행하고 이후에는 동일한 불변 객체를 반환한다. 온톨로지 파일이
     * 바뀌었다면 애플리케이션을 재시작해야 새 version과 checksum을 기준으로 다시 변환된다.
     *
     * @return version과 정렬된 개체·관계 타입을 가진 기술 중립 ontology
     */
    override fun load(): KnowledgeOntology = ontology
}
