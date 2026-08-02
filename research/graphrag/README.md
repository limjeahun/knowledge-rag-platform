# GraphRAG 비교 연구 환경

이 디렉터리는 운영 애플리케이션과 분리된 재현 가능한 비교 실험 환경이다.

- 운영 기준선: 이 저장소의 `Protégé + OWLAPI + HermiT + SHACL + Jena/Fuseki/TDB2 + Milvus`
- 비교군 A: Microsoft GraphRAG `3.1.0`
- 비교군 B: LightRAG `1.5.4`
- Local model client: Ollama Python SDK `0.6.2`
- 공통 데이터셋: `dataset/software-architecture-v1`

Python 패키지는 루트 Gradle 빌드나 Spring Boot classpath에 들어가지 않는다. `uv.lock`과 이
디렉터리의 `.venv`만 사용하며 생성된 workspace, 실행 결과와 평가 보고서는 Git에서 제외한다.

Windows 애플리케이션 제어가 Microsoft GraphRAG의 `graspologic_native.pyd`를 차단할 수
있다. 파일 차단을 강제로 해제하지 말고 아래 Docker 실행 경로를 사용한다. Docker 서비스는
루트 `docker-compose.yml`의 선택적 `research` profile에 있어 일반 `docker compose up`에는
포함되지 않는다.

```powershell
docker compose --profile research build microsoft-graphrag lightrag-research
docker compose run --rm microsoft-graphrag rag-compare smoke
```

## 환경 준비

PowerShell에서 이 디렉터리로 이동해 실행한다.

```powershell
uv sync --python 3.12
uv run rag-compare --help
```

Microsoft GraphRAG workspace와 기본 prompt를 만든다.

```powershell
uv run rag-compare prepare --system microsoft
```

Docker 경로에서는 저장소 루트에서 같은 명령을 실행한다.

```powershell
docker compose run --rm microsoft-graphrag rag-compare prepare --system microsoft
docker compose run --rm microsoft-graphrag rag-compare index --system microsoft
docker compose run --rm microsoft-graphrag rag-compare run --system microsoft --method local
```

`configs/microsoft/settings.ollama.yaml`은 Ollama의 OpenAI 호환 endpoint를 사용하는 연구
설정이다. `OLLAMA_CHAT_MODEL`, `OLLAMA_EMBEDDING_MODEL`, `OLLAMA_BASE_URL` 환경 변수를
필요에 맞게 바꿀 수 있다. Microsoft GraphRAG는 구조화 출력 품질에 민감하므로 사용 모델이
JSON schema 출력을 안정적으로 지키지 못하면 비교 결과에 실패로 기록한다.

LightRAG Server는 같은 가상환경에서 별도 프로세스로 실행한다.

```powershell
uv run lightrag-server --host 127.0.0.1 --port 9621
```

Docker에서는 다음 서비스가 Ollama host endpoint와 연구용 영속 workspace를 이미 연결한다.

```powershell
docker compose --profile research up -d lightrag-research
```

LightRAG의 모델·저장소 환경 변수는 `configs/lightrag/env.ollama.example`을 복사해 현재
PowerShell 세션에 설정한다. 비밀값이 들어간 실제 `.env`는 커밋하지 않는다.

## 색인과 질의

세 시스템 모두 같은 세 문서와 세 질문을 사용한다.

```powershell
# 운영 기준선이 localhost:8080에서 실행 중일 때
uv run rag-compare index --system primary
uv run rag-compare run --system primary --method hybrid

# Microsoft GraphRAG
uv run rag-compare index --system microsoft
uv run rag-compare run --system microsoft --method local

# LightRAG Server가 localhost:9621에서 실행 중일 때
uv run rag-compare index --system lightrag
uv run rag-compare run --system lightrag --method hybrid
```

endpoint가 다르면 `PRIMARY_RAG_BASE_URL`, `LIGHTRAG_BASE_URL`을 지정한다. LightRAG 인증을
켰다면 `LIGHTRAG_API_KEY`도 지정한다.

## 평가

실행기는 모든 결과를 동일한 JSONL 계약으로 저장한다. 평가기는 LLM judge 대신 데이터셋에
명시된 용어·관계 code·근거 파일 회수율, 지연시간과 실패 건수를 계산한다.

```powershell
uv run rag-compare evaluate `
  results/primary-hybrid.jsonl `
  results/microsoft-local.jsonl `
  results/lightrag-hybrid.jsonl
```

결과는 `reports/comparison-details.csv`와 `reports/comparison-summary.json`에 생성된다.
품질 비교 시 모델명, 모델 digest, 온도, 데이터셋 commit, 실행 시각과 하드웨어를 함께
기록해야 한다. 이 harness는 제품 우열을 단정하는 도구가 아니라 동일 조건의 연구 실험을
반복하기 위한 실행 기반이다.
