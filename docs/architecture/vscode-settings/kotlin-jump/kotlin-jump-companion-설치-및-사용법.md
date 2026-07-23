# Kotlin Jump Companion 설치 및 사용법

## 1. 문서 목적

이 문서는 JetBrains Kotlin 확장과 커스텀 Kotlin Jump Companion을 함께 사용하여 다음 동작을 제공하는 방법을 설명한다.

- 일반 `Ctrl + 클릭`과 `F12`는 JetBrains Kotlin이 처리한다.
- 구현 메서드 위의 `↑ overrides`는 Kotlin Jump가 인터페이스 또는 상위 클래스 선언으로 이동시킨다.
- `Alt + F7`은 Kotlin Jump의 사용처 검색을 실행한다.
- Kotlin Jump의 일반 Definition Provider는 Companion Mode에서 등록되지 않으므로 `Definitions (2)` 중복을 방지한다.

## 2. 필요한 구성

다음 두 확장만 함께 사용한다.

| 역할 | 확장 ID |
| --- | --- |
| Kotlin 의미 분석, 일반 Definition | `jetbrains.kotlin-server` |
| override 탐색, 사용처 검색 | `local.kotlin-jump-companion-local` |

기존 Marketplace판 Kotlin Jump인 `elumine.kotlin-jump`는 함께 설치하지 않는다. 함께 설치하면 Definition 결과나 명령이 다시 중복될 수 있다.

커스텀 VSIX 원본 위치:

```text
C:\workspace\kotlin-jump-companion-local\kotlin-jump-companion-local-1.23.0.vsix
```

## 3. 현재 PC에 설치하기

### 3.1 VS Code 화면에서 설치

1. `Ctrl + Shift + X`로 Extensions 화면을 연다.
2. `Kotlin by JetBrains`를 검색하여 설치한다.
3. 기존 `Kotlin Jump`가 설치되어 있다면 제거한다.
4. Extensions 화면 오른쪽 위의 `...` 메뉴를 누른다.
5. `Install from VSIX...`를 선택한다.
6. 다음 파일을 선택한다.

   ```text
   C:\workspace\kotlin-jump-companion-local\kotlin-jump-companion-local-1.23.0.vsix
   ```

7. 설치가 끝나면 `Ctrl + Shift + P`를 누르고 `Developer: Reload Window`를 실행한다.

### 3.2 명령줄에서 설치

`code` 명령을 사용할 수 있다면 PowerShell에서 다음을 실행한다.

```powershell
code --install-extension jetbrains.kotlin-server
code --uninstall-extension elumine.kotlin-jump
code --install-extension "C:\workspace\kotlin-jump-companion-local\kotlin-jump-companion-local-1.23.0.vsix" --force
```

`elumine.kotlin-jump`가 원래 설치되어 있지 않다면 제거 명령의 오류는 무시해도 된다.

설치 후 VS Code에서 `Developer: Reload Window`를 한 번 실행한다.

## 4. 필수 설정

1. `Ctrl + Shift + P`를 누른다.
2. `Preferences: Open User Settings (JSON)`을 실행한다.
3. 기존 JSON 객체 안에 다음 설정을 추가한다.

```json
{
    "kotlinJump.companionMode": "always",
    "kotlinJump.indexSourcesJars": false,
    "kotlinJump.indexMavenSources": false,
    "kotlinJump.suppressFirstScanPrompt": true,
    "kotlinJump.smartNavigation": false,
    "kotlinJump.codeLens": false,
    "kotlinJump.overrideGutterIcons": true,
    "kotlinJump.snapshotEnabled": true,
    "editor.codeLens": true
}
```

기존 `settings.json`에 다른 설정이 있다면 바깥쪽 `{}`를 새로 추가하지 말고 각 속성만 합친다. 앞 속성과 다음 속성 사이에는 쉼표가 필요하다.

설정 의미:

| 설정 | 의미 |
| --- | --- |
| `companionMode: always` | JetBrains Kotlin과 함께 사용하는 Companion Mode 강제 적용 |
| `indexSourcesJars: false` | Gradle 캐시의 수많은 `-sources.jar` 색인 방지 |
| `indexMavenSources: false` | Maven 저장소 전체 소스 색인 방지 |
| `smartNavigation: false` | 일반 Definition 탐색에 Kotlin Jump가 개입하는 동작 비활성화 |
| `codeLens: false` | Kotlin Jump의 일반 사용처 개수 렌즈 비활성화 |
| `overrideGutterIcons: true` | `↑ overrides`와 구현 관계 렌즈 활성화 |
| `editor.codeLens: true` | VS Code 편집기에서 override 렌즈가 보이도록 허용 |
| `snapshotEnabled: true` | 프로젝트 인덱스를 재사용하여 다음 실행을 빠르게 함 |

