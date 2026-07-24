# Kotlin + Spring AI 로컬 RAG/MCP

JDK 21 LTS, Kotlin, Spring AI 2.0.0으로 만드는 실행형 로컬 지식 검색 백엔드입니다. PostgreSQL은 원문과 업무 상태를 보관하고, Milvus가 유일한 Vector DB로 동작합니다. Kafka, Transactional Outbox, Redis 락을 이용해 문서 색인을 비동기로 처리합니다.

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
| AI protocol | MCP Streamable HTTP `/mcp` |

상세 설계는 `docs/architecture/rag-target-architecture.md`에서 확인할 수 있습니다.

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

Ollama Desktop이 자동으로 서버를 시작하지 않은 경우 별도 터미널에서 `ollama serve`를 실행합니다. Docker Compose는 PostgreSQL, Milvus, Kafka, Redis만 시작하며 데이터 볼륨은 서비스별로 분리됩니다.

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

## 관측성과 테스트

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-WebRequest http://localhost:8080/actuator/prometheus
.\gradlew.bat clean check
```

테스트는 Domain 상태 전이, Application orchestration, Kafka lock 경계, Hexagonal 의존성 규칙을 외부 인프라 없이 검증합니다. 실제 인프라 smoke test는 로컬 Ollama와 Docker Compose 인프라를 시작한 뒤 `requests.http` 순서로 수행합니다.

## 데이터 주의사항

기존 pgvector 볼륨은 자동 삭제하지 않았습니다. 새 구성은 `postgres-data`와 `milvus-data`를 사용하며 pgvector 의존성과 설정은 제거되었습니다. `docker compose down -v`는 PostgreSQL 원문, Milvus index, Kafka, Redis 데이터를 모두 삭제하므로 명시적으로 초기화할 때만 사용하세요. 로컬 Ollama 모델은 이 명령의 영향을 받지 않습니다.
