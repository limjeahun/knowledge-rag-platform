# 백엔드 Quality Gate 지속 개선 실행 지시서

이 문서는 특정 프로젝트의 실행 결과를 기록하는 측정지가 아니다. 작업자가 이 문서를 읽고 백엔드 소스 코드를 반복적으로 점검, 수정, 재검증하면서 목표 품질에 도달할 때까지 작업하기 위한 범용 실행 지시서다.

이 문서는 여러 백엔드 프로젝트에서 재사용될 수 있어야 한다. 특정 repository, 특정 skill, 특정 빌드 스크립트, 특정 모듈명, 특정 패키지 구조에 종속된 내용을 전제로 하지 않는다. 프로젝트마다 빌드 도구, 테스트 명령, 리포트 경로, Quality Gate 기준이 다를 수 있으므로 작업자는 먼저 해당 프로젝트의 실제 설정을 확인한 뒤 이 지시서의 절차를 적용한다.

---

# 1. 목표

작업 목표는 Quality Gate 도구를 한 번 실행하는 것이 아니라, 도구 피드백을 근거로 소스 코드와 테스트를 개선해 원하는 품질 기준을 만족하는 상태를 만드는 것이다.

완료 상태는 다음을 의미한다.

- 프로젝트의 관련 테스트가 통과한다.
- 프로젝트에 정의된 전체 Quality Gate 명령이 통과한다.
- SonarQube 같은 외부 품질 게이트가 설정되어 있으면 Quality Gate가 PASS다.
- 남은 P0가 없다.
- 현재 작업 범위의 P1은 수정됐거나, 외부 조건/기존 무관 실패/명시적 범위 제외로 남은 이유가 최종 보고에 설명된다.
- P2는 필요 시 남길 수 있지만, 남긴 항목과 다음 조치가 최종 보고에 명확하다.

---

# 2. 작업 기본 원칙

- 사용자가 명시적으로 문서만 수정하라고 한 경우가 아니라면, 품질 개선 요청은 코드와 테스트를 실제로 수정하는 요청으로 해석한다.
- 프로젝트별 지침 문서가 있으면 먼저 읽고 따른다.
  - 예: contributor guide, repository README, architecture guide, coding convention 문서
- 프로젝트별 지침과 이 문서가 충돌하면 프로젝트별 지침을 우선한다.
- 검증 도구의 rule, threshold, exclude, suppress를 먼저 완화하지 않는다.
- suppress 또는 exclude는 generated code, 명확한 false positive, 외부 라이브러리 경계처럼 코드 개선으로 해결할 수 없는 경우에만 사용한다.
- coverage 숫자만 올리는 테스트를 만들지 않는다. 핵심 비즈니스 흐름의 성공, 실패, 예외, 경계 조건을 검증한다.
- 한 번에 모든 문제를 섞어 고치지 않는다. 실패를 우선순위로 나누고, 하나의 원인 또는 vertical slice를 수정한 뒤 targeted 검증을 실행한다.
- 기존 사용자 변경을 되돌리지 않는다. 관련 없는 변경은 무시하고, 관련 변경은 읽고 함께 작업한다.

---

# 3. 프로젝트 적응 절차

이 절은 작업 전 1회 수행하는 사전 준비 절차다.

작업자는 이 문서를 적용하기 전에 프로젝트의 실제 Quality Gate 구성을 먼저 파악한다.

확인할 항목:

- 빌드 도구
  - Gradle, Maven, npm, pnpm, yarn, Makefile, Docker Compose, CI script 등
- 테스트 명령
  - unit test, integration test, architecture test, contract test, e2e test
- 정적 분석 도구
  - SonarQube, Checkstyle, PMD, SpotBugs, Error Prone, ESLint, ktlint, detekt 등
- coverage 도구
  - JaCoCo, Istanbul, coverage.py, pytest-cov 등
- 리포트 위치
  - XML, HTML, JSON, console report 위치
- CI에서 사용하는 품질 명령
  - `qualityCheck`, `check`, `verify`, `test`, `lint`, `sonar`, `ci` 등
