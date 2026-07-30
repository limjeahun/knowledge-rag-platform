# Protégé에서 `software-architecture-v1.ttl` 작업하기

`software-architecture-v1.ttl`은 Protégé라는 프로그램이 아니라 OWL 2 ontology를 Turtle 문법으로 직렬화한 파일이다. Protégé는 이 파일을 시각적으로 편집하고 reasoner로 검증하는 도구다.

## 열기

1. Protégé Desktop을 설치하고 `File > Open`을 선택한다.
2. `src/main/resources/ontology/domain/software-architecture-v1.ttl`을 연다.
3. import가 보이지 않으면 `Ontology imports`에서 `knowledge-core-v1.ttl`을 같은 workspace의 local copy로 연결한다.
4. `Active Ontology`에서 ontology IRI와 version IRI가 각각 아래 값인지 확인한다.

```text
urn:airag:ontology:software-architecture
urn:airag:ontology:software-architecture:1.0.0
```

## 편집 위치

- `Entities > Classes`: 클래스 계층과 disjointness
- `Entities > Object properties`: domain, range, inverse, subproperty
- `Entities > Data properties`: literal datatype
- `Annotations`: 한·영 `rdfs:label`, 한·영 `rdfs:comment`, `skos:definition`, 선택적
  `skos:example`, `core:code`, `core:extractable`
- `Individuals`: 연구용 예시 ABox를 시험할 때만 사용하며 운영 문서 사실은 Fuseki가 소유한다.

새 추출 타입을 만들 때는 상위 클래스를 먼저 정하고 `core:code`와 `core:extractable true`를 추가한다.
모든 named class와 property는 영어·한국어 label/comment와 영어 definition을 가져야 한다. 예시가
용어 경계를 이해하는 데 도움이 되면 `skos:example`을 추가한다. 새 관계는 domain/range를 반드시
명시한다. `RELATED_TO` 같은 무제한 관계는 구체적인 의미를 잃으므로 사용하지 않는다.

SHACL을 편집할 때는 모든 Node Shape에 한·영 `sh:name`, `sh:description`을 작성하고, 모든
Property Shape에는 한·영 `sh:name`, `sh:description`, `sh:message`를 작성한다. `sh:message`는
제약을 반복하는 코드가 아니라 사용자가 어떤 값을 어떻게 수정해야 하는지 알 수 있는 문장이어야 한다.

## Reasoner 검증

1. `Reasoner > HermiT`를 선택한다.
2. `Reasoner > Start reasoner`를 실행한다.
3. `Inferred class hierarchy`와 `Inferred object property hierarchy`를 확인한다.
4. `owl:Nothing` 아래에 의도하지 않은 named class가 나타나면 배포하지 않는다.
5. 예시 individual을 추가했다면 inferred type과 subproperty/inverse property가 기대대로 생성되는지 확인한다.

애플리케이션 테스트도 같은 HermiT 계열 검사를 수행하므로 Protégé 결과만으로 배포를 완료하지 않는다.

```powershell
.\gradlew.bat test --tests "*OntologyDocumentationTests" --tests "*OwlOntologySemanticTests" `
  --tests "*KnowledgeGraphShaclValidationTests"
```

## 저장과 버전 발행

`File > Save As`에서 RDF/Turtle 형식을 유지한다. 단순 label/comment 오타 수정이 아니라 추론 결과나 데이터 계약이 달라지는 변경이면 기존 version IRI를 덮어쓰지 않는다.

예를 들어 1.1.0 발행 시:

1. 파일을 `software-architecture-v1.1.ttl` 같은 새 배포 자산으로 복사한다.
2. `owl:versionIRI`를 `urn:airag:ontology:software-architecture:1.1.0`으로 바꾼다.
3. application 설정의 ontology location과 테스트 기대값을 갱신한다.
4. SHACL, SPARQL competency question, 전체 재색인 영향도를 함께 검토한다.

Git diff에서는 IRI, domain/range, subclass, inverse, cardinality, disjointness 변경을 우선 리뷰한다. 이 항목들은 화면 label 변경보다 추론 결과에 훨씬 큰 영향을 준다.
