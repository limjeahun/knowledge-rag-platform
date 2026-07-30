# Software Architecture OWL Ontology Guide

## 목표와 범위

이 프로젝트의 온톨로지는 단순한 JSON 타입 목록이 아니라 OWL 2 DL 의미론을 가진 버전형 도메인 모델이다. 원본 지식 문서는 계속 PostgreSQL이 소유하고, OWL 온톨로지는 문서에서 어떤 개체와 관계를 표현할 수 있는지 정의한다. 실제 사실은 RDF 지식 그래프로 Fuseki/TDB2에 저장한다.

구현 자산은 다음처럼 나뉜다.

| 자산 | 역할 |
| --- | --- |
| `ontology/core/knowledge-core-v1.ttl` | 문서, 청크, 개념, 사람, 조직, 증거와 provenance의 공통 어휘 |
| `ontology/domain/software-architecture-v1.ttl` | 기술, 컴포넌트, 프로세스, 저장소, 포트와 소프트웨어 아키텍처 관계 |
| `ontology/shapes/software-architecture-shapes-v1.ttl` | OWL의 열린 세계 의미론을 보완하는 입력 데이터 품질 제약 |
| HermiT | OWL 2 DL 일관성 검사와 클래스·속성 entailment 계산 |
| Apache Jena SHACL | RDF 투영 및 statement evidence 검증 |
| Fuseki/TDB2 | asserted, inferred, provenance named graph와 SPARQL endpoint |
| PostgreSQL projection registry | 활성 문서 투영, ontology version IRI, checksum, Fuseki graph IRI 기록 |

OWL의 TBox와 RDF의 ABox를 구분한다.

```text
TBox: Component rdfs:subClassOf KnowledgeEntity
      writesTo rdfs:subPropertyOf uses

ABox: Indexer rdf:type Component
      Indexer writesTo Milvus

Entailment: Indexer rdf:type KnowledgeEntity
            Indexer uses Milvus
```

## 네임스페이스와 버전

- Core ontology IRI: `urn:airag:ontology:knowledge-core`
- Core version IRI: `urn:airag:ontology:knowledge-core:1.0.0`
- Domain ontology IRI: `urn:airag:ontology:software-architecture`
- Domain version IRI: `urn:airag:ontology:software-architecture:1.0.0`
- Runtime entity IRI: `urn:airag:entity:{stableUuid}`
- Runtime assertion IRI: `urn:airag:assertion:{stableUuid}`

파일명은 편의를 위한 배포 경로이고 정체성은 ontology IRI와 version IRI가 결정한다. 의미가 호환되지 않는 변경은 기존 version IRI를 수정하지 않고 새 version IRI로 발행한다. 애플리케이션의 `core:code`는 LLM JSON 계약과 REST 필터에서 사용하는 안정적인 코드다. RDF/OWL IRI를 Kotlin enum으로 복제하지 않는다.

## 문서화 annotation 계약

연구와 Protégé 탐색을 위해 ontology annotation도 배포 계약으로 관리한다.

- 모든 ontology, named class, object property, datatype property, annotation property는 영어·한국어
  `rdfs:label`을 가진다.
- 같은 용어는 영어·한국어 `rdfs:comment`와 하나 이상의 `skos:definition`을 가진다.
- 실제 사용 사례가 용어의 포함·제외 경계를 명확히 하면 `skos:example`을 추가한다.
- `rdfs:comment`는 용어의 역할을 설명하고, `skos:definition`은 다른 용어와 구분되는 필요충분한
  의미 경계를 설명한다.
- 모든 SHACL Node Shape는 한·영 `sh:name`과 `sh:description`을 가진다.
- 모든 SHACL Property Shape는 한·영 `sh:name`, `sh:description`, 수정 가능한 `sh:message`를 가진다.

`OntologyDocumentationTests`가 이 최소 계약을 자동 검사한다. 다국어 annotation은 OWL의 논리
entailment를 바꾸지 않지만 파일 checksum을 바꾸므로 운영 배포에서는 재색인 영향을 검토한다.

## 모델 핵심

주요 상위 클래스는 다음과 같다.

- `core:KnowledgeEntity`: 그래프에서 식별할 수 있는 모든 지식 개체
- `core:Agent`: `Person`, `Organization`
- `core:KnowledgeArtifact`: `Document`, `DocumentChunk`, `Answer`
- `soft:Technology`: framework, library, protocol, programming language, AI model, broker, model runtime
- `soft:Component`: inbound adapter, application service, outbound adapter, port
- `soft:Process`: document registration, document indexing, knowledge retrieval
- `soft:DataStore`: relational database, vector index, processing lock store

관계는 일반 관계와 구체 관계를 함께 정의한다.

- `partOf` / `hasPart`: 역관계
- `uses`: 일반 사용 관계
- `readsFrom`, `writesTo`, `publishesTo`, `consumesFrom`: `uses`의 하위 속성
- `storesIn`: 저장 관계
- `indexedIn`: `storesIn`의 하위 속성이며 `containsIndexedChunk`의 역속성
- `implementsPort`, `invokesPort`: Hexagonal Architecture 경계