- 제외 대상
  - generated code, migration file, framework bootstrap, config file 등

프로젝트별 명령을 확인한 뒤, 이 문서의 `<quality-gate-command>`, `<module>`, `<test-command>`, `<report-path>` placeholder를 실제 명령과 경로로 바꿔 적용한다.

단, 작업 중 빌드 설정, 테스트 설정, 리포트 생성 설정, Quality Gate threshold를 변경한 경우에는 관련 항목을 다시 확인한다.

---

# 4. 도구별 검사 목적과 피드백 해석

| 도구 | 무엇을 검사하는가 | 작업자가 확인해야 할 피드백 | 기본 조치 |
| --- | --- | --- | --- |
| Test Runner | unit, integration, architecture, contract test 실행 결과 | 실패한 test class/method, assertion, exception, fixture 문제, 환경 의존 실패 여부 | production code 또는 test fixture를 원인에 맞게 수정하고 같은 테스트를 다시 실행한다. |
| Architecture Test | 계층 의존성, package boundary, clean/hexagonal architecture 규칙 | 실패 rule, 위반 클래스, 금지된 의존 경로, 깨진 계층 경계 | architecture rule을 완화하지 않고 코드 경계를 복구한다. |
| Static Analysis | source 또는 bytecode 기반 결함/유지보수성 위험 | rule id, priority/severity, 파일/라인, 결함 가능성, false positive 가능성 | suppress보다 구조 개선, 책임 분리, 안전한 API 사용을 우선한다. |
| Security Analysis | 취약점, 보안 hotspot, unsafe API 사용 | 취약 rule, 공격 가능성, 노출 경로, review 필요 여부 | 실제 위험이면 수정하고, safe 판단이면 근거를 남긴다. |
| Coverage | 테스트가 실행한 line/branch/function/path coverage | coverage gap, 테스트되지 않은 핵심 branch, 제외 대상 적용 여부 | 핵심 도메인/application/use case branch 테스트를 보강한다. |
| Duplication | 중복 코드 또는 중복 block | 중복 위치, 공통화 가능성, 의미상 분리 필요 여부 | 의미가 같은 중복은 공통화하고, 우연한 유사성은 과한 추상화를 피한다. |
| SonarQube | 통합 Quality Gate, New Code 품질, 보안 review, duplication | Bugs, Vulnerabilities, Hotspots, Coverage, Duplication, rating, issue 위치 | 이슈를 local tool 결과와 연결해 수정하고, Quality Gate PASS까지 재검증한다. |

---

# 5. 리포트 확인 원칙

이 절은 7절 반복 루프 실행 중 리포트를 확인할 때 참조한다.

작업자는 콘솔 로그만 보지 말고 가능한 경우 도구 리포트를 함께 확인한다.

프로젝트별로 실제 위치는 다를 수 있으므로, 다음 후보를 우선 탐색한다.

| 도구 유형 | 흔한 리포트 후보 |
| --- | --- |
| Test | `build/test-results`, `build/reports/tests`, `target/surefire-reports`, `target/failsafe-reports`, `coverage`, `test-results` |
| Static Analysis | `build/reports`, `target/site`, `target/*.xml`, `reports`, CI artifact |
| Coverage | `build/reports/jacoco`, `target/site/jacoco`, `coverage/lcov-report`, `coverage.xml`, `htmlcov` |
| SonarQube | SonarQube UI, scanner console output, imported XML reports |

리포트가 없으면 빌드 설정에서 report task가 비활성화되어 있는지 확인한다. 리포트 생성 설정을 바꾸는 것은 사용자의 요청이나 프로젝트 관례에 맞을 때만 한다.

---

# 6. 목표 Quality Gate

프로젝트에 명시된 기준이 있으면 그 기준을 따른다. 명시 기준이 없으면 아래 기준을 기본값으로 삼는다.

## 6.1 Local Gate

Local Gate는 외부 서비스 없이 로컬에서 확인 가능한 기본 품질 기준이다.

