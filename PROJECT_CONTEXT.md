# 프로젝트 정보

- 프로젝트 이름: 우사기 알람 (usagi_alarm)
- 목적과 사용자: 치이카와 우사기를 테마로 한 개인용 비영리 안드로이드 알람 앱. 일반 사용자에게 APK로 배포(GitHub 릴리스).
- 저장소 / 작업 경로: `C:\repository\usagi_alarm`, 원격 https://github.com/eva01sin/usagi_alarm
- 사용할 공통 에이전트 정의의 버전 또는 커밋: agents-claude (로컬 `C:\repository\agents-claude`, 2026-10-31 설치)
- 기술 스택과 버전: Java 17, Android Gradle(wrapper), compileSdk/targetSdk 35, minSdk 26, JUnit 4.13.2. 현재 v1.2.7 (versionCode 6)
- 주요 폴더와 구조:
  - `app/src/main/java/com/usagi/alarm/` — AlarmScheduler/Receiver/Service/Store/Times(예약·울림), MainActivity(설정), RingingActivity(울림 화면), AlarmWidget(3×1 위젯), UsagiClockView·AlarmMotion(그림·움직임), PcmAssets·PcmSequence(음원), RestoreReceiver(재부팅 복원)
  - `app/src/test/` 단위 테스트, `app/src/androidTest/` 에뮬레이터 통합 시나리오
  - `docs/` README용 이미지, `verification/` 원본 검증 출력(git 제외)
- 설치 방법: Android 8.0 이상 기기에 APK 설치. 개발은 `C:\repository\.android-tools`의 SDK·Gradle·에뮬레이터 사용.
- 실행 방법: 에뮬레이터 `Usagi_API_35` 기동 후 `.\gradlew.bat installDebug`
- 빌드 방법: `.\gradlew.bat assembleDebug`
- 테스트 방법: CLAUDE.md "빌드·테스트" 참고 (단위 16개 + 통합 시나리오 `all` 13개 + 별도 `widgetHost`)
- 프로젝트 개발 규칙: CLAUDE.md "프로젝트 규칙" 참고
- 수정 가능한 범위와 제약: 외부 의존성·네트워크·수익화 추가 금지. 서명 인증서 유지. 릴리스·푸시는 사용자 요청 시에만.
- 현재 요청과 완료 기준: 없음 (작업 시작 시 coordinator가 기록)

## 협업 설정