`writesTo` 사실은 HermiT에 의해 `uses` 사실도 함의한다. API는 두 문장을 합치지 않고 `ASSERTED`와 `INFERRED`를 구분해 반환한다.

## 입력 검증과 provenance

LLM 출력은 곧바로 지식 그래프가 되지 않는다.

1. Application validator가 ontology code, source/target 허용 타입, 청크 존재 여부, 원문 quote를 검증한다.
2. 검증된 projection을 RDF로 변환한다.
3. 각 asserted statement를 RDF reification으로 표현하고 `documentId`, `documentVersion`, `chunkId`, `quote`, `confidence`를 연결한다.
4. SHACL로 필수 provenance와 datatype/cardinality를 검사한다.
5. HermiT로 ontology와 ABox의 일관성을 검사하고 entailment를 계산한다.
6. asserted, inferred, provenance를 서로 다른 named graph에 기록한다.

추론 사실은 원문 직접 진술이 아니므로 quote를 위조하지 않는다. 대신 ontology version과 reasoning activity를 가진 `InferredStatementProvenance`를 기록한다.

## Named graph와 활성 투영

문서 버전마다 다음 named graph를 만든다.

```text
urn:airag:graph:document:{documentId}:v{version}:{ontologyChecksum}:asserted
urn:airag:graph:document:{documentId}:v{version}:{ontologyChecksum}:inferred
urn:airag:graph:document:{documentId}:v{version}:{ontologyChecksum}:provenance
```

`urn:airag:graph:projection-catalog`가 문서별 활성 graph IRI를 가리킨다. 읽기 경로는 catalog에서 물질화한 세 활성 union graph를 사용한다.

- `urn:airag:graph:active-asserted`
- `urn:airag:graph:active-inferred`
- `urn:airag:graph:active-provenance`

재색인은 새 문서 projection을 기록하고 catalog pointer와 활성 union을 한 Fuseki transaction에서 교체한다. PostgreSQL의 `knowledge_graph_projection_runs`는 이 활성 상태와 ontology checksum을 별도로 기록한다. Fuseki를 잃어도 PostgreSQL 원문에서 재색인할 수 있다.

## Competency questions

온톨로지 변경은 다음 질문에 답할 수 있어야 한다.

1. 특정 컴포넌트가 어떤 저장소를 읽거나 쓰는가?
2. 특정 outbound adapter는 어떤 port를 구현하는가?
3. 문서 색인 프로세스는 어떤 broker와 vector index를 사용하는가?
4. 직접 진술된 관계와 ontology가 추론한 관계를 구분할 수 있는가?
5. asserted 관계를 원문 document/chunk/quote로 역추적할 수 있는가?
6. 한 개체의 1~2 hop 이웃을 제한된 크기로 조회할 수 있는가?
7. ontology version이 바뀌었을 때 어떤 문서 projection을 재생성해야 하는가?

실행 가능한 예시는 `docs/ontology/sparql`에 있다.

## 변경 및 배포 규칙

1. Protégé에서 OWL 2 DL profile을 유지하며 새 클래스·속성·제약을 편집한다.
2. 모든 named ontology 용어에 문서화 annotation 계약을 적용한다.
3. 모든 LLM 추출 대상 class/object property에 고유한 `core:code`와 `core:extractable true`를 둔다.
4. 추출 대상 object property는 정확히 하나의 domain class expression과 하나의 range class expression을 가져야 한다.
5. SHACL 제약, 한·영 실패 메시지와 competency query를 함께 갱신한다.
6. `OntologyDocumentationTests`, `OwlOntologySemanticTests`, `KnowledgeGraphShaclValidationTests`,
   Fuseki adapter 통합 테스트를 실행한다.
7. 의미가 달라지면 version IRI를 올리고 기존 version 파일을 보존한다.
8. 운영 전 원문 기준 전체 재색인 계획과 rollback할 이전 version IRI를 확정한다.

OWL은 열린 세계 가정과 Unique Name Assumption 부재를 따른다. 따라서 “값이 없다”는 사실만으로 거짓이라고 판단하지 않으며, 데이터 수집 계약의 필수 필드·건수·datatype은 SHACL로 강제한다.

## 로컬 실행

```powershell
$env:KNOWLEDGE_GRAPH_ENABLED="true"
docker compose up -d --wait
.\gradlew.bat bootRun
```

Fuseki endpoint:

- Dataset: `http://localhost:3030/knowledge`
- Query: `http://localhost:3030/knowledge/query`
- Update: `http://localhost:3030/knowledge/update`
- Graph Store Protocol: `http://localhost:3030/knowledge/data`

설정과 ontology checksum은 애플리케이션이 관리한다. 운영 데이터에 Fuseki UI로 직접 update를 실행하면 projection registry와 불일치할 수 있으므로 탐색용 SELECT/CONSTRUCT만 허용한다.