| 항목 | 기본 목표 |
| --- | --- |
| 전체 테스트 | PASS |
| architecture/boundary test | PASS |
| static analysis | high/critical issue 0 |
| lint/style check | blocking violation 0 |
| coverage | 프로젝트 기준 충족. 기준이 없으면 line 70% 이상을 기본 목표로 사용 |
| branch coverage | 프로젝트 기준 충족. 기준이 없으면 핵심 비즈니스 branch 60% 이상을 기본 목표로 사용 |

## 6.2 SonarQube Gate

SonarQube 서버와 token이 제공된 경우에만 실행한다.

| 항목 | 기본 목표 |
| --- | --- |
| Quality Gate | PASS |
| Bugs | 0 |
| Vulnerabilities | 0 |
| Security Hotspots | 모두 review 완료. 실제 위험 항목은 수정 |
| New Code Coverage | 프로젝트 기준 충족. 기준이 없으면 70% 이상 |
| New Code Duplication | 프로젝트 기준 충족. 기준이 없으면 3% 이하 |
| Reliability Rating | A |
| Security Rating | A |
| Maintainability Rating | A 또는 프로젝트 기준 충족 |

SonarQube URL 또는 token이 없으면 SonarQube Gate는 외부 조건으로 `BLOCKED`라고 보고한다. 이때도 Local Gate 개선은 계속 진행한다.

---

# 7. 반복 실행 루프

3절 사전 준비가 완료된 이후 반복 실행한다.

작업자는 품질 개선 작업을 다음 루프로 진행한다.

1. 지침 확인
   - 프로젝트별 지침 문서
   - 이 문서
2. 작업 전 상태 확인
   - version control 상태 확인
   - 관련 파일의 기존 변경 여부 확인
3. Quality Gate 구성 파악
   - 전체 품질 명령
   - targeted 테스트 명령
   - static analysis 명령
   - coverage 명령
   - report 위치
4. 기준선 실행
   - 전체 명령이 너무 무겁거나 외부 의존성이 있으면 관련 targeted 명령부터 실행한다.
   - 가능한 경우 전체 Quality Gate 명령을 실행해 baseline을 잡는다.
5. 리포트 확인
   - console output과 report file을 함께 확인한다.
6. 실패 분류
   - P0, P1, P2로 나눈다.
   - 이번 변경과 관련 없는 기존 실패인지 확인한다.
7. 원인 분석
   - rule 이름만 보고 기계적으로 고치지 않는다.
   - 계층 책임, 도메인 규칙, API 계약, 테스트 의도, runtime 환경을 확인한다.
8. 수정
   - P0부터 처리한다.
   - 하나의 원인 또는 vertical slice 단위로 수정한다.
9. targeted 재검증
   - 수정한 실패 유형에 맞는 가장 작은 검증을 먼저 실행한다.
10. 전체 재검증
   - targeted 검증이 통과하면 프로젝트의 전체 Quality Gate 명령을 실행한다.
11. 반복
   - Local Gate가 통과하고, 가능한 경우 외부 Quality Gate가 PASS가 될 때까지 5~10을 반복한다.
12. 완료 확인 — 13절 완료 조건을 모두 만족하면 루프를 종료하고 14절 보고로 이동한다. 조건을 만족하지 못하면 5단계로 돌아간다.

---

# 8. 실패 유형별 재검증 매핑

이 절은 7절 반복 루프의 6단계(실패 분류) 및 9단계(targeted 재검증) 실행 시 참조한다.

| 실패 유형 | 먼저 확인할 것 | 수정 후 targeted 검증 | 최종 검증 |
| --- | --- | --- | --- |
| 일반 테스트 실패 | 실패 test, assertion, stacktrace, fixture | 해당 test 또는 해당 module test | `<quality-gate-command>` |
| architecture/boundary 실패 | 실패 rule, 위반 dependency, package 위치 | 해당 architecture test | `<quality-gate-command>` |
| static analysis 실패 | rule id, severity, file/line, false positive 여부 | 해당 static analysis task | `<quality-gate-command>` |
| security issue | 취약 rule, 실제 노출 가능성, 입력 경로 | 관련 test/static analysis/security scan | `<quality-gate-command>`, 가능하면 external gate |
| coverage 실패 | coverage report의 부족 package/class/branch | 해당 module test + coverage verification | `<quality-gate-command>` |
| duplication 실패 | 중복 block, 공통화 가능성, 의미 보존 여부 | 관련 test + duplication/static analysis | `<quality-gate-command>`, 가능하면 external gate |
| external gate 실패 | external UI issue, local report import 여부 | 관련 local command | external gate 재실행 |

