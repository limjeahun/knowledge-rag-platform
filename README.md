# Kotlin + Spring AI 로컬 Hybrid RAG/MCP

JDK 21 LTS, Kotlin, Spring AI 2.0.0으로 만드는 실행형 로컬 지식 검색 백엔드입니다. PostgreSQL은 원문과 업무 상태를 보관하고, Milvus가 유일한 Vector DB로 동작합니다. 선택적으로 OWL 2 DL 온톨로지, HermiT 추론, SHACL 검증, Fuseki/TDB2 지식 그래프를 결합한 Hybrid GraphRAG를 사용할 수 있습니다.

## 기술 구성

| 영역 | 선택 |
| --- | --- |
| Language/JVM | Kotlin 2.3.21 / JDK 21 LTS |
| Application | Spring Boot 4.1.0 / Spring AI 2.0.0 |
| Source of Truth | PostgreSQL 17 |
| Vector DB | Milvus 2.6 |
| Async pipeline | Kafka 4.1 + Transactional Outbox |
| Processing lock | Redis 8.2 |
| Local AI | Ollama `qwen3.6:27b`, `nomic-embed-text` |
| Semantic model | OWL 2 DL/Turtle + HermiT + SHACL |
| RDF graph | Apache Jena Fuseki/TDB2 6.1 |
| AI protocol | MCP Streamable HTTP `/mcp` |

상세 설계는 `docs/architecture/rag-target-architecture.md`, 온톨로지와 Protégé 작업법은 `docs/ontology`에서 확인할 수 있습니다.

## 사전 준비

- JDK 21 LTS: 이 PC에는 `C:\Users\USER\.jdks\ms-21.0.11`이 설치되어 있습니다.
- IntelliJ의 Gradle JVM도 같은 Microsoft OpenJDK 21(`ms-21`)로 지정합니다.
- Docker Desktop에 최소 8GB 메모리를 권장합니다. Milvus standalone은 etcd와 MinIO를 함께 사용합니다.
- 로컬 Ollama가 설치되어 있고 `http://localhost:11434`에서 실행 중이어야 합니다.

프로젝트에는 PC별 JDK 절대 경로를 저장하지 않습니다. 현재 PowerShell 세션에서만 설정하려면 다음과 같이 실행합니다.

```powershell
$env:JAVA_HOME="C:\Users\USER\.jdks\ms-21.0.11"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## 실행

프로젝트 루트 `C:\workspace\knowledge-rag-platform`에서 다음을 실행합니다.

```powershell
ollama list
ollama pull qwen3.6:27b
ollama pull nomic-embed-text

docker compose up -d --wait
.\gradlew.bat bootRun
```

Ollama Desktop이 자동으로 서버를 시작하지 않은 경우 별도 터미널에서 `ollama serve`를 실행합니다. Docker Compose는 PostgreSQL, Milvus, Kafka, Redis, Fuseki를 시작하며 데이터 볼륨은 서비스별로 분리됩니다.

상태 확인과 종료:

```powershell
docker compose ps
docker compose logs --tail 100 milvus kafka
docker compose down
```

`docker compose down`은 컨테이너만 내리고 볼륨은 삭제하지 않습니다. 로컬 Ollama와 로컬에 저장된 모델에도 영향을 주지 않습니다.

## API 흐름

문서를 등록하면 원문과 Outbox가 같은 DB 트랜잭션에 저장되고 즉시 `202 Accepted`와 `PENDING` 상태가 반환됩니다.

```powershell
$registered = Invoke-RestMethod -Method Post http://localhost:8080/api/documents `
  -ContentType "application/json" `
  -Body '{"title":"RAG 메모","content":"RAG는 관련 문서를 검색한 뒤 근거를 LLM에 제공한다.","metadata":{"category":"study"}}'

Invoke-RestMethod "http://localhost:8080/api/documents/$($registered.documentId)"
```

Kafka Consumer가 색인을 완료하면 상태가 `INDEXED`가 됩니다. 이후 검색과 답변을 호출합니다.

```powershell
Invoke-RestMethod "http://localhost:8080/api/search?query=RAG란?&topK=5&similarityThreshold=0.3"

Invoke-RestMethod -Method Post http://localhost:8080/api/chat `
  -ContentType "application/json" `
  -Body '{"question":"RAG가 무엇이야?","topK":5,"similarityThreshold":0.3}'
```

실패 문서는 재시도할 수 있고, 삭제하면 PostgreSQL 상태를 `DELETED`로 바꾸고 Milvus 청크를 제거합니다.

```powershell
Invoke-RestMethod -Method Post "http://localhost:8080/api/documents/$($registered.documentId)/retry"
Invoke-RestMethod -Method Delete "http://localhost:8080/api/documents/$($registered.documentId)"
```

## MCP

- URL: `http://localhost:8080/mcp`
- `knowledge_search(query, topK)`
- `knowledge_ask(question)`