- 설계·작업 상태 기록 위치: 이 파일의 "현재 요청" 항목과 TEST_REPORT.md
- 작업 브랜치 또는 작업 폴더: 미정 (git 소유권 경고로 확인 못 함)
- 변경 전 테스트 결과와 알려진 실패: v1.2.7 기준 단위 16개·통합 13개 통과(TEST_REPORT.md)
- 필수 자동 검증 / 수동 확인 절차: assembleDebug·assembleDebugAndroidTest·testDebugUnitTest·lintDebug, 에뮬레이터 통합 `all`
- 핵심 사용자 흐름과 통합 확인 기준: 알람 설정 → 사전 알림 → 본 알람 → `5분만...`/끄기, 뒤로·홈 종료 시 알람 해제, 재부팅 후 복원, 위젯 현재 시각 표시와 얼굴 버튼 켜기/끄기
- 현재 작업 ID / 상태 / 선행 작업:
  - LANG-001 한국어·영어 언어 설정: 개발·독립 검증 완료(조건부 통과, 미커밋). `all`의 간헐 실패는 v1.2.7에서도 재현되는 기존 타이밍·에뮬레이터 문제
  - ALARM-002 사용 중 알람 화면 직접 표시(`SYSTEM_ALERT_WINDOW`, 권한 없으면 기존 알림 카드): 개발·독립 검증 완료(조건부 통과, 미커밋)
  - FOLD-003 폴더블·큰 화면 대응(1단계): 개발 완료, 수정 1회차(낮은 큰 창 설정 화면 하단 잘림) 후 소리 없는 항목 재검증 통과. 알람 소리가 필요한 통합 항목(foldWindow 전체, overlayLaunch, all, widgetHost, 알람 중 크기 변경)은 에뮬레이터 콜드 재기동 후 재검증 필요 → 수정 횟수 2/2 (통합 재검증에서 폰 가로 버튼 위치 회귀 발견 → 2회차: `WindowLayoutPolicy.mayMoveAlarmControls`로 폰 크기 창은 이동 안 함, 코드·단위 40개 통과, 소리 필요한 통합은 미재검증. 이번 재검증이 실패하면 계획 재수립)
  - 최종 재검증 2(콜드 부팅): LANG-001·ALARM-002 통과, FOLD-003은 `artwork` 회귀 수정 확인·`foldSettings` 통과, `foldWindow` 3/3 실패(알람 화면 큰 창 "control buttons are not shown", 원인 미확정: 시험의 clock 참조 경쟁 가설 vs 에뮬레이터 오디오 열화 pcm 3,237건). 수정 한도(2/2) 소진 → 재계획 필요. v1.3.0 릴리스 준비 불가(foldWindow 통과 필요)
  - 최종 재검증 3(콜드 부팅 직후 foldWindow 우선): foldWindow 3/4 통과(실패 1회는 pcm 0건이지만 HWUI Davey 16~18초 호스트 그래픽 정체 동반, `artwork must fill the new window: 0x0` — 시험이 `clock`을 한 번만 잡아 뷰 재구성 후 분리된 뷰를 보는 경쟁 가능성). 제품 결함 증거 없음. FOLD-003 조건부 통과, v1.3.0 릴리스 조건부 가능. 열화 전 `all` 반복, 접힘 전환 후 버튼 클릭, 오버레이+큰 창 조합은 미확인
  - 최종 재검증 4(시험 안정화 후, 콜드 부팅): foldWindow 2/3 통과(실패 1회는 inner-tall 사전 알림 단발 검사 `artwork must fill the window: 0x0`, pcm 0건·그래픽 정체 없음). 원인 가설: 큰 세로 창에서 매니페스트 landscape 잠금으로 처음 가로로 뜬 뒤 `applyOrientationPolicy` 해제로 세로 전환되며 `onConfigurationChanged→buildScreen`이 뷰를 새로 만듦(첫 화면이 가로로 보였다 돌아가는 깜박임 가능). 시험 쪽 틈: `checkRingingWindow` 사전 알림의 `checkClockFillsWindow`가 `awaitCheck` 밖 단발 검사(AlarmInstrumentation 약 683행). `all`은 7개 통과 후 `backDismiss`에서 오디오 열화(pcm 5,415건)로 멈춤 → 이번 부팅의 `all` 완주 미확인. 시험 순서 제안: 한 부팅에서 소리 시나리오 5~6개 이내, 사이 30~60초 간격, pcm 수백 건이면 중단
  - HOL-004 진행: 개발 완료 → 독립 검증 D1(노동절 5/1·제헌절 7/17이 2026년부터 공휴일, 대체공휴일 포함이 누락, 웹으로 확인) → 수정 1회차 완료(단위 58개) → 재검증 중. 에뮬레이터 시나리오 `holiday`·`foldSettings`는 미실행. 알려진 한계: 선거일은 2026·2028만 고정, 임시공휴일은 앱 업데이트로 추가.
  - HOL-004 범위 변경 + HOL-005(캘린더 공휴일 가져오기): 사용자 결정 — 내장 계산에서 선거일 제거(법정·음력·대체공휴일만 유지), 임시공휴일·선거일은 별도 스위치 `캘린더에서도 공휴일 가져오기`(READ_CALENDAR 런타임 권한, 구글 대한민국 공휴일 캘린더 `south_korea#holiday`, 기본 꺼짐)로만 반영. 울리기 직전(AlarmReceiver) 재확인으로 늦게 발표된 임시공휴일도 건너뜀, 오류 시에는 울림. v1.4.0에 함께 포함 예정. **사용자 요청으로 개발 도중 중단(미착수에 가까움, 빌드·단위 58개 정상)**. 재개 시 개발자에게 위 범위(선거일 제거 + 캘린더 가져오기 + AlarmReceiver 울리기 직전 재확인, 오류 시 울림)를 다시 전달. 현재 미커밋 상태: HOL-004 구현(공휴일 판정·토글·단위 58개)은 독립 검증 조건부 통과(에뮬레이터 `holiday`·`foldSettings`·`language` 미실행), v1.3.1 이후 커밋 없음.
  - HOL-004 한국 공휴일엔 알람 울리지 않기 토글(v1.4.0 예정, 기본 꺼짐) 계획: 정책 — 한국 기준 고정(기기 지역 무관), 사전 알림도 함께 건너뜀, 반복·단발 모두 다음 공휴일 아닌 날로 예약, 연속 휴일 대비 탐색 범위 확대, 공휴일 판정은 Android 비의존 클래스로 분리해 단위 테스트
  - 공통 남은 일: 에뮬레이터 콜드 재기동 후 통합 재검증, 서명 APK 빌드·릴리스(사용자 요청 시), 실기기(삼성 폴더블) 확인
  - 알려진 환경 문제: 에뮬레이터를 오래 쓰면 오디오 `pcm_writei` 오류가 쌓여 `all`이 실패/멈춤. 콜드 재기동하면 초기화됨. 계측이 중간에 죽으면 `wm size`/앱 언어 저장값(`ko`)이 남을 수 있어 시작 전 확인