설정을 변경한 후에는 `Developer: Reload Window`를 실행한다.

## 5. 기능 사용법

### 5.1 일반 코드의 선언으로 이동

호출부의 메서드, 클래스 또는 변수 이름에서 다음 중 하나를 사용한다.

- `Ctrl + 클릭`
- `F12`
- 마우스 오른쪽 버튼의 `Go to Definition`

이 탐색은 JetBrains Kotlin이 담당한다.

예시:

```kotlin
registerUseCase.register(command)
```

호출부의 `register`에서 `Ctrl + 클릭`하면 JetBrains가 타입을 분석하여 선언으로 이동한다.

### 5.2 구현 메서드에서 인터페이스 선언 확인

다음과 같은 구현 메서드 위에는 `↑ overrides`가 표시된다.

```kotlin
override fun register(
    command: RegisterKnowledgeDocumentCommand,
): RegisteredKnowledgeDocumentResult {
    // ...
}
```

`↑ overrides`를 클릭하면 Kotlin Jump가 해당 메서드가 구현한 인터페이스 선언으로 이동한다.

이 프로젝트의 예에서는 다음 관계를 확인할 수 있다.

```text
RegisterKnowledgeDocumentService.register(...)
    -> RegisterKnowledgeDocumentUseCase.register(...)
```

클래스가 여러 인터페이스를 구현하더라도 Kotlin Jump 인덱스가 override 선언과 상위 타입을 찾아 이동하므로, 클래스 선언부의 인터페이스를 일일이 열어볼 필요가 없다.

### 5.3 인터페이스의 구현체 확인

인터페이스 또는 추상 메서드 위에 `N implementations` 렌즈가 표시되면 이를 클릭한다. 구현체가 하나이면 바로 이동하고, 여러 개이면 선택 목록이 나타난다.

### 5.4 사용처 검색

검색할 클래스, 메서드 또는 변수에 커서를 놓고 `Alt + F7`을 누른다.

Kotlin Jump의 Find Usages 패널에 프로젝트 내 사용처가 표시된다. 테스트 코드나 미리보기 코드 표시 여부는 패널의 필터로 조정할 수 있다.

단축키가 다른 확장과 충돌하면 다음과 같이 확인한다.

1. `Ctrl + K`, `Ctrl + S`로 Keyboard Shortcuts를 연다.
2. `kotlin-jump.findUsages`를 검색한다.
3. 원하는 단축키를 다시 지정한다.

## 6. 정상 설치 확인

PowerShell에서 다음을 실행한다.

```powershell
code --list-extensions --show-versions | Select-String "kotlin"
```

정상 예시:

```text
jetbrains.kotlin-server@0.0.5
local.kotlin-jump-companion-local@1.23.0
```

다음 확장이 함께 출력되면 제거해야 한다.

```text
elumine.kotlin-jump@...
```

제거 명령:

```powershell
code --uninstall-extension elumine.kotlin-jump
```

제거 후 `Developer: Reload Window`를 실행한다.

## 7. 다른 PC에 적용하기

### 7.1 파일 복사

다음 VSIX 파일을 USB, 사내 파일 공유 또는 클라우드 저장소로 다른 Windows PC에 복사한다.

```text
kotlin-jump-companion-local-1.23.0.vsix
```

다른 PC에는 `C:\workspace\kotlin-jump-companion-local` 소스 폴더가 없어도 된다. VSIX 파일만 있으면 설치할 수 있다.

### 7.2 다른 PC에서 설치

1. VS Code에 `Kotlin by JetBrains`를 설치한다.
2. 기존 `elumine.kotlin-jump`를 제거한다.
3. Extensions 화면의 `Install from VSIX...`를 선택한다.
4. 복사한 VSIX를 선택한다.
5. 이 문서의 **필수 설정**을 User `settings.json`에 적용한다.
6. `Developer: Reload Window`를 실행한다.
7. **정상 설치 확인** 명령으로 확장 목록을 확인한다.

명령줄 설치 예시:

```powershell
code --install-extension jetbrains.kotlin-server
code --uninstall-extension elumine.kotlin-jump
code --install-extension "D:\tools\kotlin-jump-companion-local-1.23.0.vsix" --force
```

`D:\tools` 부분은 실제로 VSIX를 복사한 경로로 바꾼다.

## 8. 문제 해결

