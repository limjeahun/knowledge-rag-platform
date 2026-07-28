# Spring AI RAG Project Agent Instructions

이 문서는 `C:\workspace\knowledge-rag-platform`에서 Codex/AI agent가 문서, 코드, 설정, 테스트 및 로컬 인프라를 변경할 때 따라야 하는 프로젝트 전용 기준이다.

## 1. 작업 절차

- 파일 수정, Gradle 실행, Docker 실행, 서비스 기동, 데이터 변경 전에 먼저 작업 계획서를 사용자에게 제시한다.
- 작업 계획서에는 목표, 변경 대상, 실행 명령, 검증 방법, 위험 요소 및 변경하지 않을 범위를 포함한다.
- 사용자가 계획을 승인한 후에만 실행한다. 사용자가 요구사항을 추가하면 계획을 갱신하고 다시 확인받는다.
- 진단 요청은 원인과 근거를 먼저 보고하며, 사용자가 수정을 요청하거나 승인하기 전에는 코드를 고치지 않는다.
- 기존 사용자 변경과 관련 없는 파일은 수정하거나 되돌리지 않는다.
- 파괴적 명령, 볼륨 삭제, 데이터 초기화, 기존 파일 덮어쓰기는 반드시 영향 범위와 복구 방법을 계획서에 명시한다.

## 2. 프로젝트 목적

이 프로젝트는 사용자가 등록한 지식 문서를 관리하고, 문서를 비동기로 청킹·임베딩·색인한 뒤, 검색 및 RAG 답변 기능을 REST와 MCP로 제공하는 로컬 AI 지식 검색 백엔드다.

핵심 업무 흐름은 다음과 같다.

```text
지식 문서 등록
  -> PostgreSQL에 원본과 색인 상태 저장
  -> Transactional Outbox 기록
  -> Kafka 색인 메시지 발행
  -> Kafka Consumer가 색인 Use Case 호출
  -> Redis Processing Lock 획득
  -> 문서 청킹
  -> Ollama 임베딩 생성
  -> Milvus Vector Index 저장
  -> PostgreSQL 색인 상태 확정
  -> REST/MCP에서 검색 및 근거 기반 답변 제공
```

## 3. 확정 아키텍처 방향

- 하나의 Spring Boot 애플리케이션과 하나의 Gradle 모듈을 유지한다. 별도 승인이 없으면 마이크로서비스로 분리하지 않는다.
- 로컬 인프라는 하나의 `docker-compose.yml`에서 관리한다.
- PostgreSQL과 Milvus를 함께 사용하지만 Vector DB는 Milvus 하나만 사용한다. pgvector를 Milvus와 동시에 운영하지 않는다.
- Kafka는 문서 색인 작업을 비동기로 분리하는 메시지 브로커다. 이 프로젝트에는 현재 분산 SAGA가 필요하지 않다.
- Redis는 짧은 Processing Lock과 명시적으로 승인된 캐시에만 사용한다.
- Ollama는 임베딩과 채팅 응답 생성을 담당한다.
- REST, MCP, Kafka Consumer는 모두 Inbound Adapter이며 동일한 Application Inbound Port를 호출한다.

## 4. 기술 스택

| 영역 | 기준 |
| --- | --- |
| Language | Kotlin |
| JVM | JDK 21 LTS |
| Framework | Spring Boot 4.1.x |
| AI Framework | Spring AI 2.0.x |
| RDB | PostgreSQL |
| Vector DB | Milvus |
| Message Broker | Kafka |
| Cache/Lock | Redis |
| Local Model Runtime | Ollama |
| AI Protocol | MCP Streamable HTTP |
| Build | Gradle Wrapper |

- 라이브러리와 컨테이너 버전은 호환성을 검증한 고정 버전을 사용한다. 새 인프라에 `latest`를 무조건 사용하지 않는다.
- 임베딩 차원, 모델명, 인덱스 타입, timeout, topic 이름, cache TTL은 Domain 상수가 아니라 Configuration으로 둔다.
- 기본 임베딩 모델이 `nomic-embed-text`이면 Milvus embedding dimension은 768로 맞춘다. 모델 변경 시 collection 재생성 또는 재색인 영향을 검토한다.

## 5. 저장소별 책임

