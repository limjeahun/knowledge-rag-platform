# Ollama + Continue 로컬 LLM 설치·연동 가이드

> 작성 기준: 2026-07-17  
> 대상 운영체제: Windows 10/11, macOS 14 이상  
> 대상 IDE: Visual Studio Code, IntelliJ IDEA  
> 이 문서는 현재 PC에서 실제로 사용 중인 Ollama와 Continue 구성을 기준으로 작성했다.

---

## 1. 전체 구조 이해하기

```text
로컬 LLM 모델
  └─ Ollama가 모델을 다운로드하고 실행
       └─ http://localhost:11434 로 API 제공
            └─ Continue가 API에 연결
                 ├─ VS Code에서 채팅·Agent·편집·자동 완성
                 └─ IntelliJ에서 채팅·Agent·편집·자동 완성
```

- **Ollama**: 로컬 PC에서 LLM을 내려받고 실행하는 프로그램이다.
- **Continue**: IDE에서 Ollama 모델을 사용하도록 연결하는 확장 프로그램이다.
- 모델 파일과 대화는 기본적으로 로컬 PC에 남는다. 단, 별도의 클라우드 모델/API를 설정하면 해당 요청은 외부로 전송된다.
- Ollama를 기본 방식으로 설치할 때는 Docker나 WSL이 필요하지 않다.

### 이 PC에서 확인된 모델

| 모델 | Ollama 저장 크기 | 권장 역할 |
|---|---:|---|
| `qwen2.5-coder:14b` | 약 9 GB | 빠른 코드 자동 완성 |
| `qwen3.6:27b` | 약 17 GB | 복잡한 코딩 Agent, 저장소 분석, 수정 |
| `gemma4:31b` | 약 19 GB | 한국어 설명, 일반 작업, 이미지·화면 분석 |

이 PC처럼 VRAM 16 GB, 시스템 RAM 64 GB 환경에서는 14B 모델이 가장 부드럽다. 17~19 GB 모델도 실행할 수 있지만 일부가 시스템 RAM으로 넘어가므로 느려질 수 있다.

---

## 2. Windows에 Ollama 설치

### 2.1 설치 프로그램으로 설치