- 담당 파일과 다음 담당자: 없음
- 검증 대상 커밋 또는 변경 상태: 미정
- 수정 횟수와 미해결 문제: 없음. 알려진 미검증: 실제 휴대폰의 절전·배터리 최적화, 정확한 알람 권한이 없는 기기의 위젯 갱신 지연

## HOL-005 진행 기록 (2026-11-03)

- 완료(개발 직접 수행, 독립 검증 전): A 선거일 제거, B 캘린더 스위치·READ_CALENDAR 런타임 요청·`AndroidHolidayCalendar`·`HolidayPolicy`·`CalendarHolidays`, 예약 시점/onResume/부팅 재계산, AlarmReceiver 울리기 직전 재확인(`AlarmScheduler.skipIfHoliday`, 오류 시 울림). 단위 68개 통과, assembleDebug·assembleDebugAndroidTest·lintDebug 통과.
- 2026-11-04 추가 완료: `calendarHoliday` 시나리오, 에뮬레이터 `all` 14/14·`holiday`·`calendarHoliday`·`foldSettings`·`language`·`settings` 통과, compact 큰 창 레이아웃 수정, README v1.4.0, 버전 1.4.0/versionCode 9, TEST_REPORT.md. 단위 68개.
- 남음: 독립 검증(verifier, 이 세션은 서브에이전트 불가라 미실시), 서명 APK·커밋·릴리스(사용자 요청 시; README 맨 위 APK 링크는 릴리스 전이라 v1.3.1 그대로), 실기기·실제 구글 캘린더 확인.

## HOL-005 재개용 상세 요구사항 (대화를 지워도 이어갈 수 있게 보존)

작업 시작: coordinator 절차(개발 → 독립 검증 → 에뮬레이터)로 진행. 커밋·푸시·삭제·에뮬레이터 조작은 사용자 요청 시에만, 커밋에 Co-Authored-By 넣지 않기.

A. 내장 계산에서 선거일 제거 (KoreanHolidays의 2026-06-03, 2028-04-12 목록·로직·테스트 기대값). 남기는 것: 양력 고정 공휴일(노동절 5/1·제헌절 7/17은 2026년부터), 설날·부처님오신날·추석, 대체공휴일. 선거일 제거가 대체공휴일 배정에 영향 없는지 확인.