| 저장소 | 소유 데이터 | 금지 사항 |
| --- | --- | --- |
| PostgreSQL | 원본 문서, 문서 metadata, 색인 상태, outbox, 처리 완료 메시지 | 임베딩 검색의 주 저장소로 사용하지 않음 |
| Milvus | 문서 청크, embedding, 검색 metadata | 원본 문서와 업무 상태의 기준 저장소로 사용하지 않음 |
| Kafka | 색인 요청 및 색인 결과 메시지 전달 | 장기 기준 데이터 저장소로 간주하지 않음 |
| Redis | Processing Lock, 선택적 단기 cache | 문서 상태나 멱등성의 최종 기준으로 사용하지 않음 |

- PostgreSQL이 원본 문서와 색인 상태의 Source of Truth다.
- Milvus는 PostgreSQL 원본으로부터 다시 만들 수 있는 검색 인덱스다.
- Milvus에는 최소한 `documentId`, `chunkId`, `documentVersion`, `chunkIndex`를 검색 metadata로 보존한다.
- 문서 수정 또는 삭제 시 PostgreSQL의 업무 상태를 먼저 확정하고 Milvus index를 재생성하거나 제거하는 명시적 Use Case를 사용한다.

## 6. 최상위 설계 원칙

### 6.1 Domain의 외부 무지

- Domain은 Spring, HTTP, JSON, JDBC/JPA, Spring AI, Milvus, Kafka, Redis, Ollama, MCP를 모른다.
- Domain 모델에 `Document`, `VectorStore`, `SearchRequest`, `ChatClient`, `TokenTextSplitter`, `KafkaTemplate`, `RedisTemplate`, persistence entity, web DTO를 넣지 않는다.
- Domain은 지식 문서 등록, 색인 상태 전이, 재색인, 색인 실패, 문서 폐기 같은 업무 사실과 불변식만 표현한다.

### 6.2 Hexagonal 의존성 방향

```text
Inbound Adapter -> Inbound Port -> Application Service -> Domain
                                         |
                                         v
                                   Outbound Port
                                         ^
                                         |
                                  Outbound Adapter
```

- 외부 계층은 내부 계층에 의존할 수 있지만 내부 계층은 외부 기술 계층에 의존하지 않는다.
- Application Service는 Domain과 Application Port만 의존한다.
- Adapter가 Application Outbound Port를 구현한다.
- Inbound Adapter와 Outbound Adapter는 서로 직접 호출하지 않는다.
- Spring annotations는 Application Service에서 프로젝트 관례상 사용할 수 있지만, 이 허용이 기술 구현체 의존을 허용하지는 않는다.

### 6.3 Ubiquitous Language

- 이름은 기술 처리보다 지식 관리 업무를 먼저 드러내야 한다.
- `process`, `handleData`, `updateStatus`, `manager`, `info`, `data` 같은 모호한 이름을 피한다.
- 다음과 같은 이름을 우선한다.
  - `registerKnowledgeDocument`
  - `requestDocumentIndexing`
  - `startIndexing`
  - `completeIndexing`
  - `failIndexing`
  - `retryDocumentIndexing`
  - `removeDocumentIndex`
  - `searchKnowledge`
  - `answerKnowledgeQuestion`

## 7. Domain 모델 기준

초기 Bounded Context는 `knowledge` 하나로 본다.

핵심 Aggregate 후보는 `KnowledgeDocument`다.

```text
KnowledgeDocument
├── DocumentId
├── title
├── originalContent
├── metadata
├── version
├── DocumentIndexingStatus
├── registeredAt
└── indexedAt / failureReason
```

색인 상태는 유한하고 안정적인 Domain enum으로 표현한다.

```text
PENDING
INDEXING
INDEXED
FAILED
DELETED
```