---

# 9. 명령 placeholder

이 문서는 특정 빌드 도구를 강제하지 않는다. 작업자는 프로젝트 설정을 확인해 아래 placeholder를 실제 명령으로 대체한다.

| Placeholder | 의미 | 예시 |
| --- | --- | --- |
| `<quality-gate-command>` | 전체 품질 검증 명령 | `./gradlew qualityCheck`, `mvn verify`, `npm run ci`, `make quality` |
| `<test-command>` | 전체 또는 module test 명령 | `./gradlew test`, `mvn test`, `npm test` |
| `<targeted-test-command>` | 실패 test만 재실행하는 명령 | `./gradlew :module:test --tests SomeTest`, `mvn -Dtest=SomeTest test` |
| `<static-analysis-command>` | lint/static analysis 명령 | `./gradlew pmdMain spotbugsMain`, `mvn pmd:check spotbugs:check`, `npm run lint` |
| `<coverage-command>` | coverage report 또는 verification 명령 | `./gradlew jacocoTestReport`, `mvn jacoco:report`, `npm run coverage` |
| `<external-gate-command>` | SonarQube 등 외부 gate 명령 | `./gradlew sonar`, `mvn sonar:sonar`, `sonar-scanner` |
| `<report-path>` | 실패 분석 리포트 위치 | 프로젝트별 build/report artifact |

---

# 10. 수정 규칙

## 10.1 Architecture

- 도메인/비즈니스 로직은 프레임워크, persistence, messaging, web adapter 세부 구현에 과하게 의존하지 않게 한다.
- application/use case 계층은 orchestration에 집중하고 기술 구현체에 직접 결합하지 않게 한다.
- inbound adapter는 request/message를 command/query/input model로 변환한 뒤 use case를 호출한다.
- outbound adapter는 persistence, messaging, external API 같은 기술 세부사항을 감싸고 application port/interface를 구현한다.
- controller 또는 handler가 domain entity/persistence entity를 직접 외부 응답으로 노출하지 않게 한다.

## 10.2 API and Contract

- public API DTO, application input/output model, domain model, persistence model, messaging contract를 구분한다.
- 외부 message 또는 generated schema type을 domain model처럼 재사용하지 않는다.
- service boundary를 넘는 상태 변경은 프로젝트가 정한 messaging/API 계약을 따른다.
- 직접 service-to-service HTTP 호출 금지 같은 프로젝트 규칙이 있으면 반드시 따른다.

## 10.3 Domain and Policy

- 비즈니스 정책값과 finite business concept은 magic string/number보다 enum, value object, policy object, domain behavior로 표현한다.
- runtime configurable technical value는 configuration으로 분리한다.
- domain event가 있는 프로젝트에서는 event가 실제로 발생한 사실을 표현하게 하고, transport metadata와 domain fact를 섞지 않는다.

## 10.4 Test and Coverage

- coverage 부족은 핵심 business branch 테스트로 해결한다.
- DTO, generated code, 단순 config만 억지로 테스트하지 않는다.
- 테스트 이름과 assertion은 비즈니스 규칙 또는 실패 조건을 설명해야 한다.
- fixture 문제로 실패한 경우 production code를 왜곡하지 말고 fixture를 수정한다.

## 10.5 Suppress / Exclude 판단 기준

- 코드 수정으로 해결 가능한가? → 가능하면 suppress 금지
- generated code, migration file, framework bootstrap인가? → 파일 경로 또는 어노테이션으로 확인 후 exclude 허용
- 외부 라이브러리 경계에서 발생한 false positive인가? → 근거를 주석으로 남기고 suppress 허용
- 위 세 조건에 해당하지 않으면 suppress/exclude 대신 구조 개선을 먼저 시도한다