B. 캘린더 가져오기 스위치 `캘린더에서도 공휴일 가져오기` / `Also use holidays from calendar` (기본 꺼짐)
- 위치: `한국 공휴일엔 알람 울리지 않기` 스위치 아래. 위 스위치가 꺼져 있으면 비활성(흐리게). 짧은 안내 문구(임시공휴일·선거일을 구글 캘린더 공휴일에서 가져옴, 캘린더 읽기 권한 필요). 영/한 문구, ids.xml, foldSettings id 목록 반영.
- 권한: AndroidManifest에 READ_CALENDAR. 스위치를 켤 때 런타임 요청(기존 권한 요청 방식과 맞춤). 거부하면 스위치를 되돌리고 안내. 나중에 철회돼도 SecurityException을 처리해 내장 계산만 사용, 앱이 죽지 않음.
- 읽기: CalendarContract.Calendars의 OWNER_ACCOUNT에 `south_korea#holiday`가 들어가는 캘린더(ko./en. 접두사 모두)만, Instances.query로 구간의 종일 일정. 종일 일정은 UTC 자정 기준 → UTC로 LocalDate 변환(순수 함수로 분리, 단위 테스트: 하루/여러 날 연휴/종료 시각 배타). 캘린더가 없으면 빈 집합 + 설정 화면에 "공휴일 캘린더를 찾지 못했습니다(구글 캘린더 동기화 필요)" 한 줄.
- 판정: 건너뛸 날 = 내장 isHoliday OR 캘린더 공휴일. 두 스위치가 모두 켜졌을 때만 캘린더 사용. 조회 인터페이스는 Android 비의존으로 분리해 단위 테스트에서 가짜 주입.
- 최신성: (1) 예약 시점(save/restore/advance)에 읽어 계산, (2) 앱 열 때(onResume)·부팅 복원 때 재계산, (3) 가장 중요: AlarmReceiver가 본 알람(MAIN)·사전 알림(PRE)을 울리기 직전에 오늘이 공휴일인지 다시 판정해 공휴일이면 울리지 않고 advance(). 조회 실패·지연 시 반드시 울림(안전 폴백). 스누즈 경로는 건드리지 않음. generation·PendingIntent 일관성 유지, 사전 알림/본 알람 불일치 없게.

C. 테스트: 단위(UTC 변환, 결합 판정, 선거일 제거 후 기대값, 스위치 조합) + 에뮬레이터 시나리오 `calendarHoliday`(all 제외, 시험용 로컬 캘린더 OWNER_ACCOUNT를 'ko.south_korea#holiday@group.v.calendar.google.com' 형태로 만들어 종일 일정 삽입, 권한 없을 때 내장 계산만 사용 확인, 끝에 데이터 정리). 빌드: `.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` (현재 단위 58개).

D. 끝낸 뒤: 에뮬레이터에서 `holiday`·`calendarHoliday`·`foldSettings`·`language`(이전 언어 드롭다운 후 미실행) 실행, README `### v1.4.0` 갱신(캘린더 권한·개인정보 안내, 내장 계산 범위와 한계), 버전 1.4.0 / versionCode 9, TEST_REPORT.md, 서명 APK·커밋·릴리스는 사용자 요청 시. 2027-05-03(노동절 대체)은 자료마다 달라 미확정이며 월력요항으로 재확인 필요.

## 문서 산출물 설정

- 기능명 / 매뉴얼 파일명: 해당 없음 (README 업데이트 내역으로 대체)
- 매뉴얼 저장 위치: `README.md`
- 테스트 보고서 저장 위치: `TEST_REPORT.md` (최신 섹션을 맨 위에 추가)
- 매뉴얼 대상 독자 / 사용 권한: 일반 앱 사용자
- 매뉴얼 작성·갱신 담당: 개발
- 매뉴얼 검증·보고서 담당: 테스트 및 검증
- 기능별 매뉴얼 경로 / 작업별 보고서 경로: README.md / TEST_REPORT.md

## 효율 설정

- 작업 규모와 판단 근거 (경량/일반/복합): 작업마다 coordinator가 판단
- 실행 방식 (자체 확인/독립 검증): 알람·위젯 동작 변경은 독립 검증(verifier)
- 토큰·시간·호출 예산: 미설정
- 실제 사용량 확인 수단: 측정 불가
- 계획한 개발·검증 인계 횟수: 작업마다 기록
- 현재 수정 횟수 / 중단·재계획 조건: 최대 2회 수정 후 재계획
- 재사용할 검증 근거와 유효한 대상 코드·환경: 같은 코드·에뮬레이터(API 35)에서 얻은 단위·통합 결과만 재사용