- Aggregate 생성자는 제한하고 `register(...)`와 persistence 복원용 `reconstitute(...)`를 분리한다.
- 상태 변경은 public setter가 아니라 `startIndexing()`, `completeIndexing()`, `failIndexing(...)`, `markDeleted()` 같은 behavior를 통해서만 수행한다.
- 상태 전이 불변식은 상태 변경 전에 Aggregate가 검증한다.
- Aggregate는 상태 변경이 확정된 뒤 service-local Domain Event를 내부 버퍼에 기록한다.
- `pullDomainEvents()`는 현재 이벤트의 불변 스냅샷을 반환한 뒤 내부 버퍼를 비워 중복 Outbox 기록을 막는다.
- `reconstitute(...)`는 persistence 복원일 뿐 신규 업무 행위가 아니므로 과거 Domain Event를 다시 기록하지 않는다.
- Domain Event에는 `eventId`, `correlationId`, topic, Kafka payload 같은 전달 metadata를 넣지 않는다. 이 값은 Application의 Outbox Envelope가 소유한다.
- Domain enum에 DB 코드, JSON label, HTTP status, Kafka topic을 넣지 않는다.
- `DocumentChunk`는 Milvus 저장 모양이 아니다. Domain/Application에서 필요하면 기술 필드가 없는 내부 모델로 정의하고, Milvus schema 변환은 Vector Adapter가 담당한다.

## 8. 패키지 구조

목표 패키지 구조는 다음과 같다.

```text
dev.study.airag
├── domain
│   ├── event
│   ├── model
│   └── vo
├── application
│   └── <feature>
│       ├── dto
│       │   ├── command
│       │   ├── query
│       │   └── result
│       ├── exception
│       ├── mapper
│       ├── outbox
│       ├── port
│       │   ├── in
│       │   └── out
│       │       └── dto
│       ├── policy
│       ├── service
│       └── validation
├── adapter
│   ├── in
│   │   ├── web
│   │   │   ├── common
│   │   │   │   ├── exception
│   │   │   │   └── response
│   │   │   └── <feature>
│   │   │       ├── controller
│   │   │       ├── request
│   │   │       ├── response
│   │   │       ├── mapper
│   │   │       └── exception
│   │   ├── mcp
│   │   └── messaging
│   └── out
│       ├── persistence
│       │   └── postgres
│       ├── vector
│       │   └── milvus
│       ├── messaging
│       │   └── kafka
│       ├── cache
│       │   └── redis
│       └── ai
│           └── ollama
│               └── <feature-or-capability>
├── common
└── config
    ├── graph
    ├── ocr
    └── web
```

- 최상위 패키지는 Hexagonal 계층 경계를 우선하고, `application`과 `adapter.in.web` 내부는 `knowledge`, `graph`, `ocr` 같은 기능을 먼저 나누는 Package-by-Feature를 사용한다.
- Application 코드는 `application.<feature>` 아래에서 `dto`, `exception`, `mapper`, `outbox`, `port`, `policy`, `service`, `validation` 역할로 나눈다. 모든 기능에 모든 하위 패키지를 미리 만들지 않고 실제 타입이 있을 때만 만든다.
- `knowledge`, `graph`, `ocr`는 하나의 `knowledge` Bounded Context와 단일 Gradle 모듈 안의 기능 네임스페이스다. 별도 Bounded Context, 모듈, 마이크로서비스 경계로 간주하지 않는다.
- 각 Web 기능 패키지 안에서 `controller`, `request`, `response`, `mapper`, 필요한 경우 `exception`으로 역할을 나눈다.
- Controller와 해당 OpenAPI Spec은 같은 `<feature>.controller` 패키지에 둔다.
- `adapter.in.web.common`에는 둘 이상의 Web 기능이 실제로 공유하는 오류 응답과 전역 예외 처리만 둔다. 기능 전용 타입이나 편의성 코드의 임시 보관소로 사용하지 않는다.
- Web 기능 패키지는 다른 Web 기능의 Controller, Request, Response, Mapper를 직접 호출하거나 재사용하지 않고 Application Inbound Port를 통해 협력한다.
- Ollama Adapter는 `adapter.out.ai.ollama.<feature-or-capability>` 아래에 두어 지식 답변, 그래프 추출, 문서 청킹, OCR 책임을 구분한다.
- 여러 기능의 공통 Application wiring은 `config` 루트에 두고, 기능 또는 프로토콜 전용 설정은 `config.<feature>` 또는 `config.web`에 둔다.
- 운영 Kotlin 코드는 top-level 타입 하나당 파일 하나를 사용하고 파일명은 타입명과 일치시킨다. DTO, Result, 예외, Port 보조 모델도 예외 없이 각각 독립 파일에 둔다.
- 같은 경계의 순수 extension mapping 함수는 타입 선언 없이 목적이 드러나는 `*Mappings.kt` 파일에 함께 둘 수 있다.
- 테스트 패키지는 운영 코드의 feature-first 패키지를 그대로 반영한다.
- 승인 없이 전체 구조를 한 번에 재작성하지 않는다. Vertical Slice 단위로 이전하고 매 단계 테스트를 통과시킨다.