---

# 11. 우선순위

## P0: 즉시 수정해야 하는 실패

- 전체 테스트 실패
- architecture/boundary test 실패
- external Quality Gate의 Bug/Vulnerability
- static analysis high/critical issue
- 런타임 장애 가능성이 높은 null/resource/equality/exception 문제
- 보안 취약점
- public API가 내부 domain/persistence model을 직접 노출하는 문제
- service boundary 또는 architecture rule을 깨는 문제
- 데이터 손실, 중복 처리, idempotency 실패, 보상 중복 실행 같은 운영 위험

P0 항목이라도 단일 작업 범위를 초과하는 구조적 설계 문제인 경우, 12절 BLOCKED 기준에 따라 blocked로 보고할 수 있다. 이 경우 blocked 사유와 재작업 범위를 명시해야 한다.

## P1: Quality Gate 달성을 위해 우선 처리

- static analysis major issue
- 실제 결함 가능성이 있는 medium issue
- coverage 기준 미달
- duplication 기준 초과
- 복잡도 높은 메서드/class
- 중복 조건
- 빈 catch 또는 예외 삼키기
- 과도한 static constant/magic value
- DTO/domain/persistence contract 혼합
- application/use case에 business/protocol 상수 집중

## P2: 품질을 더 안정화하기 위한 정리

- 네이밍 개선
- 경미한 문서 보강
- 스타일 정리
- 테스트 fixture 정리
- 리포트 가독성 개선
- 중복 helper 정리
- 작은 mapper/adapter 정리

---

# 12. BLOCKED 판단

다음 경우에만 blocked로 보고한다.

- 외부 Quality Gate URL/token/권한이 없어 실행할 수 없다.
- Docker, DB, message broker, cache, external service 등 외부 런타임이 필요한 테스트가 현재 환경에서 실행 불가능하고, 해당 실패가 이번 변경과 무관하다.
- 기존 실패가 이번 작업 범위 밖에 있으며, 이를 고치려면 사용자가 요청하지 않은 큰 리팩터링이 필요하다.
- 도구 자체 설정, dependency resolution, license, network 문제로 검증을 진행할 수 없고, 코드 수정으로 해결할 수 없다.

Blocked로 보고할 때도 가능한 local targeted 검증은 실행하고, 어떤 gate가 확인됐고 어떤 gate가 미확인인지 구분한다.

---

# 13. 완료 조건

작업자는 다음 조건을 만족해야 작업을 완료할 수 있다.

- 수정 범위의 targeted 검증이 통과한다.
- 프로젝트의 전체 Quality Gate 명령이 통과한다.
- 외부 Quality Gate 실행 환경이 있으면 외부 Quality Gate가 PASS다.
- 외부 Quality Gate 실행 환경이 없으면 미실행 사유와 Local Gate 결과를 보고한다.
- 남은 P0가 없다.
- 현재 작업 범위의 P1이 남아 있으면 완료가 아니라 blocked 또는 범위 제외로 보고한다.
- 남은 P2는 다음 조치와 함께 보고한다.

전체 Quality Gate 명령을 실행하지 못했거나 실패한 상태에서는 완료라고 말하지 않는다. 그 경우 최종 응답은 성공 보고가 아니라 부분 완료 또는 blocked 보고여야 한다.

---

# 14. 최종 보고 형식

작업 완료 시 작업자는 이 문서에 실행값을 누적 기록하지 않는다. 사용자에게 다음 내용을 간결하게 보고한다.

- 수정한 파일과 핵심 변경 내용
- 해결한 P0/P1/P2 항목
- 실행한 targeted command와 결과
- 실행한 full gate command와 결과
- 외부 Quality Gate 실행 여부와 결과
- 남은 P2, blocked 사유, 또는 미확인 gate
- P0가 blocked 처리된 경우: blocked 사유, 해당 gate 또는 항목, 재작업에 필요한 범위를 명시하고 부분 완료로 보고한다

실행 결과를 이 문서에 영구 기록해야 하는 경우는 사용자가 명시적으로 요청했을 때만 해당한다.