REST와 MCP는 Controller나 Service를 서로 호출하지 않고 동일한 Application Inbound Port를 사용합니다.

## OWL 지식 그래프와 Hybrid GraphRAG

그래프 기능은 기본적으로 비활성화되어 기존 Vector RAG 동작을 보존합니다. OWL 기반 문서 그래프 투영과 hybrid 답변을 활성화하려면 애플리케이션 실행 전에 다음 값을 지정합니다.

```powershell
$env:KNOWLEDGE_GRAPH_ENABLED="true"
.\gradlew.bat bootRun
```

온톨로지는 OWL/Turtle, 그래프 저장소는 Fuseki로 고정됩니다. 활성화된 색인 흐름은 LLM 후보를 애플리케이션 규칙과 SHACL로 검증하고, HermiT가 계산한 사실을 원문 직접 진술과 분리해 저장합니다. `/api/chat` 응답의 `graphFacts`에는 `ASSERTED` 또는 `INFERRED` 구분이 포함됩니다.

Hybrid 조회는 Milvus 상위 결과의 `chunkId`를 Fuseki provenance 시드로 사용합니다. 같은 청크가
직접 뒷받침하는 asserted 사실을 먼저 고르고 질문 어휘와 제한된 이웃의 inferred 사실로
보완합니다. `KNOWLEDGE_GRAPH_MAX_SEED_CHUNKS`, `KNOWLEDGE_GRAPH_MAX_HOPS`,
`KNOWLEDGE_GRAPH_MAX_FACTS`로 context 폭을 제한할 수 있습니다.

OWL version을 올린 뒤 기존 활성 projection을 재생성하려면 아래 설정으로 내부 스케줄러를
명시적으로 활성화합니다. 이 경로는 공개 관리 API가 아니라 Aggregate 상태 전이와
Transactional Outbox를 사용합니다.

```powershell
$env:KNOWLEDGE_GRAPH_ONTOLOGY_REINDEX_ENABLED="true"
$env:KNOWLEDGE_GRAPH_ONTOLOGY_REINDEX_BATCH_SIZE="100"
$env:KNOWLEDGE_GRAPH_ONTOLOGY_REINDEX_CRON="0 0 * * * *"
```

- Ontology: `src/main/resources/ontology/core`, `src/main/resources/ontology/domain`
- SHACL: `src/main/resources/ontology/shapes`
- Fuseki SPARQL dataset: `http://localhost:3030/knowledge`
- Protégé 가이드: `docs/ontology/protege-guide.md`
- 설계·버전·재색인 가이드: `docs/ontology/software-architecture-ontology-guide.md`

## Microsoft GraphRAG와 LightRAG 비교 연구

두 도구는 운영 구현을 대체하지 않고 `research/graphrag`에 격리된 비교군으로 실제 버전을
고정합니다. Microsoft GraphRAG `3.1.0`, LightRAG `1.5.4`, 공통 데이터셋·실행기·평가기와
`uv.lock`이 포함되어 있습니다. Windows 애플리케이션 제어가 Microsoft의 네이티브 Leiden
확장을 차단하는 환경에서는 보안 정책을 우회하지 않고 Docker Compose `research` profile을
사용합니다.

```powershell
docker compose --profile research build microsoft-graphrag lightrag-research
docker compose run --rm microsoft-graphrag rag-compare smoke
docker compose run --rm microsoft-graphrag rag-compare prepare --system microsoft
docker compose run --rm microsoft-graphrag rag-compare index --system microsoft
docker compose --profile research up -d lightrag-research
docker compose run --rm microsoft-graphrag rag-compare index --system lightrag
```

상세 실행·평가 절차는 `research/graphrag/README.md`를 따릅니다.

## 관측성과 테스트

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-WebRequest http://localhost:8080/actuator/prometheus
.\gradlew.bat clean check
```

테스트는 Domain 상태 전이, Application orchestration, Kafka lock 경계, Hexagonal 의존성, OWL 2 DL 일관성, SHACL, HermiT entailment와 embedded Fuseki SPARQL 왕복을 검증합니다. 실제 전체 인프라 smoke test는 로컬 Ollama와 Docker Compose 인프라를 시작한 뒤 `requests.http` 순서로 수행합니다.

## 데이터 주의사항

기존 pgvector 볼륨은 자동 삭제하지 않았습니다. 새 구성은 `postgres-data`와 `milvus-data`를 사용하며 pgvector 의존성과 설정은 제거되었습니다. Flyway V5는 더 이상 사용하지 않는 PostgreSQL 관계형 그래프 프로젝션 테이블만 제거하고 원문 문서와 semantic projection registry는 보존합니다. `docker compose down -v`는 PostgreSQL 원문, Milvus index, Kafka, Redis, Fuseki 데이터를 모두 삭제하므로 명시적으로 초기화할 때만 사용하세요. 로컬 Ollama 모델은 이 명령의 영향을 받지 않습니다.