## 9. DTO와 경계 모델

- Web Request는 `adapter.in.web.<feature>.request`에 둔다.
- Web Response는 `adapter.in.web.<feature>.response`에 둔다.
- 둘 이상의 Web 기능이 공유하는 오류 응답처럼 기능 중립적인 HTTP 계약만 `adapter.in.web.common.response`에 둔다.
- Application Command, Query, Result는 `application.<feature>.dto` 아래에 둔다.
- Web Request는 primitive/simple field를 가진 Command 또는 Query로만 변환한다.
- Web Request가 Domain VO를 직접 만들지 않는다.
- Application Service가 Command/Query를 Domain VO로 변환한다.
- Application Result는 HTTP status, JSON shape, MCP schema를 모른다.
- Web Response가 최종 HTTP 응답 모양을 소유한다.
- MCP Adapter는 Web Request/Response DTO를 재사용하지 않고 Application Inbound Port와 Result를 사용한다.
- Kafka Consumer는 wire message를 Application Command로 변환한 뒤 Inbound Port를 호출한다.
- Kotlin `data class`는 immutable DTO와 VO에 사용하고 mutable property와 public setter를 피한다.
- 케이스별 데이터가 다른 결과는 nullable field나 generic map보다 Kotlin `sealed interface`와 `data class`를 검토한다.

## 10. Application Port 기준

Inbound Port는 다음 업무 능력을 표현한다.

- `RegisterKnowledgeDocumentUseCase`
- `GetKnowledgeDocumentUseCase`
- `IndexKnowledgeDocumentUseCase`
- `RetryKnowledgeDocumentIndexingUseCase`
- `DeleteKnowledgeDocumentUseCase`
- `SearchKnowledgeUseCase`
- `AnswerKnowledgeQuestionUseCase`

Outbound Port는 다음 외부 능력을 표현한다.

- Knowledge Document load/save
- Outbox save/load/mark-published
- Durable processed-message claim
- Document chunking
- Embedding/vector index store, search, remove
- Kafka indexing message publish
- Redis processing lock acquire/release
- Local LLM answer generation
- Clock와 ID generation이 테스트 결정성을 위해 필요하면 별도 Port로 분리

- Port 이름은 기술 제품명보다 업무 능력을 우선한다. Milvus, Redis, Kafka라는 이름은 Adapter 또는 Config에 머무르게 한다.
- Application Service에서 `VectorStore`, `KafkaTemplate`, `RedisTemplate`, Spring Data Repository를 직접 주입하지 않는다.
- Application Service는 Use Case 흐름을 조율하고, 색인 가능 상태 같은 비즈니스 규칙은 Aggregate behavior에 둔다.

## 11. PostgreSQL과 Transactional Outbox

- 문서 등록과 색인 요청의 유실을 막기 위해 PostgreSQL 문서 저장과 Outbox 저장을 같은 로컬 트랜잭션에서 처리한다.
- Application Service는 Aggregate를 저장한 뒤 Aggregate가 실제로 기록한 Domain Event만 꺼내 Outbox Envelope로 감싸 저장한다.
- Outbox Envelope는 영구 중복 처리 식별자인 `eventId`와 요청 흐름 추적용 `correlationId`를 소유한다.
- DB commit과 Kafka publish가 원자적이라고 표현하지 않는다.
- Outbox Publisher는 미발행 레코드를 읽어 Kafka에 전송하고 성공한 레코드를 발행 완료로 표시한다.
- Outbox의 `event_type`은 Kotlin 클래스명과 분리된 버전형 고정 코드로 저장하고 Flyway migration으로 변경한다.
- Outbox polling, retry, cleanup 정책은 Configuration으로 관리한다.
- 최초 구현에 필요한 기본 테이블은 다음과 같다.
  - `knowledge_documents`
  - `outbox_events`
  - `processed_messages`
- DB schema는 Flyway 같은 명시적 migration으로 관리한다. 애플리케이션 기동 때 기존 테이블을 파괴하지 않는다.

## 12. Kafka 규칙

