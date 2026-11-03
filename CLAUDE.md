# 우사기 알람 (usagi_alarm)

이 프로젝트는 agents-claude의 총괄·개발·검증 에이전트(`.claude/agents/`)를 사용합니다.

## 에이전트 사용

- 기능 구현·버그 수정·동작 변경은 `/task <내용>` 또는 `claude --agent coordinator`로 시작합니다.
- 총괄(coordinator)이 작업을 분류하고 `developer`(구현)와 `verifier`(독립 검증)를 호출합니다.
- 오탈자·문서 등 동작에 영향 없는 작은 수정은 에이전트 없이 직접 처리해도 됩니다.

## 공통 원칙

- 추측과 확인된 사실을 구분합니다. 실행하지 않은 테스트를 통과했다고 보고하지 않습니다.
- 비밀번호·API 키·서명 키 등 비밀정보를 문서·저장소·로그에 남기지 않습니다.
- 요청 범위 안에서만 변경하고, 사용자의 기존 변경은 되돌리지 않습니다.
- 병합·배포·푸시·릴리스는 사용자가 요청했을 때만 합니다.
- 커밋 메시지와 PR 설명에 `Co-Authored-By: Claude ...` 같은 부저자·생성 표기를 넣지 않습니다.
- 프로젝트 정보와 작업 기록은 `PROJECT_CONTEXT.md`에 둡니다.

## 산출물 위치

이 프로젝트는 기존 관례를 유지합니다. 템플릿 기본 경로(`docs/manuals/`, `docs/test-reports/`)를 쓰지 않습니다.

| 산출물 | 경로 | 비고 |
|---|---|---|
| 사용자 안내·업데이트 내역 | `README.md` | 버전별 `### vX.Y.Z · YYYY-MM-DD` 섹션을 위에 추가 |
| 테스트 보고서 | `TEST_REPORT.md` | 최신 검증을 맨 위에 추가. 환경·명령·통과 수·미검증 항목을 적음 |
| 원본 검증 출력(로그·스크린샷) | `verification/` | `.gitignore` 대상(저장소에 올라가지 않음). 보고서에서 경로로만 참조 |
| 릴리스 APK | 저장소 밖 `C:\repository\usagi_alarm_vX.Y.Z.apk` | `*.apk`는 `.gitignore` 대상 |

기능 매뉴얼(`templates/MANUAL.template.md`)은 앱에 별도 사용자 매뉴얼이 없으므로 README 업데이트 내역으로 대체합니다.

## 프로젝트 규칙

### 스택
- Android 앱, Java 17, 순수 Android 프레임워크 UI(외부 UI 라이브러리 없음), minSdk 26 / compileSdk·targetSdk 35. 의존성은 테스트용 JUnit 4.13.2뿐이며 추가하지 않습니다.
- 패키지 `com.usagi.alarm`, 소스 `app/src/main/java/com/usagi/alarm/`, 단위 테스트 `app/src/test/`, 에뮬레이터 통합 테스트 `app/src/androidTest/.../AlarmInstrumentation.java`.
- 서버·계정·복용 기록이 없는 로컬 앱입니다. 네트워크·수익화(광고·결제) 기능을 넣지 않습니다.

### 빌드·테스트 (Windows)
- 기본 검증 묶음: `.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` (lint는 `abortOnError true`).
- SDK는 `C:\repository\.android-tools\sdk`(`local.properties`), 에뮬레이터 AVD는 `Usagi_API_35`(API 35).
- 통합 시나리오: 디버그·테스트 APK를 에뮬레이터에 설치한 뒤
  `adb -s emulator-5554 shell am instrument -w -e scenario <이름|all> com.usagi.alarm.test/com.usagi.alarm.AlarmInstrumentation`
  시나리오 이름은 `AlarmInstrumentation.java`의 `run("...", ...)` 호출에서 확인합니다.
- 알람·위젯 관련 변경은 단위 테스트만으로 통과 처리하지 않고 통합 시나리오(`all`)까지 실행합니다. 실제 휴대폰·절전 정책·정확한 알람 권한 없는 기기는 에뮬레이터로 검증되지 않으므로 보고서의 "미검증"에 명시합니다.
- 이 폴더는 과거 Codex 샌드박스 계정 소유라 git이 `dubious ownership`을 낼 수 있습니다. `git config --global`을 임의로 바꾸지 말고 사용자에게 확인합니다.

### 릴리스 규칙
- 버전을 올릴 때 `app/build.gradle`의 `versionCode`·`versionName`, 화면 하단 버전 표기, README의 APK 링크·파일명, 업데이트 내역, TEST_REPORT.md를 함께 갱신합니다.
- APK는 기존 설치본 위에 업데이트되도록 이전 버전과 같은 서명 인증서로 빌드하고 `apksigner verify`로 확인합니다. 서명 키·키스토어 비밀번호는 저장소에 두지 않습니다.
- GitHub 릴리스 업로드는 사용자가 요청했을 때만 합니다.

### 코드·문서 스타일
- 기존 코드 스타일(들여쓰기, 한국어 사용자 문구)을 따르고 코드 주석은 한국어로 필요한 곳에만 답니다.
- 서체(DSEG7, SIL OFL)와 캐릭터 이미지 출처·라이선스 표기는 README "이미지 출처"와 `assets/DSEG-LICENSE.txt`에서 유지합니다.
- 사용자 문구와 문서는 한국어입니다.

@PROJECT_CONTEXT.md