### 8.1 `Definitions (2)`가 다시 표시되는 경우

다음을 순서대로 확인한다.

1. `elumine.kotlin-jump`가 다시 설치되지 않았는지 확인한다.
2. `kotlinJump.companionMode`가 `always`인지 확인한다.
3. 커스텀 확장 ID가 `local.kotlin-jump-companion-local`인지 확인한다.
4. `Developer: Reload Window`를 실행한다.

VS Code Settings Sync가 다른 PC의 확장 목록을 동기화하면서 Marketplace판 Kotlin Jump를 다시 설치할 수 있다. 이 경우 `elumine.kotlin-jump`만 다시 제거한다.

### 8.2 `↑ overrides`가 보이지 않는 경우

다음을 확인한다.

```json
"editor.codeLens": true,
"kotlinJump.overrideGutterIcons": true
```

그 후 Kotlin 파일을 저장하고 `Developer: Reload Window`를 실행한다. Kotlin Jump의 프로젝트 색인이 완료되어야 렌즈가 표시된다.

### 8.3 VS Code 시작이 다시 느려지는 경우

다음 설정이 `false`인지 확인한다.

```json
"kotlinJump.indexSourcesJars": false,
"kotlinJump.indexMavenSources": false
```

상태 표시줄에 수백 개의 JAR 또는 백만 개 이상의 symbol을 스캔한다는 메시지가 장시간 표시되면 Marketplace판 Kotlin Jump가 함께 설치되었거나 설정이 다른 범위에 적용됐을 가능성이 있다.

User 설정과 Workspace 설정을 모두 확인한다. Workspace 설정이 User 설정을 덮어쓸 수 있다.

### 8.4 JetBrains Kotlin 서버 연결 오류가 표시되는 경우

1. `View -> Output`을 연다.
2. 출력 채널에서 `Kotlin by JetBrains`를 선택한다.
3. 서버 초기화 오류 내용을 확인한다.
4. 프로젝트에서 사용하는 JDK가 설치되어 있는지 확인한다. 이 프로젝트의 기준은 JDK 21이다.
5. `Developer: Reload Window`를 실행한다.

이 문제는 Kotlin Jump의 Definition 중복과 별개이며 JetBrains Kotlin 서버가 정상적으로 시작해야 일반 `F12`와 `Ctrl + 클릭`이 정확하게 동작한다.

### 8.5 `Alt + F7`이 동작하지 않는 경우

- 커서가 Kotlin 또는 Java 심볼 위에 있는지 확인한다.
- Keyboard Shortcuts에서 `kotlin-jump.findUsages` 명령에 `Alt + F7`이 연결됐는지 확인한다.
- 다른 확장이 같은 단축키를 먼저 사용하는지 확인한다.

## 9. 커스텀 VSIX 업데이트

이 로컬 확장은 Marketplace에서 자동 업데이트되지 않는다. 새 VSIX를 받으면 기존 확장을 제거할 필요 없이 다음 명령으로 덮어 설치할 수 있다.

```powershell
code --install-extension "C:\path\to\kotlin-jump-companion-local-새버전.vsix" --force
```

설치 후 `Developer: Reload Window`를 실행한다.

## 10. 제거 및 원본으로 복구

커스텀 버전을 제거하고 Marketplace판 Kotlin Jump로 돌아가려면 다음을 실행한다.

```powershell
code --uninstall-extension local.kotlin-jump-companion-local
code --install-extension elumine.kotlin-jump
```

복구 후 `Developer: Reload Window`를 실행한다.

단, 원본 Kotlin Jump와 JetBrains Kotlin을 동시에 사용할 경우 일반 Definition Provider가 둘 다 등록되어 `Definitions (2)`가 다시 표시될 수 있다.

## 11. 최종 점검표

- [ ] `jetbrains.kotlin-server`가 설치되어 있다.
- [ ] `local.kotlin-jump-companion-local`이 설치되어 있다.
- [ ] `elumine.kotlin-jump`는 설치되어 있지 않다.
- [ ] `kotlinJump.companionMode`가 `always`이다.
- [ ] JAR 및 Maven 전체 소스 색인이 비활성화되어 있다.
- [ ] `editor.codeLens`와 `kotlinJump.overrideGutterIcons`가 활성화되어 있다.
- [ ] `Developer: Reload Window`를 실행했다.
- [ ] 일반 `Ctrl + 클릭` 결과가 하나만 표시된다.
- [ ] `↑ overrides`가 인터페이스 선언으로 이동한다.
- [ ] `Alt + F7` 사용처 검색이 동작한다.