- Kafka는 색인 업무를 비동기로 분리하기 위해 사용한다.
- Domain과 Application은 topic name, serializer, KafkaTemplate, consumer record를 모른다.
- Producer Adapter가 Application/Domain event를 Kafka wire message로 변환한다.
- Consumer Adapter가 Kafka wire message를 Application Command로 변환한다.
- 메시지는 최소한 다음 식별자를 가진다.
  - `eventId`: 현재 메시지의 고유 ID
  - `correlationId`: 하나의 문서 색인 흐름 ID
  - `occurredAt`
  - `documentId`
  - `documentVersion`
- 결과 메시지를 추가하면 새 `eventId`를 만들고 원본 ID는 `sourceEventId`로 보존한다.
- Kafka key는 같은 문서 버전의 순서가 중요하므로 기본적으로 `documentId` 또는 합의된 correlation key를 사용한다.
- Consumer에서 비즈니스 상태를 직접 변경하거나 repository를 직접 조합하지 않는다.
- 실패를 로그만 남기고 정상 처리로 끝내지 않는다. 재시도 가능 실패와 최종 색인 실패 상태를 구분한다.
- 현재는 단일 서비스의 비동기 파이프라인이므로 SAGA, 중앙 Orchestrator, 보상 이벤트를 도입하지 않는다.

## 13. Consumer 멱등성과 Redis 규칙

- Redis `SETNX`/`setIfAbsent`는 동시 중복 처리를 줄이는 짧은 Processing Lock으로만 사용한다.
- Lock key는 consumer와 event identity가 드러나게 구성하고 TTL을 반드시 둔다.
- Lock 값에는 owner token을 저장하고, 해제할 때 같은 owner인지 확인한다. 다른 처리자의 Lock을 삭제하지 않는다.
- 처리 실패 시 재전달이 가능하도록 자신이 획득한 Lock을 해제한다.
- Redis Lock을 처리 완료의 최종 증거로 사용하지 않는다.
- 영구 멱등성은 PostgreSQL `processed_messages`의 `consumer_name + event_id` unique constraint로 보장한다.
- 처리 완료 기록과 업무 변경을 가능한 한 같은 로컬 트랜잭션에서 다룬다.
- Redis에 문서 색인 상태의 기준 데이터를 저장하지 않는다.
- 검색 cache는 정합성, TTL, 문서 버전 기반 invalidation 전략이 승인된 뒤 추가한다.
- LLM 답변은 모델과 prompt에 따라 달라질 수 있으므로 기본적으로 cache하지 않는다.

## 14. Milvus와 RAG 규칙

- Milvus는 유일한 Vector DB이며 검색 index 역할만 한다.
- Milvus collection/schema/index 설정은 Vector Adapter와 Config에 둔다.
- Application과 Domain은 Milvus client, collection name, index parameter를 모른다.
- 원본 문서가 기준이므로 Milvus data는 전체 재색인할 수 있어야 한다.
- 문서 version이 바뀌면 이전 version의 chunk를 제거하거나 검색에서 제외한 뒤 새 version을 저장한다.
- 청킹 전략은 Outbound Port 뒤에 둔다. Application Service가 `TokenTextSplitter`를 직접 생성하지 않는다.
- 검색은 `topK`, similarity threshold, metadata filter를 명시적 Query로 전달한다.
- 검색 결과는 Milvus raw result가 아니라 Application Result로 변환한다.
- RAG 답변은 검색된 근거와 질문을 Local LLM Port에 전달해 생성한다.
- 답변과 함께 근거 문서 ID, chunk ID, score 등 추적 가능한 source를 반환한다.
- 같은 질문을 답변용 Advisor와 source 반환용으로 중복 검색하지 않도록 검색 결과 재사용 구조를 우선 검토한다.

## 15. REST와 MCP 규칙

- 문서 등록이 비동기 색인을 시작하면 `POST` 응답은 기본적으로 `202 Accepted`와 문서 ID 및 `PENDING` 상태를 반환한다.
- Controller는 validation, Request-to-Command 변환, Inbound Port 호출, Result-to-Response 변환만 담당한다.
- Controller가 VectorStore, Repository, Kafka, Redis를 직접 호출하지 않는다.
- 공통 오류 응답 형식과 HTTP status 의미를 일치시킨다.
- MCP Tool은 Web Controller를 호출하지 않고 동일한 Inbound Port를 호출한다.
- MCP Tool annotation과 MCP schema는 MCP Adapter에만 둔다.
- 검색과 답변 Tool에는 실제 행위에 맞는 read-only, non-destructive hint를 유지한다.
- OpenAPI Spec을 Controller 구현과 분리하면 같은 `adapter.in.web.<feature>.controller` 패키지에 두고 해당 Controller가 구현한다.
- 실제 HTTP 계약이 없는 기능을 위해 Spec 계층을 미리 만들지 않는다.