1. [Ollama Windows 다운로드](https://ollama.com/download/windows)에 접속한다.
2. `Download for Windows`를 눌러 설치 파일을 받는다.
3. 설치 파일을 실행한다.
4. 설치가 끝나면 열려 있던 PowerShell과 IDE를 모두 닫고 다시 연다.
5. PowerShell에서 확인한다.

```powershell
ollama --version
```

정상 예시:

```text
ollama version is 0.32.1
```

Ollama Windows 앱은 일반적으로 로그인 후 백그라운드에서 실행되며 API 주소는 다음과 같다.

```text
http://localhost:11434
```

### 2.2 서버 동작 확인

PowerShell 5.1에서는 `Invoke-WebRequest`가 웹 페이지 스크립트 경고를 표시할 수 있다. API 확인에는 다음 명령이 더 편하다.

```powershell
Invoke-RestMethod http://localhost:11434/api/tags
```

또는 간단히 다음 주소를 웹 브라우저에서 연다.

```text
http://localhost:11434
```

`Ollama is running`이 보이면 서버가 정상이다.

서버가 실행되지 않았을 때는 다음을 실행한다.

```powershell
ollama serve
```

이미 백그라운드 앱이 서버를 실행 중이라면 포트 사용 중 메시지가 나올 수 있다. 이 경우 새 서버를 또 띄울 필요가 없다.

### 2.3 `ollama` 명령을 찾지 못할 때

1. PowerShell과 IDE를 완전히 닫고 다시 연다.
2. 아래 명령으로 실행 파일을 찾는다.

```powershell
Get-Command ollama
where.exe ollama
```

3. 계속 찾지 못하면 Ollama를 다시 설치한다.
4. 일반적인 설치 위치가 사용자 PATH에 들어 있는지 확인한다.

```text
C:\Users\<사용자명>\AppData\Local\Programs\Ollama
```

### 2.4 Windows 모델 저장 위치

기본 모델 위치:

```text
%USERPROFILE%\.ollama\models
```

다른 드라이브에 저장하려면 Windows 사용자 환경 변수 `OLLAMA_MODELS`를 원하는 폴더로 지정하고 Ollama 앱을 재시작한다.

예:

```powershell
[Environment]::SetEnvironmentVariable(
  "OLLAMA_MODELS",
  "D:\OllamaModels",
  "User"
)
```

그 후 Ollama와 PowerShell을 재시작한다. 기존 모델을 옮길 때는 Ollama를 종료한 상태에서 처리한다.

---

## 3. macOS에 Ollama 설치

### 3.1 앱 설치

1. [Ollama macOS 다운로드](https://ollama.com/download/mac)에 접속한다.
2. DMG 파일을 받는다.
3. `Ollama.app`을 `Applications` 폴더로 드래그한다.
4. 응용 프로그램에서 Ollama를 실행한다.
5. CLI 설치 또는 `/usr/local/bin` 심볼릭 링크 생성 안내가 뜨면 허용한다.
6. Terminal을 새로 열고 확인한다.

```bash
ollama --version
```

### 3.2 macOS 요구 사항과 가속

- 공식 문서 기준 macOS Sonoma 14 이상을 권장한다.
- Apple Silicon(M1/M2/M3/M4 계열)은 Metal GPU 가속을 사용할 수 있다.
- Intel Mac은 CPU 실행이 중심이어서 큰 모델은 매우 느릴 수 있다.

### 3.3 `ollama` 명령을 찾지 못할 때

```bash
which ollama
ls -l /usr/local/bin/ollama
```

명령 링크가 없으면 Ollama 앱을 다시 실행해 CLI 설치 안내를 진행한다. Apple Silicon 환경에서 Homebrew 경로만 사용하는 경우 현재 셸의 PATH도 확인한다.

```bash
echo $PATH
```

### 3.4 macOS 모델 저장 위치

기본 데이터와 모델은 사용자 홈의 다음 위치에 저장된다.

```text
~/.ollama
```

모델 저장 위치를 변경하려면 `OLLAMA_MODELS` 환경 변수를 설정한 뒤 Ollama 앱을 재시작한다.

---

## 4. Ollama 주요 명령어

### 4.1 자주 쓰는 명령어 표

| 목적 | 명령어 | 설명 |
|---|---|---|
| 버전 확인 | `ollama --version` | 설치 확인 |
| 모델 다운로드 | `ollama pull 모델명` | 모델을 미리 다운로드 |
| 모델 실행 | `ollama run 모델명` | 없으면 다운로드 후 대화 실행 |
| 모델 목록 | `ollama list` | 로컬에 설치된 모델 확인 |
| 실행 중 모델 | `ollama ps` | VRAM/RAM에 올라간 모델 확인 |
| 모델 정지 | `ollama stop 모델명` | 메모리에서 모델 내리기 |
| 모델 삭제 | `ollama rm 모델명` | 로컬 모델 파일 삭제 |
| 모델 복사 | `ollama cp 원본 새이름` | 모델을 다른 태그로 복사 |
| 모델 정보 | `ollama show 모델명` | 템플릿, 파라미터 등 확인 |
| 서버 실행 | `ollama serve` | Ollama API 서버 수동 실행 |
| 사용자 모델 생성 | `ollama create 이름 -f Modelfile` | Modelfile로 모델 생성 |
| 지원 도구 연결 | `ollama launch 대상` | 지원되는 앱/도구 연결 마법사 실행 |

### 4.2 현재 권장 모델 설치

```powershell
ollama pull qwen2.5-coder:14b
ollama pull qwen3.6:27b
ollama pull gemma4:31b
```

다운로드 후 확인:

```powershell
ollama list
```

### 4.3 모델 직접 실행

```powershell
ollama run qwen2.5-coder:14b
```

한 줄로 질문하고 종료:

```powershell
ollama run qwen2.5-coder:14b "Spring Boot 서비스 메서드 예제를 작성해줘"
```

이미지를 지원하는 모델에 이미지 경로 전달:

```powershell
ollama run gemma4:31b "C:\images\screen.png 이 화면의 오류를 분석해줘"
```

대화 중 종료:

```text
/bye
```

### 4.4 모델 삭제

```powershell
ollama rm qwen2.5-coder:7b
```

삭제 전후 확인:

```powershell
ollama list
```

모델을 지워도 Continue 설정에 해당 모델이 남아 있으면 IDE에는 이름이 표시될 수 있지만 실행은 실패한다. Continue 설정에서도 해당 모델 항목을 함께 제거한다.

### 4.5 큰 모델 전환 시 메모리 정리

```powershell
ollama ps
ollama stop gemma4:31b
ollama stop qwen3.6:27b
ollama stop qwen2.5-coder:14b
```

16 GB VRAM에서는 27B와 31B 모델을 동시에 유지하지 않는 것이 좋다.

### 4.6 모델 이름과 태그 읽는 법

예: `qwen2.5-coder:14b`

- `qwen2.5-coder`: 모델 계열
- `14b`: 약 140억 개 파라미터 규모
- 같은 모델이라도 `7b`, `14b`, `32b`처럼 크기가 다를 수 있다.
- Ollama 라이브러리의 모델은 보통 양자화되어 있어 원본 정밀도 모델보다 파일 크기가 작다.
- 태그가 정확해야 한다. `qwen3.6:27b`와 `qwen3-coder:30b`는 서로 다른 모델이다.

### 4.7 `ollama launch vscode`와 Continue의 차이

`ollama launch vscode`는 Ollama가 지원하는 VS Code 통합 설정을 시작하는 명령이다. Continue 자체를 설정하는 명령은 아니다.

Continue를 사용할 때는 다음 방식이 더 명확하다.

1. Ollama 앱을 실행한다.
2. Continue 확장을 설치한다.
3. `%USERPROFILE%\.continue\config.yaml`에서 `provider: ollama`와 모델명을 설정한다.

---

## 5. 업무별 로컬 LLM 추천

### 5.1 현재 설치 모델 중심 추천

| 업무 | 1순위 | 이유 | 주의점 |
|---|---|---|---|
| 타이핑 중 코드 자동 완성 | Qwen2.5-Coder 14B | FIM 코드 완성에 특화, Java·React 모두 무난 | Agent나 이미지 분석용은 아님 |
| 저장소 분석과 복잡한 코드 수정 | Qwen3.6 27B | 추론, 도구 사용, 코딩 품질이 좋음 | 14B보다 느리고 메모리 사용량이 큼 |
| 여러 파일을 읽는 코딩 Agent | Qwen3.6 27B | 도구 호출과 장기 작업에 적합 | 컨텍스트를 과도하게 넣으면 느려짐 |
| 한국어 설명·문서화 | Gemma4 31B 또는 Qwen3.6 27B | 한국어 표현과 일반 설명이 좋음 | 31B는 무거움 |
| 화면 캡처·이미지 분석 | Gemma4 31B 또는 Qwen3.6 27B | 이미지 입력 지원 | Continue와 모델 양쪽에서 이미지 기능 지원 필요 |
| Java/Spring 자동 완성 | Qwen2.5-Coder 14B | 메서드·DTO·스트림·테스트 패턴에 강함 | 프로젝트 스타일을 충분히 보여줘야 함 |
| React/TypeScript 자동 완성 | Qwen2.5-Coder 14B | JSX/TS/컴포넌트 완성에 적합 | 최신 라이브러리 API는 검증 필요 |
| 빠른 저사양 작업 | Qwen2.5-Coder 7B 또는 Qwen3.5 9B | 응답 지연이 짧음 | 복잡한 수정 정확도는 낮아질 수 있음 |
| 텍스트 전용 코드 Agent 실험 | Qwen3-Coder 30B | 코딩·도구 호출에 특화 | 이미지 입력 없음, 최종 품질은 작업별 비교 필요 |

### 5.2 모델별 성격

#### Qwen2.5-Coder 14B

- 코드 생성과 중간 삽입(Fill-in-the-Middle, FIM)에 특화되어 있다.
- 자동 완성은 지연 시간이 중요하므로 27B/31B보다 14B가 실용적이다.
- Java, Spring Boot, JavaScript, TypeScript, React에 모두 사용할 수 있다.
- 채팅도 가능하지만 복잡한 Agent 작업은 Qwen3.6 27B가 더 적합하다.
- 이미지 입력 기능은 없다.

#### Qwen3.6 27B

- 복잡한 저장소 분석, 버그 원인 추적, 여러 파일 수정, 도구 호출에 적합하다.
- 코드 Agent의 최종 분석·수정 품질을 우선할 때 현재 구성의 1순위다.
- 텍스트와 이미지 입력을 활용할 수 있다.
- 자동 완성으로 사용하면 품질은 높아도 대기 시간이 길어져 타이핑 흐름을 방해할 수 있다.

#### Gemma4 31B

- 한국어를 포함한 다국어 설명과 일반 대화, 이미지·화면 이해에 유용하다.
- 스크린샷의 오류 메시지를 설명하거나 문서화하는 용도로 좋다.
- 코드 Agent만 비교하면 Qwen3.6보다 우선순위가 낮다.
- 모델이 커서 16 GB VRAM 환경에서는 일부 RAM 오프로딩이 발생할 수 있다.

#### Qwen3-Coder 30B

- 코드 생성, 저장소 작업, 도구 호출에 특화된 Agent 모델이다.
- MoE 구조로 전체 파라미터보다 한 번에 활성화되는 파라미터가 적어 특정 환경에서는 체감 속도가 좋을 수 있다.
- 이미지·화면 분석은 지원하지 않는 텍스트 전용 모델이다.
- 범용 추론과 이미지 입력까지 포함하면 Qwen3.6 27B가 편하고, 텍스트 기반 코딩 도구 작업만 집중 비교할 때 Qwen3-Coder 30B를 시험할 가치가 있다.

### 5.3 모델 크기 선택 기준

| 사용 가능한 VRAM | 자동 완성 추천 | Agent 추천 |
|---:|---|---|
| 8 GB | 1.5B~7B | 7B~9B, 제한적 |
| 12 GB | 7B | 9B~14B |
| 16 GB | 7B~14B | 14B 또는 RAM 오프로딩을 허용한 27B/31B |
| 24 GB 이상 | 14B | 27B~32B |

모델 크기만으로 품질이 결정되는 것은 아니다. 자동 완성은 **전용 FIM 학습 여부와 지연 시간**이 중요하며, Agent는 **도구 호출, 추론, 긴 컨텍스트 처리**가 중요하다.

### 5.4 사양이 낮은 PC용 Qwen2.5-Coder 7B

`qwen2.5-coder:7b`는 14B의 바로 아래 단계로, 자동 완성 속도와 메모리 절약을 우선할 때 적합하다. Ollama 기본 배포 모델의 저장 크기는 약 4.7 GB이며 최대 32K 컨텍스트를 지원한다. 실제 메모리 사용량은 컨텍스트 길이와 GPU 오프로딩 정도에 따라 달라진다.

| 구분 | Qwen2.5-Coder 7B | Qwen2.5-Coder 14B |
|---|---|---|
| Ollama 저장 크기 | 약 4.7 GB | 약 9.0 GB |
| 권장 VRAM | 6~8 GB 이상 | 12~16 GB 이상 |
| 권장 시스템 RAM | 16 GB 이상, 32 GB 권장 | 32 GB 이상 |
| 자동 완성 속도 | 더 빠름 | 상대적으로 느림 |
| 복잡한 다중 라인 완성 | 보통 | 더 정확함 |
| 권장 대상 | 노트북, 저사양 GPU, 빠른 반응 우선 | 품질 우선, Java·Spring·React 대형 프로젝트 |

선택 기준:

- VRAM이 8 GB 이하이면 7B를 우선한다.
- VRAM이 12 GB라도 IDE와 다른 프로그램을 많이 실행한다면 7B가 안정적이다.
- VRAM이 16 GB 이상이고 제안 품질이 중요하면 14B를 우선한다.
- GPU가 없어도 7B를 CPU로 실행할 수 있지만 자동 완성 응답이 늦어질 수 있다.
- 작은 모델이라도 자동 완성에는 전체 32K 컨텍스트보다 2K~4K 정도의 짧은 문맥이 빠르고 실용적이다.

설치:

```powershell
ollama pull qwen2.5-coder:7b
```

실행 확인:

```powershell
ollama run qwen2.5-coder:7b "Java 메서드 자동 완성 예제를 작성해줘"
```

14B 대신 7B를 자동 완성에 사용할 때는 기존 Autocomplete 모델 항목을 다음과 같이 교체한다.

```yaml
  - name: Qwen 2.5 Coder 7B Autocomplete
    provider: ollama
    model: qwen2.5-coder:7b
    apiBase: http://localhost:11434
    roles:
      - autocomplete
    defaultCompletionOptions:
      contextLength: 4096
      maxTokens: 128
      temperature: 0.1
      keepAlive: 1800
    autocompleteOptions:
      maxPromptTokens: 768
      debounceDelay: 250
      maxSuffixPercentage: 0.2
      prefixPercentage: 0.3
      onlyMyCode: true
      useCache: true
      useImports: true
      useRecentlyEdited: true
      useRecentlyOpened: true
    requestOptions:
      timeout: 600
```

7B와 14B를 모두 설치해 비교할 수는 있지만, Continue의 Autocomplete 선택에는 한 모델만 지정한다. 7B에서 품질이 부족할 때 14B로 바꾸고, 14B가 느릴 때 7B로 되돌리는 방식이 가장 간단하다.

---

## 6. VS Code에 Continue 설치 및 연동

### 6.1 확장 설치

공식 배포 페이지: [Continue - Visual Studio Marketplace](https://marketplace.visualstudio.com/items?itemName=Continue.continue)

1. VS Code를 실행한다.
2. 왼쪽 `Extensions` 아이콘을 누른다.
3. `Continue`를 검색한다.
4. 게시자가 `Continue`이고 확장 식별자가 `continue.continue`인지 확인한다.
5. `Install`을 누른다.
6. 설치 후 VS Code를 다시 로드한다.

CLI로 설치하려면 VS Code의 `code` 명령이 PATH에 있어야 한다.

```powershell
code --install-extension continue.continue
```

`code`를 찾지 못하면:

- Windows: VS Code 설치 프로그램에서 `Add to PATH`를 활성화한 뒤 재설치하거나 새 터미널을 연다.
- macOS: Command Palette에서 `Shell Command: Install 'code' command in PATH`를 실행한다.

### 6.2 Continue 열기

- 왼쪽 Activity Bar의 Continue 육각형 아이콘을 누른다.
- 아이콘이 보이지 않으면 Activity Bar를 마우스 오른쪽 클릭하고 `Continue`를 체크한다.
- `View` → `Open View...`에서 `Continue`를 검색할 수도 있다.

### 6.3 Continue만 오른쪽으로 이동

Primary Side Bar 전체를 옮기지 않고 Continue만 옮기려면:

1. `View` → `Appearance` → `Secondary Side Bar`를 표시한다.
2. Continue 뷰의 제목 또는 아이콘을 Secondary Side Bar 쪽으로 드래그한다.
3. Explorer는 왼쪽, Continue는 오른쪽에 둘 수 있다.

Explorer를 누른 뒤 Continue가 사라진 것처럼 보이면 삭제된 것이 아니라 다른 뷰가 선택된 상태다. Activity Bar에서 Continue 아이콘을 다시 누른다.

### 6.4 VS Code Restricted Mode

신뢰할 수 있는 본인 프로젝트라면 상단의 `Trust this folder`를 선택해야 Continue가 파일을 읽고 도구를 사용하는 데 제한이 적다. 출처를 모르는 프로젝트는 신뢰하지 않는다.

---

## 7. IntelliJ IDEA에 Continue 설치 및 연동

### 7.1 플러그인 설치

공식 배포 페이지: [Continue - JetBrains Marketplace](https://plugins.jetbrains.com/plugin/22707-continue)

1. IntelliJ IDEA를 연다.
2. `File` → `Settings` → `Plugins` → `Marketplace`로 이동한다.
3. `Continue`를 검색한다.
4. 공식 Continue 플러그인을 설치한다.
5. IntelliJ를 재시작한다.
6. 프로젝트를 열고 오른쪽 Tool Window의 `Continue`를 확인한다.

검색 결과에 공식 Continue가 없을 때:

- IntelliJ와 Marketplace 연결 상태를 확인한다.
- IDE 버전과 플러그인 호환성을 확인한다.
- Continue 공식 배포 페이지에서 JetBrains용 ZIP을 받아 `Install Plugin from Disk...`로 설치할 수 있다.
- 현재 Continue 공식 문서는 신규 JetBrains 사용자에게 CLI 사용도 권장한다. 플러그인은 사용할 수 있지만 VS Code 확장과 기능·UI가 다를 수 있다.

### 7.2 자동 완성 켜기

1. `File` → `Settings` → `Tools` → `Continue`로 이동한다.
2. `Enable Tab Autocomplete`를 체크한다.
3. `Display Editor Tooltip`은 필요에 따라 켠다.
4. 설정을 적용하고 편집기로 돌아간다.

옵션 의미:

- **Enable Tab Autocomplete**: 타이핑 중 Continue가 회색 유령 텍스트(ghost text)로 코드를 제안한다.
- **Display Editor Tooltip**: 선택 코드 주변에 Chat/Edit 같은 Continue 바로가기를 보여준다.
- **Show IDE completions side-by-side**: IntelliJ 기본 자동 완성과 Continue 제안을 함께 표시하도록 시도한다. 화면이 복잡하면 끈다.

### 7.3 IntelliJ 기본 자동 완성과 Continue 구분

| 표시 형태 | 제공자 | 사용 방법 |
|---|---|---|
| 클래스·메서드 목록 팝업 | IntelliJ | 방향키 선택 후 Enter/Tab |
| 코드 오른쪽의 회색 유령 텍스트 | Continue | Tab으로 수락, Esc로 거절 |
| `Chat` / `Edit` 작은 버튼 | Continue Editor Tooltip | Chat 또는 Edit 작업 호출 |

Continue 자동 완성은 아무 위치에서 항상 나타나는 것이 아니다. 다음 조건일 때 잘 나타난다.

- 메서드 이름과 반환 타입으로 다음 코드가 어느 정도 예상될 때
- 기존 코드 앞뒤 문맥이 충분할 때
- 잠시 타이핑을 멈췄을 때
- IntelliJ 기본 제안 팝업이 닫혀 있을 때

예를 들어 `return`만 입력하면 가능한 코드가 너무 많아 제안이 없을 수 있지만, `return Book.`까지 입력하면 문맥이 좁아져 제안이 잘 나온다.

---

## 8. Continue 설정 파일

### 8.1 위치

Windows:

```text
C:\Users\<사용자명>\.continue\config.yaml
```

현재 PC:

```text
C:\Users\USER\.continue\config.yaml
```

macOS/Linux:

```text
~/.continue/config.yaml
```

VS Code와 IntelliJ가 같은 OS 사용자 계정에서 실행되면 일반적으로 같은 로컬 설정 파일을 사용한다.

Continue 설정 화면에서 `Main Config` 또는 톱니바퀴를 누르면 파일을 열 수 있다. 편집 후 저장하고 Continue의 새로고침 버튼을 누르거나 IDE를 재시작한다.

### 8.2 이 PC에 권장하는 전체 설정

아래 설정은 세 모델의 역할을 분리한다.

- Qwen3.6 27B: 채팅·Agent·편집·적용
- Gemma4 31B: 한국어·일반·이미지 작업용 선택 모델
- Qwen2.5-Coder 14B: 자동 완성 전용

```yaml
name: Local Coding Models
version: 1.0.0
schema: v1

models:
  - name: Qwen 3.6 27B Agent
    provider: ollama
    model: qwen3.6:27b
    apiBase: http://localhost:11434
    roles:
      - chat
      - edit
      - apply
    capabilities:
      - tool_use
      - image_input
    defaultCompletionOptions:
      contextLength: 16384
      maxTokens: 2048
      temperature: 0.1
      keepAlive: 300
      reasoning: true
    requestOptions:
      timeout: 600

  - name: Gemma 4 31B Vision
    provider: ollama
    model: gemma4:31b
    apiBase: http://localhost:11434
    roles:
      - chat
      - edit
      - apply
    capabilities:
      - tool_use
      - image_input
    defaultCompletionOptions:
      contextLength: 16384
      maxTokens: 2048
      temperature: 0.2
      keepAlive: 300
    requestOptions:
      timeout: 600

  - name: Qwen 2.5 Coder 14B Autocomplete
    provider: ollama
    model: qwen2.5-coder:14b
    apiBase: http://localhost:11434
    roles:
      - autocomplete
    defaultCompletionOptions:
      contextLength: 4096
      maxTokens: 128
      temperature: 0.1
      keepAlive: 1800
    autocompleteOptions:
      maxPromptTokens: 1024
      debounceDelay: 350
      maxSuffixPercentage: 0.2
      prefixPercentage: 0.3
      onlyMyCode: true
      useCache: true
      useImports: true
      useRecentlyEdited: true
      useRecentlyOpened: true
    requestOptions:
      timeout: 600

rules:
  - 설명은 한국어로 작성한다
  - 코드와 변수 이름은 영어로 작성한다
  - 기존 프로젝트 구조와 코드 스타일을 유지한다
  - 파일을 수정하기 전에 변경 내용을 간단히 설명한다
  - 기능을 변경할 때 관련 테스트도 확인한다
  - 확실하지 않은 내용은 임의로 결정하지 말고 먼저 설명한다
  - 검색이 실패하면 같은 검색을 반복하지 말고 파일명, 코드 문자열, 프로젝트 구조 순서로 범위를 바꿔 확인한다
```

> 중요: `qwen2.5-coder:14b` 자동 완성 모델에는 `tool_use`와 `image_input`을 넣지 않는다. 이 모델의 역할은 빠른 텍스트 코드 완성이다.

---

## 9. Continue 설정 옵션 상세 설명

### 9.1 최상위 옵션

| 옵션 | 예 | 의미 |
|---|---|---|
| `name` | `Local Coding Models` | 설정 묶음의 표시 이름 |
| `version` | `1.0.0` | 사용자 설정 버전 |
| `schema` | `v1` | Continue YAML 스키마 버전 |
| `models` | 모델 배열 | 사용할 모델 목록 |
| `rules` | 문자열 배열 | 모든 대화와 Agent 작업에 적용할 기본 지침 |

### 9.2 모델 연결 옵션

| 옵션 | 예 | 의미 |
|---|---|---|
| `name` | `Qwen 3.6 27B Agent` | Continue 화면에 표시되는 이름 |
| `provider` | `ollama` | 모델을 제공하는 백엔드 |
| `model` | `qwen3.6:27b` | `ollama list`에 표시되는 정확한 모델 태그 |
| `apiBase` | `http://localhost:11434` | Ollama API 기본 주소 |

`apiBase`는 같은 PC에서 실행할 때 `localhost`를 쓴다. 다른 PC의 Ollama에 연결할 때는 서버 방화벽, 바인딩 주소, 보안을 별도로 설정해야 한다.

### 9.3 `roles`

| 역할 | 기능 | 권장 모델 |
|---|---|---|
| `chat` | 질문, 설명, 코드 분석, Agent 대화 | Qwen3.6, Gemma4 |
| `edit` | 선택한 코드 또는 파일을 지시에 따라 수정 | Qwen3.6, Gemma4 |
| `apply` | 채팅에서 제안한 변경 내용을 실제 파일에 적용 | Qwen3.6, Gemma4 |
| `autocomplete` | 타이핑 중 인라인 코드 완성 | Qwen2.5-Coder 14B |
| `embed` | 코드 검색용 임베딩 | 전용 임베딩 모델 |
| `rerank` | 검색 결과 재정렬 | 전용 Reranker 모델 |
| `summarize` | 대화·변경 요약 | 작은 채팅 모델도 가능 |

예를 들어 다음 설정은 채팅, 코드 편집, 제안 적용만 가능하게 등록한다.

```yaml
roles:
  - chat
  - edit
  - apply
```

이 모델은 자동 완성 선택 목록에는 나타나지 않는다. 자동 완성으로 쓰려면 `autocomplete` 역할이 필요하다.

### 9.4 `capabilities`

| 옵션 | 의미 | 주의점 |
|---|---|---|
| `tool_use` | 파일 읽기, 검색, 터미널 등 Agent 도구 호출 | 모델 자체가 도구 호출을 지원해야 함 |
| `image_input` | 이미지·스크린샷을 프롬프트에 첨부 | 모델 자체가 비전을 지원해야 함 |

설정에 capability를 적는다고 모델에 없던 기능이 새로 생기지는 않는다. 모델이 실제로 지원할 때만 선언한다.

### 9.5 `defaultCompletionOptions`

#### `contextLength`

한 요청에서 모델이 볼 수 있는 입력과 출력 전체 토큰 한도다.

```yaml
contextLength: 16384
```

- 너무 작으면 `Message exceeds context limit` 오류가 난다.
- 크게 설정할수록 항상 좋아지는 것은 아니다. 메모리 사용과 첫 응답 시간이 증가한다.
- Agent에는 16K부터 시작하고 필요할 때 32K로 올리는 것이 안전하다.
- 자동 완성은 4K 정도로도 충분한 경우가 많다.
- Continue 설정값이 모델/Ollama가 실제 지원하는 컨텍스트보다 커서는 안 된다.

#### `maxTokens`

한 번에 생성할 최대 출력 토큰 수다.

```yaml
maxTokens: 2048
```

- Agent/채팅: 1024~4096
- 자동 완성: 64~256
- 자동 완성에서 2048처럼 크게 잡으면 불필요하게 긴 제안과 지연이 생길 수 있다.

#### `temperature`

출력의 무작위성이다.

```yaml
temperature: 0.1
```

- `0.0~0.2`: 코드, 수정, 자동 완성에 권장
- `0.5 이상`: 아이디어 생성에는 다양하지만 코드 일관성이 낮아질 수 있음

#### `keepAlive`

응답 후 Ollama 모델을 메모리(VRAM/RAM)에 유지하는 시간이다. Continue 설정에서는 초 단위로 사용한다.

```yaml
keepAlive: 300
```

- `300`: 5분
- `1800`: 30분
- 자동 완성 모델은 계속 쓰므로 1800초가 유리하다.
- 27B/31B 모델은 VRAM을 많이 차지하므로 300초가 적당하다.
- keepAlive는 응답 생성 시간이나 컨텍스트 크기를 늘리는 옵션이 아니다.

Ollama API 자체는 `5m`, `300`, `-1`, `0` 같은 값도 지원한다. Continue YAML에서는 현재 스키마에 맞춰 숫자 초 단위를 사용하는 것이 안전하다.

#### `reasoning`

지원 모델에서 추론 모드를 제어한다.

```yaml
reasoning: true
```

복잡한 Agent 작업 품질은 좋아질 수 있지만 응답이 느려지고 토큰 사용이 증가할 수 있다. 모델이 지원하지 않으면 제거한다.

#### 기타 옵션

| 옵션 | 의미 |
|---|---|
| `topP`, `topK` | 다음 토큰 후보 범위 조절 |
| `presencePenalty` | 이미 나온 주제를 반복하지 않도록 조절 |
| `frequencyPenalty` | 같은 표현 반복 억제 |
| `stop` | 생성 중단 문자열 목록 |
| `numThreads` | Ollama CPU 스레드 수 |
| `numGpu` | GPU 사용 계층 수 제어 |
| `useMmap` | 모델 파일 메모리 매핑 사용 여부 |
| `reasoningBudgetTokens` | 지원 모델의 추론 토큰 예산 |

특별한 이유가 없다면 Ollama가 하드웨어를 자동 선택하도록 `numThreads`, `numGpu`, `useMmap`은 생략한다.

### 9.6 `autocompleteOptions`

| 옵션 | 권장값 | 의미 |
|---|---:|---|
| `maxPromptTokens` | `1024` | 자동 완성 요청에 넣을 최대 문맥 |
| `debounceDelay` | `350` | 타이핑을 멈춘 뒤 요청까지 기다릴 밀리초 |
| `modelTimeout` | 환경별 | 자동 완성 모델 응답 제한 시간 |
| `maxSuffixPercentage` | `0.2` | 커서 뒤 코드에 배분할 최대 비율 |
| `prefixPercentage` | `0.3` | 커서 앞 코드에 배분할 목표 비율 |
| `onlyMyCode` | `true` | 가능하면 사용자가 작성한 코드 중심 문맥 사용 |
| `useCache` | `true` | 이전 자동 완성 결과 캐시 활용 |
| `useImports` | `true` | import 문을 문맥에 포함 |
| `useRecentlyEdited` | `true` | 최근 수정 파일을 문맥으로 사용 |
| `useRecentlyOpened` | `true` | 최근 연 파일을 문맥으로 사용 |
| `disable` | `false` | 자동 완성 기능 비활성화 여부 |
| `template` | 버전별 | 사용자 자동 완성 프롬프트 템플릿 |
| `transform` | 버전별 | 출력 후처리 방식 |

튜닝 기준:

- 제안이 너무 늦다: `debounceDelay`를 250~300으로 낮춘다.
- 타이핑 중 요청이 너무 잦다: 400~600으로 올린다.
- 품질이 낮다: `maxPromptTokens`를 1536~2048로 올려본다.
- 메모리가 부족하거나 느리다: `maxPromptTokens`를 512~1024로 줄인다.

`multilineCompletions`는 일부 Continue 버전에서 사용하던 옵션이다. 현재 설치 버전의 YAML 스키마가 허용할 때만 사용한다.

```yaml
multilineCompletions: auto
```

설정 파일에 경고가 생기면 제거한다.

### 9.7 `requestOptions`

```yaml
requestOptions:
  timeout: 600
```

| 옵션 | 의미 |
|---|---|
| `timeout` | 요청 제한 시간. 현재 스키마에서는 초 단위 사용을 권장 |
| `verifySsl` | HTTPS 인증서 검증 여부 |
| `caBundlePath` | 사내 CA 인증서 번들 경로 |
| `proxy` | HTTP 프록시 주소 |
| `headers` | 요청에 추가할 HTTP 헤더 |
| `extraBodyProperties` | API 요청 본문에 추가할 속성 |
| `noProxy` | 프록시를 사용하지 않을 주소 |
| `clientCertificate` | 클라이언트 인증서 설정 |

로컬 Ollama에는 대개 `timeout`만 필요하다. 과거 일부 Continue 어댑터는 timeout 단위 처리가 달랐으므로, 최신 확장과 현재 YAML 스키마를 기준으로 설정한다.

### 9.8 `rules`

모델의 공통 행동 규칙이다.

좋은 규칙:

- 짧고 구체적이다.
- 결과를 검증할 방법을 포함한다.
- 프로젝트의 실제 코딩 관례를 설명한다.

나쁜 규칙:

- 서로 충돌한다.
- 모든 상황을 지나치게 상세하게 강제한다.
- 모델이 확인할 수 없는 사실을 가정한다.

규칙이 많아질수록 모든 요청에서 컨텍스트를 차지한다. 핵심 규칙 5~10개 정도로 유지한다.

---

## 10. Continue 기능별 사용법

### 10.1 Autocomplete

1. 자동 완성 모델을 `roles: [autocomplete]`로 등록한다.
2. Continue의 Models 화면에서 Autocomplete 모델로 선택한다.
3. IntelliJ 설정에서 `Enable Tab Autocomplete`를 켠다.
4. 코드를 입력하다 잠시 멈춘다.
5. 회색 제안이 나타나면 `Tab`으로 수락하고 `Esc`로 거절한다.

VS Code에서는 기본적으로 `Ctrl+Alt+Space`로 수동 제안을 요청할 수 있다. 단축키 충돌 시 Keyboard Shortcuts에서 `Continue: Force Autocomplete`를 검색한다.

### 10.2 Chat

- 코드 설명, 오류 원인 질문, 설계 상담에 사용한다.
- VS Code 기본 단축키는 `Ctrl+L`, JetBrains 계열은 보통 `Ctrl+J`다.
- `@`로 파일이나 코드 문맥을 추가할 수 있다.
- 이미지 지원 모델을 선택하면 스크린샷을 첨부할 수 있다.

### 10.3 Edit

- 선택한 코드를 지시대로 바꾸는 데 사용한다.
- 일반적인 단축키는 `Ctrl+I`다.
- 변경 범위가 작고 명확할 때 Chat보다 빠르다.

### 10.4 Agent

- 파일 검색, 읽기, 터미널 실행, 여러 파일 수정 같은 연속 작업에 사용한다.
- `tool_use`를 지원하는 모델이 필요하다.
- 명령 실행 또는 파일 수정 전 승인 화면이 뜨면 내용을 확인한 후 수락한다.
- `Pending action`은 Continue가 터미널 명령 등을 실행하기 전에 사용자 승인을 기다리는 상태다.

### 10.5 Plan

- 먼저 읽기와 분석만 하고 구현 계획을 만들 때 사용한다.
- 큰 변경은 Plan으로 범위와 위험을 확인한 뒤 Agent로 구현하는 것이 좋다.

### 10.6 Apply

- 채팅 답변의 코드 변경을 실제 파일에 반영할 때 사용한다.
- `apply` 역할 모델이 필요하다.
- 적용 후 diff를 검토하고 테스트한다.

---

## 11. 자주 발생하는 문제와 해결

### 11.1 `Message exceeds context limit`

원인:

- 긴 대화 기록
- 큰 파일 여러 개 첨부
- Agent가 많은 검색 결과를 누적
- 설정의 `contextLength`가 너무 작음

해결 순서:

1. `Compact conversation`을 누른다.
2. 새 대화를 시작한다.
3. 꼭 필요한 파일만 첨부한다.
4. 프로젝트 전체 대신 오류와 관련된 모듈만 지정한다.
5. `contextLength`를 8192에서 16384로 올린다.
6. 메모리가 충분할 때만 32768을 시험한다.

`keepAlive`를 늘려도 이 오류는 해결되지 않는다.

### 11.2 Agent가 중간에 멈춤

확인:

```powershell
ollama ps
```

대응:

1. 다른 큰 모델을 `ollama stop`으로 내린다.
2. `requestOptions.timeout`을 600으로 설정한다.
3. 새 대화에서 작업 범위를 좁혀 다시 요청한다.
4. Agent가 동일한 검색을 반복하지 않도록 rules에 검색 전략을 넣는다.
5. Ollama 로그와 Continue 로그를 확인한다.
6. 31B가 너무 느리면 Qwen3.6 27B 또는 더 작은 모델로 바꾼다.

검색 결과가 없다는 것은 반드시 모델이 멈췄다는 뜻은 아니다. 검색어가 실제 클래스 선언과 맞지 않았을 수 있다.

### 11.3 `JAVA_HOME이 설정되지 않아 빌드를 실행할 수 없습니다`

IntelliJ의 프로젝트 JDK와 터미널 환경 변수는 별개일 수 있다.

PowerShell 확인:

```powershell
java -version
$env:JAVA_HOME
```

현재 창에서 임시 설정:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

영구 설정은 실제 설치된 JDK 경로를 확인한 뒤 Windows 환경 변수에서 지정한다. IntelliJ를 재시작해야 반영된다.

### 11.4 자동 완성이 나타나지 않음

체크리스트:

```powershell
ollama list
ollama ps
```

- Continue Models 화면의 Autocomplete가 `Qwen 2.5 Coder 14B Autocomplete`인지 확인한다.
- IntelliJ의 `Enable Tab Autocomplete`가 켜져 있는지 확인한다.
- `roles`에 `autocomplete`가 있는지 확인한다.
- IntelliJ 기본 코드 완성 팝업을 Esc로 닫고 0.5~1초 기다린다.
- 빈 줄에 `return`만 쓰기보다 반환 타입과 메서드 문맥을 제공한다.
- Ollama API가 `localhost:11434`에서 응답하는지 확인한다.

### 11.5 한글이 네모 또는 깨진 문자로 표시됨

터미널에서 Ollama가 한글을 정상 출력한다면 모델 문제보다 IntelliJ 글꼴 문제일 가능성이 높다.

IntelliJ:

1. `File` → `Settings` → `Editor` → `Font`
2. 한글 글리프를 포함한 글꼴을 선택한다.
3. Windows에서는 `Malgun Gothic`, `D2Coding`, `Noto Sans Mono CJK KR` 등을 시험한다.
4. `File` → `Settings` → `Editor` → `File Encodings`에서 Global/Project Encoding을 UTF-8로 맞춘다.

파일 인코딩 확인도 필요하다. Java 소스와 Continue 설정 YAML은 UTF-8로 저장한다.

PowerShell 5.1에서 UTF-8 YAML을 읽을 때는 다음처럼 인코딩을 명시한다.

```powershell
Get-Content "$env:USERPROFILE\.continue\config.yaml" -Encoding UTF8
```

### 11.6 Continue 왼쪽에 API Key 입력 화면이 나타남

그 화면은 OpenAI, Anthropic, Gemini 같은 클라우드 제공자 연결 화면이다. 로컬 Ollama만 사용할 때는 API 키가 필요하지 않다.

- 창의 `X`를 눌러 닫는다.
- Local 탭이 있다면 로컬 제공자를 선택한다.
- YAML의 `provider: ollama` 설정을 사용한다.

### 11.7 이미지 버튼이 있어도 분석하지 못함

다음 세 조건이 모두 필요하다.

1. 모델이 실제로 이미지 입력을 지원한다.
2. Continue 설정에 `image_input` capability가 있다.
3. 현재 선택된 Chat/Agent 모델이 해당 이미지 모델이다.

Qwen2.5-Coder 14B와 Qwen3-Coder 30B는 이미지 모델이 아니다.

### 11.8 Continue 설정 오류

- 들여쓰기는 탭 대신 공백을 사용한다.
- `roles`, `capabilities`, `rules`의 하위 항목 들여쓰기를 맞춘다.
- 모델 태그가 `ollama list`와 정확히 일치하는지 확인한다.
- 저장 후 Continue 새로고침 또는 IDE 재시작을 한다.
- 설정의 빨간/노란 밑줄을 확인한다.

### 11.9 Docker Desktop 가상화 오류와 Ollama

Docker Desktop의 `Virtualization support not detected`는 BIOS/UEFI 가상화, Hyper-V 또는 WSL2 문제다. Windows 네이티브 Ollama와 Continue 연동에는 Docker가 필요하지 않으므로 두 문제를 분리해서 처리한다.

---

## 12. 성능 최적화 권장값

### 12.1 자동 완성 품질 우선

```yaml
defaultCompletionOptions:
  contextLength: 4096
  maxTokens: 128
  temperature: 0.1
  keepAlive: 1800
autocompleteOptions:
  maxPromptTokens: 1024
  debounceDelay: 350
  onlyMyCode: true
  useCache: true
  useImports: true
  useRecentlyEdited: true
  useRecentlyOpened: true
```

### 12.2 Agent 안정성 우선

```yaml
defaultCompletionOptions:
  contextLength: 16384
  maxTokens: 2048
  temperature: 0.1
  keepAlive: 300
requestOptions:
  timeout: 600
```

### 12.3 메모리 관리 운영 방식

1. 평소에는 Qwen2.5-Coder 14B를 자동 완성용으로 유지한다.
2. 복잡한 분석 때만 Qwen3.6 27B를 사용한다.
3. 이미지 분석이 필요할 때 Gemma4 31B를 선택한다.
4. 큰 작업이 끝나면 사용하지 않는 모델을 내린다.

```powershell
ollama ps
ollama stop qwen3.6:27b
ollama stop gemma4:31b
```

---

## 13. 설치·연동 최종 체크리스트

```powershell
ollama --version
ollama list
Invoke-RestMethod http://localhost:11434/api/tags
```

- [ ] Ollama 명령이 정상 실행된다.
- [ ] `qwen2.5-coder:14b`, `qwen3.6:27b`, `gemma4:31b`가 목록에 있다.
- [ ] `http://localhost:11434`가 응답한다.
- [ ] VS Code 또는 IntelliJ에 Continue가 설치되어 있다.
- [ ] Continue의 `config.yaml`이 UTF-8로 저장되어 있다.
- [ ] Chat 모델은 Qwen3.6 27B 또는 Gemma4 31B로 선택된다.
- [ ] Autocomplete 모델은 Qwen2.5-Coder 14B로 선택된다.
- [ ] IntelliJ의 `Enable Tab Autocomplete`가 켜져 있다.
- [ ] 큰 모델 여러 개가 동시에 로드되지 않았다.
- [ ] Agent 실행 전 터미널 명령과 파일 diff를 검토한다.

---

## 14. 공식 참고 문서

### Ollama

- [Windows 설치](https://docs.ollama.com/windows)
- [macOS 설치](https://docs.ollama.com/macos)
- [Qwen2.5-Coder 모델 크기와 태그](https://ollama.com/library/qwen2.5-coder)
- [CLI 명령어](https://docs.ollama.com/cli)
- [Quickstart](https://docs.ollama.com/quickstart)
- [FAQ와 keep-alive·모델 경로](https://docs.ollama.com/faq)
- [Generate API](https://docs.ollama.com/api/generate)
- [Chat API](https://docs.ollama.com/api/chat)
- [Modelfile](https://docs.ollama.com/modelfile)

### Continue

- [Continue 공식 설치·배포 안내](https://docs.continue.dev/getting-started/install)
- [VS Code 공식 확장 배포 페이지](https://marketplace.visualstudio.com/items?itemName=Continue.continue)
- [JetBrains 공식 플러그인 배포 페이지](https://plugins.jetbrains.com/plugin/22707-continue)
- [Continue 설치](https://docs.continue.dev/getting-started/install)
- [IDE 확장 Quick Start](https://docs.continue.dev/ide-extensions/quick-start)
- [YAML 설정 레퍼런스](https://docs.continue.dev/reference)
- [YAML 설정 파일 위치와 마이그레이션](https://docs.continue.dev/reference/yaml-migration)
- [모델 역할 개요](https://docs.continue.dev/customize/model-roles/00-intro)
- [Chat 역할](https://docs.continue.dev/customize/model-roles/chat)
- [Edit 역할](https://docs.continue.dev/customize/model-roles/edit)
- [Apply 역할](https://docs.continue.dev/customize/model-roles/apply)
- [Autocomplete 역할](https://docs.continue.dev/customize/model-roles/autocomplete)
- [Autocomplete 모델 설정](https://docs.continue.dev/ide-extensions/autocomplete/model-setup)
- [모델 capabilities](https://docs.continue.dev/customize/deep-dives/model-capabilities)
- [문제 해결](https://docs.continue.dev/troubleshooting)

---

## 15. 빠른 시작 요약

처음부터 다시 구성할 때는 아래 순서만 따르면 된다.

```powershell
# 1. 설치 확인
ollama --version

# 2. 모델 설치
ollama pull qwen2.5-coder:14b
ollama pull qwen3.6:27b
ollama pull gemma4:31b

# 3. 설치 모델과 서버 확인
ollama list
Invoke-RestMethod http://localhost:11434/api/tags
```

그다음 VS Code 또는 IntelliJ에 Continue를 설치하고, `C:\Users\USER\.continue\config.yaml`에 이 문서의 권장 설정을 저장한다.

운영 원칙은 다음 한 줄로 요약할 수 있다.

> **Qwen2.5-Coder 14B는 자동 완성, Qwen3.6 27B는 코드 Agent, Gemma4 31B는 한국어·이미지 작업에 사용한다.**