## 16. Kotlin 코드 작성 기준

- Constructor injection을 사용한다. Lombok 규칙은 적용하지 않는다.
- Domain class는 필요하면 `private constructor`와 companion object factory를 사용한다.
- VO와 DTO는 불변 `data class`를 우선한다.
- Domain collection은 mutable reference를 외부로 노출하지 않는다.
- `Any`, raw `Map`, nullable field를 업무 모델의 기본 구조로 사용하지 않는다.
- Extension function은 계층 경계를 숨기지 않는 순수 변환에만 사용한다.
- `require`만으로 모든 Domain 예외를 표현하지 않는다. 호출자가 구분해야 하는 업무 실패에는 명시적인 Domain/Application 예외를 사용한다.
- 프로젝트가 소유한 Application Service, Port, 내부 helper 메서드의 파라미터는 최대 2개로 제한한다. 3개 이상의 값이 필요하면 업무 의미가 드러나는 Command, Query, Criteria, Context 같은 Parameter Object로 묶는다.
- Application Service는 유스케이스 흐름을 조율하고 결과 모델의 필드별 조립은 순수 mapping 함수로 분리한다.
- 하나의 메서드 안에서 Use Case 흐름, 기술 mapping, formatting, primitive validation을 섞지 않는다.
- 의미 없는 generic abstraction보다 구체적인 도메인 이름을 우선한다.

## 17. Mapper와 Adapter 기준

- Mapper는 외부 저장/전송 모양과 내부 모델 사이의 순수 번역기다.
- Mapper가 repository, service, port를 호출하거나 비즈니스 상태를 판단하지 않는다.
- PostgreSQL Entity에서 Domain으로 복원할 때 `reconstitute(...)`를 사용한다.
- Milvus Document와 Spring AI Document는 Vector Adapter 밖으로 노출하지 않는다.
- Kafka payload와 serializer type은 Messaging Adapter 밖으로 노출하지 않는다.
- Redis key 조립과 token 검증은 Cache/Lock Adapter 책임이다.
- Ollama/Spring AI prompt와 model option 조립은 AI Adapter 또는 Config 책임이다.

## 18. 주석과 문서 정책

- 이름과 구조로 표현할 수 있는 내용을 주석으로 반복하지 않는다.
- 주요 Domain behavior에는 상태 전이 조건과 불변식 같은 업무 계약을 KDoc으로 설명할 수 있다.
- Port에는 absence, idempotency, 저장 책임 같은 boundary contract를 설명한다.
- Adapter 주석은 외부 프로토콜과 내부 계약 사이의 중요한 변환 이유만 설명한다.
- `TODO`, `FIXME`, `HACK`는 추적 가능한 이슈 또는 명확한 제거 조건 없이 남기지 않는다.
- 주석 처리된 죽은 코드를 남기지 않는다.
- 아키텍처 결정이 바뀌면 코드와 함께 프로젝트 전용 문서를 갱신한다.

## 19. 테스트와 Quality Gate

### 작업 전 확인

- JDK 21 LTS가 설치되어 있고 Gradle과 IDE가 같은 JDK 21 Toolchain을 찾는지 확인한다.
- `JAVA_HOME`, IntelliJ Gradle JVM, Gradle Toolchain 설정을 구분해서 확인한다.
- 로컬 인프라가 필요한 검증은 컨테이너와 포트 충돌을 먼저 확인한다.

### 기본 명령

프로젝트 루트 `C:\workspace\knowledge-rag-platform`에서 실행한다.

```powershell
.\gradlew.bat test
.\gradlew.bat check
```

필요한 경우 전체 재검증은 다음 명령을 사용한다.

```powershell
.\gradlew.bat clean check
```

### 테스트 기준

- Domain 상태 전이와 불변식은 Spring 없는 순수 단위 테스트로 검증한다.
- Application Service는 Outbound Port mock/fake로 Use Case 흐름을 검증한다.
- Web/MCP/Kafka Adapter는 Request/Message 변환과 경계 계약을 검증한다.
- Web Adapter 테스트는 운영 코드의 기능 우선 패키지 구조를 따라 배치한다.
- PostgreSQL, Kafka, Redis, Milvus 연동은 Testcontainers 또는 명시적 integration profile로 검증한다.
- Redis Lock은 owner token, TTL, 중복 획득, 실패 해제를 테스트한다.
- Kafka Consumer는 concurrent lock과 durable processed-message 멱등성을 각각 테스트한다.
- Outbox는 DB commit, publish 성공, publish 실패, 재시도, 중복 publish 경계를 테스트한다.
- Milvus는 색인, 검색, version 교체, 삭제, 재색인을 테스트한다.
- Architecture Test로 다음을 고정한다.
  - Domain의 framework/outer-layer 의존 금지
  - Application의 Adapter 구현 의존 금지
  - Inbound와 Outbound Adapter 직접 의존 금지
  - Web/MCP/Kafka DTO의 Domain 터널링 금지
  - Spring AI/Kafka/Redis/Milvus type의 Domain/Application 유출 금지

## 20. 품질 우선순위

- P0: 빌드/테스트 실패, 계층 의존 위반, 데이터 유실, 중복 색인, 보안 문제, 원본 문서 손상
- P1: God Service, DTO 터널링, 잘못된 상태 전이, 멱등성 누락, 예외 삼키기, 기술 타입 유출
- P2: 네이밍, 문서, fixture, 작은 중복과 가독성 개선

- Architecture P0/P1은 기능 추가보다 먼저 또는 같은 Vertical Slice 안에서 해결한다.
- 품질 도구의 rule, threshold, suppress, exclude를 먼저 약화하지 않는다.
- 외부 인프라 문제로 전체 검증이 불가능하면 가능한 단위/경계 검증을 수행하고 미확인 범위를 명확히 보고한다.
- 테스트 또는 `check`가 실패한 상태를 완료라고 보고하지 않는다.

## 21. 참조 문서와 적용 우선순위

1. 루트 `AGENTS.md`의 이 프로젝트 전용 규칙
2. `docs/architecture/ddd-hexagonal-agents-guide.md`의 핵심 철학과 재사용 DDD/Hexagonal 규칙
3. `docs/architecture/pure-eda-saga-agents-guide.md`의 Kafka 계약, Port/Adapter, Consumer 멱등성 규칙
4. `docs/architecture/backend-quality-gate-guide.md`의 Quality Gate 절차

- 참조 문서의 다른 프로젝트 도메인과 Java 예제는 설명용이다. 이 프로젝트의 패키지, 도메인 이름, 모듈, 명령으로 강제하지 않는다.
- 이 프로젝트는 현재 MSA나 분산 SAGA를 사용하지 않는다. EDA 문서의 MSA 서비스 분리, Choreography SAGA, 보상 흐름은 별도 아키텍처 승인 전에는 적용하지 않는다.
- 참조 문서와 루트 `AGENTS.md`가 충돌하면 루트 `AGENTS.md`를 우선한다.
- 존재하지 않는 문서, 스킬, 모듈 또는 Gradle task를 있다고 가정하지 않는다.

## 22. 변경 완료 체크리스트

- [ ] 승인된 작업 계획 범위만 변경했는가?
- [ ] Domain이 Spring AI, Kafka, Redis, Milvus, HTTP를 모르는가?
- [ ] Application이 Adapter 구현체가 아니라 Port에만 의존하는가?
- [ ] REST, MCP, Kafka DTO가 Application/Domain으로 터널링되지 않는가?
- [ ] PostgreSQL이 원본과 색인 상태의 Source of Truth인가?
- [ ] Milvus가 유일한 Vector DB이고 재색인 가능한가?
- [ ] Kafka publish 유실과 Consumer 중복 처리 경로가 설계됐는가?
- [ ] Redis Lock과 PostgreSQL durable idempotency가 분리됐는가?
- [ ] 상태 변경이 `KnowledgeDocument` behavior로 표현되는가?
- [ ] 관련 Domain/Application/Adapter/Architecture 테스트가 통과하는가?
- [ ] `.\gradlew.bat check` 결과를 보고했는가?
- [ ] 미확인 integration gate와 남은 위험을 명확히 보고했는가?
