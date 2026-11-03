# 기능 검증 결과

## v1.3.0 릴리스 시점 상태 · 2026-11-03

- 최종 재검증 4 이후 시험 코드만 변경했습니다: `checkRingingWindow` 사전 알림 단계(AlarmInstrumentation 약 679~687행)를 `awaitCheck`(5초)로 감쌌습니다. 제품 코드 변경은 없습니다.
- 이 변경 후 `assembleDebug`·`assembleDebugAndroidTest`·`testDebugUnitTest`(40개)·`lintDebug`는 성공했으나, **에뮬레이터에서 `foldWindow`·`all`을 다시 실행하지는 못했습니다**(에뮬레이터 오디오 열화 상태, 사용자 요청으로 릴리스 진행).
- 미확인: 이번 코드에서의 `all` 14개 완주(직전 콜드 부팅 FINAL2에서는 3회 중 2회 완주), 큰 세로 창의 알람 화면 첫 프레임 가로 깜박임 여부, 실제 삼성 폴더블·플립 기기 동작.

## v1.3.0 폴더블·큰 화면 대응 (FOLD-003) · 2026-11-02

### 최종 재검증 4 (시험 안정화 후, 콜드 부팅 직후 `foldWindow` 우선) — 최신

- 판정: **통과(조건부)**. 제품 결함은 찾지 못했습니다. `foldWindow` 3회 중 2회 통과했고, 실패 1회는 시험 코드에 남은 단발 검사(경쟁)가 알람 화면의 설계된 뷰 재구성과 어긋난 것입니다. `all`은 이번 부팅에서 오디오 열화로 끝까지 닫지 못했습니다.
- 코드 리뷰(시험 코드만 변경, `app/src/main` 제품 코드 변경 없음 확인): `liveClock`은 매번 현재 시계 뷰를 다시 조회하고 분리된 뷰는 null이라 이전의 0x0 모드를 그대로 잡고, `awaitCheck`는 100ms마다 검사 전체를 다시 실행하다 시간 초과 시 마지막 실패를 그대로 던지므로 완화가 아니라 대기 방식 변경입니다. 같은 인스턴스·서비스·소리 단언(`resize while ringing`)은 유지됐고 `artwork`의 "Alarm controls must be horizontally centered"와 "Pre artwork missing" 검사도 그대로입니다. 남은 틈: `checkRingingWindow`의 사전 알림 단계(라인 683) `checkClockFillsWindow`는 `awaitCheck` 밖의 단발 검사라, 바로 앞의 `awaitLiveClock`이 통과한 뒤 방향 전환 재구성으로 새 뷰가 아직 배치 전이면 실패합니다.
- 환경: 콜드 부팅(`-no-snapshot-load -no-snapshot-save`), 사용자 잠금 해제, pcm_writei 0건, wm Override 없음, 앱 언어 system, appops default, 기기 언어 en-US. 빌드 `FINAL4-verify-gradle.txt`: 단위 40개 통과, lint 오류 0·경고 9.
- `foldWindow`(요약 `FINAL4-verify-run-summary.txt`): 1회 **실패**(pcm 0), 2회 통과(pcm 0), 3회 통과(pcm 259). 통과한 두 회차에서 6개 창(폰 가로 16:9 포함)과 알람 중 창 크기 변경(서비스·소리·RingingActivity 유지, 버튼 유지)이 통과했습니다. 실패 상세: 창 `inner-tall`의 "ringing: pre: artwork must fill the window: 0x0 vs 2176x1812"(AlarmInstrumentation 라인 683). 이때 logcat(`FINAL4-verify-logcat-after-foldWindow-1.txt`)에 2초를 넘는 `Davey`가 없고 최대 `Skipped 91 frames`여서 호스트 그래픽 정체가 아니며 pcm도 0건입니다. 원인(코드·logcat으로 구체화한 가설): inner-tall(1812x2176)은 방향 잠금이 매니페스트 landscape라 처음 가로(2176x1812)로 떴다가 `applyOrientationPolicy`의 해제로 세로로 돌 때 `onConfigurationChanged → buildScreen`이 시계 뷰를 새로 만듭니다(설계된 동작, logcat에 `finishDrawing of orientation change` 156ms). 시험이 `awaitLiveClock`을 통과한 직후 재구성이 일어나면 단발 검사가 0x0 새 뷰를 봅니다. 큰 세로 창에서 처음 가로로 보였다가 돌아가는 깜박임이 있을 수 있다는 사용자 관점 관찰도 함께 남깁니다(결함으로 판정하지는 않음). 제안: 라인 683 검사를 `awaitCheck`로 감쌉니다.
- `all`(같은 부팅, `FINAL4-verify-all-1.txt`): `settings`~`preDismiss`와 `artwork`까지 7개 통과 후 `backDismiss`에서 10분 넘게 멈춰 타임아웃(스레드 덤프 시도 후 `am force-stop`), 이때 pcm_writei 5,415건으로 오디오 열화가 시작되어 이후 실행(`all` 2·3회, `overlayLaunch`, `widgetHost`)은 무효·미실행으로 표시하고 멈췄습니다. 따라서 이번 부팅에서 `all` 14개 완주는 확인하지 못했습니다. 직전 콜드 부팅(FINAL2)에서는 `all` 3회 중 2회 14개 완주와 `artwork` 통과를 확인했습니다.
- 앞으로의 시험 순서 제안: 소리 시나리오(`all`, `foldWindow`)는 한 부팅에서 합계 5~6개 시나리오 이내로 묶고, 묶음 사이에 콜드 재부팅을 하며, 시나리오 사이에 최소 30~60초 간격을 두고, pcm_writei가 수백 건대에 오르면 곧바로 멈춥니다. 소리 없는 시나리오(`settings`·`foldSettings`)와 `overlayLaunch`·`widgetHost`는 열화 후에도 의미가 있으나 `settings`처럼 항상 통과하던 시나리오가 실패하면 열화로 보고 무효 처리합니다.
- 추가 확인(알람 중 접힘 전환 후 `5분만...`/`알람 끄기` 실제 클릭, 오버레이 허용+큰 창 직접 열기)은 열화 시작으로 진행하지 못했습니다(미확인).
- 원복 확인: wm Override 없음, 앱 강제 종료·홈, appops default, 열화 중 실행으로 `language=ko`·`enabled=true`가 남았던 것을 설정 화면에서 알람 끄기와 `시스템 설정 따라가기`로 되돌려 저장값 system·`enabled=false`·`target=0` 확인, 기기 언어 en-US. 에뮬레이터는 종료하지 않았고 열화 상태입니다.

### 최종 재검증 3 (콜드 부팅 직후 `foldWindow` 우선 실행, 코드 변경 없음) — 최신

- 판정: **통과(조건부)**. 제품 결함은 찾지 못했습니다. `foldWindow`는 4회 중 3회 통과했고, 실패 1회는 pcm_writei 0건인데도 났지만 같은 시각에 호스트 전체 그래픽이 16~18초 멈춘 증거가 있어 환경 정체로 분류합니다. 시험 코드의 취약점(제안)이 이를 키웠습니다.
- 환경: 잔재 emulator.exe 프로세스는 AVD 잠금을 잡고 있지 않았고(`Another emulator instance` 없이 기동, 부팅 20.6초), 콜드 부팅(`-no-snapshot-load -no-snapshot-save`) 후 사용자 잠금 해제·pcm_writei 0건·wm Override 없음·앱 언어 system·appops default·기기 언어 en-US 확인. 최신 코드 빌드 `FINAL3-verify-gradle.txt`: 단위 40개 통과, lint 오류 0·경고 9. 부팅 직후 가장 먼저 `foldWindow`를 돌렸습니다.
- `foldWindow`(요약 `FINAL3-verify-run-summary.txt`, `...summary2.txt`): 1회 통과(pcm 0), 2회 **실패**(pcm 0), 3회 통과(pcm 747), 4회 통과(pcm 2,394). 통과한 3회에서 6개 창(폰 세로·가로 16:9 포함)과 알람 중 창 크기 변경(서비스·소리·RingingActivity 같은 인스턴스, 이미 나타난 버튼 유지)이 모두 통과했습니다.
- 실패 상세(2회): 창 `resize while ringing: folded`의 "artwork must fill the new window: 0x0 vs 2176x1812"(AlarmInstrumentation `checkResizeWhileRinging`). 같은 시각(기기 시각 15:08:19~21)의 logcat에 서로 다른 프로세스(런처·앱 등)의 `HWUI Davey!`가 16.6~18.7초씩 동시에 찍혀 있어 호스트 GPU/디스플레이 정체입니다(`FINAL3-verify-logcat-after-foldWindow-2.txt`, `...logcat-final.txt`). 이때 새로 만든 알람 뷰가 아직 배치되지 않아 0x0이고 창 크기도 갱신 전이었습니다. 이 구간은 pcm_writei가 아니라 그래픽 정체여서 pcm 건수만으로는 환경 여부를 가를 수 없음을 보여 줍니다.
- 시험 취약점(제안, 수정 아님): `checkResizeWhileRinging`과 `checkRingingWindow`는 `findClock`으로 `clock`을 한 번만 잡고 이후 고정된 sleep/await로 기다립니다. `RingingActivity.onConfigurationChanged → buildScreen`이 뷰를 새로 만들면(설계된 동작) 잡아 둔 `clock`은 분리된 이전 뷰가 되어 0x0이거나 버튼이 안 보이는 것처럼 보입니다. 렌더링이 느리면 이 경쟁이 쉽게 드러나므로, 대기 안에서 매번 clock을 다시 찾고 새 뷰가 배치될 때까지 기다리도록 개선하면 간헐 실패가 줄어듭니다. 앞선 `foldWindow` 간헐 실패("control buttons are not shown")도 같은 구조로 설명됩니다.
- 이후 실행(오디오·그래픽 열화 시작 후, pcm 2,394건): `all`은 2회 모두 첫 시나리오 `settings`에서 실패("Noon conversion failed"/"Disabled draft or midnight conversion failed", 이전에는 항상 통과) — 이는 열화 상태에서 UI 입력·저장 타이밍이 어긋난 것으로 보이며 유효한 `all` 결과로 쓰지 않습니다. `overlayLaunch` 1회 통과, `widgetHost` 1회 통과. 열화 시작 후 멈추라는 지시에 따라 알람 중 접힘 전환 후 `5분만...`/`알람 끄기` 실제 클릭, 오버레이 허용 + 큰 창 직접 열기 조합은 진행하지 못했습니다(미확인).
- 원복 확인: wm size/density Override 없음(1080x1920@420), 앱 언어 system, appops default, 알람 꺼짐, 기기 언어 en-US. 에뮬레이터는 종료하지 않았지만 열화 상태입니다.

### 최종 재검증 2 (수정 2회차 후, 콜드 부팅) — 최신

- 판정: **조건부(미결: `foldWindow` 재검증 필요)**. `artwork` 회귀는 수정 확인됐지만, `foldWindow`가 3회 모두 "control buttons are not shown"으로 실패했고 원인을 환경(오디오·그래픽 열화)과 테스트 경쟁 중 어느 쪽으로도 확정하지 못했습니다. 새 콜드 부팅 후 재실행이 필요합니다.
- 환경: Usagi_API_35 `-no-snapshot-load -no-snapshot-save` 콜드 부팅, 사용자 잠금 해제 확인, 기동 직후 pcm_writei 0건, wm Override 없음, 앱 언어 system, appops default, 기기 언어 en-US. 빌드: `FINAL2-verify-gradle.txt` 단위 40개 통과, lint 오류 0·경고 9. `adb root`는 쓰지 않았습니다.
- 코드 리뷰: 수정은 최소이고 정확합니다. `mayMoveAlarmControls`는 큰 창이면 true, 폰 크기 창은 `긴 변*9 > 짧은 변*21`(정수)일 때만 true라 일반 폰(16:9~21:9)은 `placeControls()`가 즉시 반환해 버튼이 항상 아래 가운데입니다. 21:9 경계(840x360 false, 850x360 true)와 접힌 길쭉한 창(344x884 true)을 단위 테스트가 값으로 검사하고, 개발이 밝힌 "이동 금지 테스트는 스텁에서도 통과"는 맞는 지적이나 경계 테스트(정확히 21:9 false / 초과 true)가 의미를 보완합니다. `checkControls` 완화는 `mayMoveAlarmControls`가 false인 폰 창에서만 숫자판·버튼 겹침 검사를 건너뛰며, 폰 가로에서 겹침은 기존 동작(수정 전 폰 가로 스크린샷과 계산상 같음)이라 결함을 가리지 않습니다. 잔여 위험: 250ms 갱신 경쟁은 큰 창에서만 남고, 큰 창 검사는 `await`로 기다리지 않고 sleep 기반이며(이전 지적과 동일), 소소하게 `WindowLayoutPolicy`에 문서 주석이 두 개 연달아 남아 있습니다(제안).
- 실행(요약 `FINAL2-verify-run-summary.txt`, 각 결과 `FINAL2-verify-*.txt`): `all` 3회 중 2회(1·2회)는 14개 전부 통과, `artwork` 포함(회귀 해소), 3회는 `preDismiss`의 "missed pre-alarm window"(사전 알림 6초 창을 쓰는 기존 시나리오로 기준선 v1.2.7에서도 같은 실패가 있었음, pcm_writei 155건 누적 구간). `overlayLaunch` 3/3 통과, `widgetHost` 통과, `foldSettings` 통과. 
- 미결 `foldWindow` 3회 모두 실패: 1회 inner-tall, 2회 inner-wide, 3회 inner-wide-dense 의 "ringing: pre: control buttons are not shown"(AlarmInstrumentation.java:732). 직전(수정 2 이전) 통합에서는 3/3 통과했습니다. 환경 상태: pcm_writei가 `all-2`(0→123)부터 증가해 `foldWindow-1` 안에서 366→3,061로 급증했고(오디오 정체), 이후 3,237건. 큰 창에서 알람 화면은 방향 잠금이 풀리고 `onConfigurationChanged`로 뷰를 다시 만드는데, 느린 환경에서 이 재구성이 시험이 잡아 둔 `clock` 참조 이후에 일어나면 이전 뷰를 눌러 새 버튼이 안 보일 수 있다는 것이 가설입니다(확정 아님). 수정 2의 변경(폰 창에서 `placeControls` 반환, 큰 창에서는 동일)이 큰 창 실패의 직접 원인이라는 증거는 없습니다. 새 콜드 부팅 후 `foldWindow` 3회를 다시 돌려야 확정됩니다(이번에는 환경 열화로 거기서 멈춤).
- 추가 확인(알람 중 접힘 근사 후 `5분만...`/`알람 끄기` 클릭, 큰 창+직접 열기)은 `foldWindow`가 통과하지 못해 진행하지 못했습니다(미확인).
- 원복 확인: wm Override 없음(1080x1920@420), 앱 강제 종료·홈, appops default, 알람 꺼짐, 앱 언어 system, 기기 언어 en-US.

### 통합 재검증 (최종, 에뮬레이터 콜드 부팅 후)

- 판정: **수정 필요**. 큰 창 대응(`foldWindow` 6개 창 + 알람 중 창 크기 변경, `foldSettings`)은 통과했지만, 기존 통합 시나리오 `artwork`가 이번 변경(`RingingActivity.placeControls`)으로 인해 실패합니다.
- 환경: Usagi_API_35 `-no-snapshot-load -no-snapshot-save` 콜드 부팅, 사용자 잠금 해제(RUNNING_UNLOCKED) 확인, 기동 직후 pcm_writei 0건, wm Override 없음, 앱 언어 저장값 system, appops default, 기기 언어 en-US. 최신 코드 새 빌드(`FINAL-verify-gradle.txt`): 단위 37개 통과, lint 오류 0개·경고 9개.
- 실행(요약 `verification/FINAL-verify-run-summary.txt`, 각 결과 `FINAL-verify-*.txt`): `foldWindow` 3회 모두 통과(6개 창 + 알람 중 창 크기 변경에서 AlarmService·소리·RingingActivity 같은 인스턴스 유지, 이미 나타난 버튼 유지 확인 포함), `foldSettings` 1회 통과, `overlayLaunch` 3회 통과, `widgetHost` 1회 통과. 실행 사이 상태 점검에서 wm size/density Override는 매번 없었습니다.
- 중요 결함(재현·위치·원인 가설): `all` 3회 중 1회만 14개 통과, 2회(2·3회)는 `artwork`에서 `AssertionError: Alarm controls must be horizontally centered`(AlarmInstrumentation.java:341)로 계측 프로세스가 종료됨(원본 `FINAL-verify-all-2.txt`, `...-3.txt`, `FINAL-verify-crash-buffer-1.txt`). 이때 pcm_writei 누적은 8~26건으로 환경 열화가 아니고, 같은 시나리오는 FOLD-003 이전(ALARM-002)의 `all` 통과 기록에서 통과했습니다. 원인 가설(계산으로 확인): 폰 가로(1920x1080@420) 사전 알림 그림에서 시계 숫자판은 대략 x 1193~1538, y 743~870이고, 버튼 줄은 x 556~1363, y 829~976입니다. 겹치므로 `placeControls()`가 이를 가린다고 판단해 사전 알림에서 버튼 줄을 아래 가운데에서 왼쪽 아래 모서리로 옮깁니다. 기존 `artwork`는 사전 알림에서 버튼이 가운데에 있어야 한다고 검사하므로, 250ms 주기 갱신이 검사 시점보다 먼저 돌면 실패하는 경쟁 상태입니다(1회 통과는 우연). 사용자 관점에서도 "일반 폰 가로 기존 모습 유지"와 달리 폰 가로 사전 알림의 버튼 위치가 바뀝니다. 위치: `RingingActivity.placeControls()`(폰 크기·16:9 창에서 숫자판과 버튼 줄이 조금 겹치는 조건에도 이동), `AlarmInstrumentation.artwork`(라인 341 검사). 조치 방향 제안: 폰 크기 창에서는 이동을 하지 않거나(큰 창·아주 긴 창만), 또는 `artwork`의 기대를 바꾸고 폰 가로 변경을 사용자에게 알리는 선택이 필요합니다(총괄 판단).
- 부차 관찰: 이후 연속 실행으로 에뮬레이터 오디오 오류(pcm_writei)가 `foldWindow-1` 뒤 391건, `foldWindow-3` 뒤 1,973건, 이후 4,217건까지 누적되어 `artwork` 단독 실행이 4분 넘게 멈추는 그래픽 정체(스레드 덤프에 `dequeueBuffer` 대기)가 생겼습니다. 이 구간의 `artwork` 재현은 환경 열화로 분류하고 새 콜드 부팅이 필요해 중단했습니다. 위 `artwork` 실패 2건은 누적 전(8~26건) 발생이라 환경 탓이 아닙니다. 참고: 스레드 덤프를 받으려고 `adb root`를 실행하면 adbd가 재시작되어 진행 중이던 계측이 끊기므로(내 실수) 해당 실행은 무효입니다.
- 원복 확인: wm size/density reset, 앱 강제 종료·홈, appops default, 알람 꺼짐, 앱 언어 저장값 system(키 제거), 기기 언어 en-US, 자동 회전 on.

### 재검증 1회차 (수정 후, 소리 없는 항목만)

- 판정(소리 없는 항목): **통과(조건부)**. 낮은 큰 창에서 `사전 알림` 줄이 잘리던 문제는 수정됐고, 이번에 새로 만든 스크린샷과 `foldSettings` 실행으로 확인했습니다. 단, 시나리오 실행이 환경 문제로 간헐 실패했고(아래), 알람 소리를 쓰는 통합 항목은 지시대로 실행하지 않았습니다.
- 미실행(에뮬레이터 콜드 재기동 후로 남김): `foldWindow` 전체, `overlayLaunch`, `all`, `widgetHost`, 알람이 울리는 중 크기 변경(b), 폴더블 실제 접기. 이들은 이번 수정 뒤에도 판정하지 않습니다(검증 불가 유지).
- 실행: `.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --rerun-tasks` 성공(`FOLD-003-verify2-gradle.txt`, `...-lint.txt`). 단위 테스트 37개 통과(WindowLayoutPolicyTest에 낮은 큰 창 압축 케이스 추가), lint 오류 0개·경고 9개(변화 없음).
- 코드 리뷰: 수정은 최소입니다. `needsCompactLayout`은 `LARGE`이고 높이 640dp 미만일 때만 true라 폰 크기 창에는 항상 false입니다. MainActivity에서 폰은 스크롤 영역 아래 여백 52dp와 가운데 정렬 푸터(아래 여백 20dp)를 그대로 쓰고(`largeWindow ? 0 : dp(52)`, `largeWindow ? END : CENTER_HORIZONTAL`), 큰 창만 스크롤 영역을 창 끝까지 늘려 오른쪽 아래 푸터로 옮깁니다. 폰 세로 스크린샷이 수정 전과 같아 폰 UI 불변을 확인했습니다. 한국어 주석이 이유를 설명하고 읽기 쉽습니다. 범위 초과는 없습니다.
- `checkFullyVisible`(`getGlobalVisibleRect` 크기 == 뷰 크기)은 이전 검사의 미탐을 실제로 잡습니다. 이전 `checkInsideWindow`는 잘리지 않은 뷰 상자 기준이라 스크롤 영역 아래에 잘린 뷰를 못 찾았고, 새 검사는 개발 증거(`FOLD-003-fix1-foldSettings-before.txt`)에서 옛 앱에 대해 `lead_seconds is cut off: visible 306x85 of 306x156`으로 실패했습니다(내가 옛 APK로 직접 재현하지는 않았음, 로그만 확인). 한계: 푸터처럼 투명하게 겹치는 오버레이는 클리핑이 아니라 잡히지 않아 기존 상자 교차 검사가 맡습니다.
- `foldSettings` 직접 실행(내 실행, 소리 없음, 원본 `FOLD-003-verify2-foldSettings-1~6.txt`): 통과 4회(1·3·5·6회). 나머지 2회는 환경 문제 — 2회는 계측 실행 중 `system_server`가 워치독으로 종료("System has crashed", logcat `WATCHDOG KILLING SYSTEM PROCESS: Blocked in monitor WindowManagerService … 72s`, `FOLD-003-verify2-logcat-after-crash.txt`)되어 재부팅됐고 그동안 `wm size` 2176x1812@520 상태가 남았으며, 4회는 `captureWidget`의 화면 캡처가 null이라 NPE로 중단(에뮬레이터 그래픽 정체). 재부팅 직후에는 사용자가 아직 잠금 해제 전이라 계측이 즉시 죽었고(`SharedPreferences ... until after user (id 0) is unlocked`), 잠금 해제를 기다린 뒤 다시 통과했습니다. 앱 코드가 아닌 에뮬레이터·시스템 안정성 문제로 판단하지만, `wm size` 변경이 이 에뮬레이터에서 워치독을 유발했을 가능성은 배제하지 못했습니다.
- 스크린샷 6장(`FOLD-003-verify2-*-settings.png`, 내 실행) + 개발 증거 6장(`FOLD-003-fix1-after-*.png`)을 모두 봤습니다. 근사 창이며 실제 삼성 기기 사양이 아닙니다.
  - 낮은 큰 창 inner-wide-dense(2176x1812@520, 약 670x558dp): 왼쪽 열 `사전 알림` 줄과 상태 문구까지 모두 보이고 푸터는 오른쪽 아래로 옮겨져 어떤 카드와도 겹치지 않음. 글자 크기는 폰과 같은 1.0배로 줄었고 2열 유지. 이전 의심(잘림) 해소.
  - inner-wide(2176x1812@374), inner-tall(1812x2176@374): 2열, 폭 제한, 푸터 오른쪽 아래, 잘림·겹침 없음.
  - folded(904x2316@420)와 phone-portrait(1080x1920): 폰과 같은 1열. 맨 아래 `English` 항목이 푸터 뒤에 반쯤 가려지나 스크롤하면 닿는 기존 폰 동작(수정 전과 동일).
  - phone-landscape: 내 실행 캡처는 빈 화면(그래픽 정체)이라 평가 불가. 개발이 찍은 `FOLD-003-fix1-after-phone-landscape-settings.png`는 수정 전 폰 가로(ALARM-002 때 `...landscape-ko-panel` 등)와 같은 모습.
  - 개발 보고의 "inner-tall 한 장은 빈 캡처"는 현재 파일(`FOLD-003-fix1-after-inner-tall-settings.png`)에서는 정상 이미지로 확인됨.
  - 내 실행 중 한 번은 시스템 설정 앱의 "Settings isn't responding" 대화 상자가 캡처에 찍혔고(`...verify2-now.png`는 정상 홈), 이 때문에 5회째 캡처는 사용하지 않고 6회째 캡처로 평가했습니다.
- 원복 확인: `wm size`/`wm density` 모두 reset(1080x1920, 420), 앱 강제 종료 후 홈 화면, appops SYSTEM_ALERT_WINDOW=default, 알람 꺼짐(`enabled=false`), 기기 언어 en-US, 자동 회전 on. 계측 실행이 비정상 종료된 탓에 앱 언어 저장값이 `ko`로 남았기에 prefs에서 `language` 키를 제거해 기본(시스템 따라가기)으로 되돌림(BOM 없이 UTF-8로 재작성해 앱이 정상 파싱함을 확인).
- 제안: ① 계측 시나리오가 중간에 죽으면 `wm size`와 앱 언어가 남으므로 시작 시 복구 단계를 두거나 시나리오 문서에 확인 절차를 명시. ② `captureWidget`은 null 비트맵이면 NPE로 시나리오 전체를 실패시키므로 캡처 실패는 기록만 하고 건너뛰는 편이 안정적. ③ 압축 조건(높이 640dp 미만)의 경계(640dp 근처 큰 창)는 시험 창이 없어 미검증.

### 1회차 (최초 검증, 수정 전)

- 판정: **검증 불가(통합 항목)**. 빌드·단위 테스트·lint·코드 리뷰·기존 스크린샷 검토만 수행했고, 에뮬레이터 통합 실행(`foldWindow`·`overlayLaunch`·`all`·`widgetHost`)은 하지 못했습니다. 제품 결함으로 확정된 것은 없고, 확인이 필요한 의심 1건이 있습니다(아래).
- 통합을 못 한 사유: 기존 emulator-5554는 오디오(pcm_writei) 오류가 쌓여 `all`을 믿을 수 없고 종료도 금지라서, 같은 AVD를 `-read-only -no-snapshot -port 5556`로 하나 더 띄우려 했습니다. 기동 로그에 "Another emulator instance is running. Please close it or run all emulators with -read-only flag."로 거부됐습니다(기존 인스턴스가 `-read-only` 없이 AVD 잠금을 잡고 있음). AVD 복제 등 우회는 하지 않았습니다. 원본 `verification/FOLD-003-verify-emulator-boot.txt`, `...boot-err.txt`. 새 인스턴스가 없어 pcm_writei 0건 확인과 누적 시점 기록도 못 했고, 기존 인스턴스에는 어떤 시험도 하지 않아 복원할 상태 변경이 없습니다.
- 실행 명령: `.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --rerun-tasks` 성공(`FOLD-003-verify-gradle.txt`, `...-lint.txt`). 단위 테스트 36개 통과(기존 25 + WindowLayoutPolicyTest 6 + ArtworkCropTest 5), lint 오류 0개·경고 9개(ALARM-002와 동일).
- 테스트 품질 리뷰(코드): WindowLayoutPolicyTest는 폰 세로·가로, 접힌 좁은 창, 접힌 폴더 근사 창(673x586 등), 태블릿, 짧은 변 540dp 경계(540 LARGE / 539 PHONE)를 값으로 검사해 구현 흉내가 아닙니다. ArtworkCropTest는 13개 비율(0.5~2.4)에서 그림이 창을 빈틈없이 덮는지, 캐릭터·시계(KEEP)가 0.8 이상 비율에서 안 잘리는지, 시계 숫자판(ESSENTIAL)은 모든 비율에서 안 잘리는지, 폰 가로 16:9·2:1·2.4:1에서 기존 가운데 크롭과 같은 값인지, 그림 비율과 같을 때 이동 0인지를 불변식으로 검사해 실제 동작을 확인합니다. red 단계는 사후 재현할 수 없어 미확인입니다.
- `foldWindow` 시나리오 리뷰(코드): 창 6개마다 레이아웃 모드·핵심 뷰의 창 안 위치·시계 숫자판·버튼 비겹침·울리는 중 창 변경 시 서비스·같은 액티비티 인스턴스·소리 유지를 검사하고 finally에서 `wm size/density reset`을 호출해 복원 설계는 적절합니다. 다만 `waitForIdleSync`를 고정 sleep으로 바꾼 것은 느린 환경에서 레이아웃이 끝나기 전에 검사해 거짓 통과/실패가 나올 여지가 있고(재시도 `await` 없음), 큰 창 검사는 스크롤에 가려진 부분이 아니라 잘리지 않은 뷰 상자 기준이라 "화면에 실제로 보이는가"를 놓칠 수 있습니다(아래 의심 사항과 연결).
- 개발 스레드 덤프 검토(`FOLD-003-threaddump-1.txt`, `...-all-artwork.txt`): 두 덤프 모두 메인 스레드가 `ThreadedRenderer.draw → DrawFrameTask.drawFrame`에서 렌더 스레드를 기다리고 있고, 덤프 1의 RenderThread는 `eglSwapBuffers → BufferQueueProducer::dequeueBuffer`에서 버퍼를 기다립니다(에뮬레이터 그래픽 버퍼 정체). `AlarmService.play`는 `AudioTrack.write → obtainBuffer`에서 대기해 오디오 쪽도 정체되어 있습니다. `onConfigurationChanged`가 반복 호출되는 스택은 없어 "앱 구성 변경 반복이 아니라 에뮬레이터 그래픽·오디오 정체"라는 개발 주장은 덤프와 일치합니다. 덤프 2의 RenderThread는 JNI 콜백 대기라 덤프 자체가 만든 일시 정지일 수 있어 증거 가치는 약합니다. `all`이 `artwork`(변경 대상이 아닌 원래의 `waitForIdleSync`)에서 20분 넘게 멈춘 것은 이번 변경이 아닌 환경 정체와 같은 양상이지만, 같은 인스턴스에서 직전 코드와 대조하지 못해 확정은 아닙니다(ALARM-002 때도 같은 인스턴스에서 `artwork`가 오디오 오류 누적 뒤 실패).
- 스크린샷 18장 직접 검토(개발이 `foldWindow` 실행 중 저장한 `FOLD-003-*.png`, 내가 새로 찍은 것이 아님). 근사 창이며 실제 삼성 기기 사양이 아닙니다.
  - 폰 세로·가로(1080x1920, 1920x1080): 설정 2열 아님, 알람 화면 가로. 시계 숫자판이 얼굴 아래 오른쪽에 있고 사전(18:57/18:58)·본 알람 모두 기존 모습과 같고(LANG-001·ALARM-002 때 스크린샷과 대조) 버튼은 아래 가운데에서 숫자판과 겹치지 않음.
  - 폴더 안쪽 가로(2176x1812@374, 약 931x775dp): 설정이 2열(시간·반복 | 권한 안내·언어), 폭 제한, 하단 버전과 비겹침. 알람 화면은 그림이 창을 채우고 시계 숫자판 온전, 버튼은 숫자판 아래 가운데.
  - 폴더 안쪽 세로(1812x2176): 설정 2열, 아래는 토끼 그림 일부. 알람 사전은 헤드업 알림 카드가 위쪽을 덮은 순간이 찍혔지만 시계는 온전. 본 알람은 캐릭터·시계 보임, 버튼 정상.
  - 접힌 좁은 창(904x2316): 설정은 세로 1열로 폰과 같음(맨 아래 English 항목은 스크롤 영역). 알람은 폰처럼 가로(2316x904)로 표시되고 시계·버튼이 겹치지 않음(버튼이 캐릭터 몸 위에 걸치는 것은 장식 영역).
  - 의심(확인 필요, 중요): 큰 창 근사 중 가장 작은 쪽인 `inner-wide-dense`(2176x1812@520, 약 670x558dp, 큰 창 임계값 540dp 근처)의 설정 화면에서 왼쪽 열 맨 아래 `사전 알림` 입력줄이 하단 버전 표기 영역에 가려 반쯤 잘려 보입니다(`FOLD-003-inner-wide-dense-settings.png`). 스크롤하면 닿는 것으로 보이나, 시나리오는 큰 창에서 "스크롤 없이 모두 보임·푸터와 비겹침"을 검사하는데도 통과로 기록되어 있어 검사와 화면이 어긋납니다. 새 인스턴스가 없어 재현·스크롤 확인을 못 했습니다.
- 요구사항 (a)~(f) 판정: (a) 코드·기존 스크린샷 상 충족(위 의심 1건 제외), 폰 모습 불변은 ArtworkCropTest와 스크린샷 대조로 확인. (b) 코드 상 `configChanges`로 재생성 없이 뷰만 재구성해 서비스·소리가 유지될 설계이나 **실행 미검증**(`checkResizeWhileRinging` 미실행). (c) MainActivity는 재생성하며 `onPause`의 `save(false)`로 입력값 저장하는 설계이나 **실행 미검증**. (d) 위젯 파일은 이번 변경에 없음(코드 상 영향 없음), 크기 변화 시 실제 영향은 **미검증**. (e) 범위: 플립 커버·탁상 자세·의존성·릴리스 변경 없음(파일 목록·매니페스트로 확인). (f) LANG-001·ALARM-002 회귀(`overlayLaunch`·언어)는 단위 테스트만 통과, **실행 미검증**.
- 미검증: 실제 삼성 One UI 폴더블·접기/펴기 이벤트 실제 흐름, 플립 커버·탁상 자세(미지원), 실기기 절전·제조사 정책, 위 통합 항목 전부(`foldWindow` 3회, `overlayLaunch`, `all` 3회, `widgetHost`), 폴더블 접기 시 `onUserLeaveHint`로 알람이 꺼지는지 여부(실제 접기와 `wm size` 변경은 다를 수 있음).

## v1.3.0 사용 중 알람 화면 직접 표시 (ALARM-002) · 2026-11-02

### 최종 재검증 2 (FOLD-003 수정 2회차 코드, 콜드 부팅) — 최신

- 판정: **통과**. 새 콜드 부팅(pcm_writei 0건)에서 `overlayLaunch` 3/3, `widgetHost` 1/1 통과했고 `all` 14개가 3회 중 2회 전부 통과했습니다(실패 1회는 기존 타이밍 시나리오 `preDismiss`로 ALARM-002와 무관). 원본 `FINAL2-verify-overlay-1~3.txt`, `...all-1~3.txt`. 직접 열기와 큰 창 조합의 알람 중 클릭은 `foldWindow` 미결로 확인하지 못했습니다.

### 통합 재검증 (최종, 콜드 부팅 후, FOLD-003 포함 최신 코드) — 최신

- 판정: **통과**(이전 조건부에서 상향). 최신 코드·새로 콜드 부팅한 에뮬레이터(pcm_writei 0건)에서 `overlayLaunch` 3회 모두 통과, `widgetHost` 통과, `all`은 1회에서 14개 전부 통과(`FINAL-verify-all-1.txt`)했습니다. `all` 나머지 2회의 실패는 `artwork`의 `Alarm controls must be horizontally centered`로, ALARM-002 기능이 아니라 FOLD-003의 버튼 위치 변경 때문입니다(자세한 내용은 FOLD-003 섹션). `overlayLaunch`는 직접 열기·거부 폴백·안내 패널을 포함하고 큰 창과의 조합은 `foldWindow`(알람 중 창 변경)에서 서비스·화면 유지로 확인했습니다. 원본 `FINAL-verify-overlay-1~3.txt`, `FINAL-verify-run-summary.txt`.
- 알람 중 접힘 근사(1080x1920↔2176x1812 등) 후 `5분만...`/`알람 끄기` 버튼 실제 클릭은 이번 회차에서 별도로 하지 않았습니다(`foldWindow`는 이미 나타난 버튼 유지만 검사). 미확인으로 남깁니다.

- 판정: **통과(조건부)**. 제품 결함은 찾지 못했습니다. 통합 `all`은 새 빌드에서 5회 중 2회만 14개가 전부 통과했고, 나머지 3회는 에뮬레이터 오디오(pcm_writei) 오류가 쌓인 뒤에 발생했습니다(아래).
- 환경: 에뮬레이터 Usagi_API_35(API 35)를 `ANDROID_AVD_HOME=C:\repository\.android-tools\avd`, `-no-snapshot-load -no-snapshot-save`로 콜드 부팅. 대상은 미커밋 작업 트리(1.3.0 / versionCode 7).
- 기동 직후 logcat(`verification/ALARM-002-verify-logcat-boot.txt`, 약 3만 줄)에 `pcm_writei` 오류 0건.
- 실행 명령: `.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --rerun-tasks` 성공(`ALARM-002-verify-gradle.txt`). 단위 테스트 25개 통과(ScreenLaunchPolicyTest 3 포함), lint 오류 0개·경고 9개(`ALARM-002-verify-lint.txt`).
- `overlayLaunch` 단독 3회 모두 통과(`ALARM-002-verify-overlay-1~3.txt`): 권한 허용 시 홈 화면 위에서 사전 알림 시작 때 서비스가 알람 화면을 직접 열고 본 알람 전환 후에도 화면 하나가 유지됨, 거부 시 화면은 열리지 않고 알림만 표시됨, 설정 화면 안내 패널의 표시/숨김. 시험 뒤 appops는 default로 복원됨.
- `all`(14개) 새 빌드 5회(`ALARM-002-verify-all-1~5.txt`, 각 실행 뒤 logcat `...logcat-all-N.txt`): r1·r2 통과. r3는 `sequence`의 "Ringing screen stop failed", r4·r5는 `artwork`의 "Pre artwork missing"으로 실패. logcat에서 `pcm_writei failed ... I/O error`가 r1·r2는 0건, r3에서 시작(207건, 실패 시각 직전부터)해 r4 1,119건, r5 1,442건으로 누적됨. 즉 오디오 오류가 시작된 뒤의 실행만 실패했고, 사전 알림 창을 쓰는 같은 시나리오(`artwork`, `preDismiss`, `snooze`)는 이전 LANG-001 기준선(v1.2.7)에서도 같은 방식으로 실패했습니다. 환경 문제로 판단하지만, r3의 `sequence` 실패가 직접 열기와 무관하다는 것을 별도 대조로 증명하지는 못했습니다(권한이 default일 때 직접 열기 코드는 동작하지 않음).
- 수동 확인(실제 알람을 UI로 설정해 확인, 스크린샷 `ALARM-002-verify-*.png`):
  - (a) 권한 허용 + 설정 앱이 앞에 있을 때 알람 시작: 사전 알림 시작 시(17:52:58)와 본 알람 시(17:53:07) 모두 `mCurrentFocus`가 RingingActivity(`...allowed-main.png`).
  - 알람 중 버튼: 터치하면 `5 more min...`/`Turn off alarm` 표시. 5분만은 알람을 멈추고 snooze 값을 저장한 뒤 홈으로 이동, 끄기는 서비스를 멈추고 snooze 값과 알림을 지움.
  - (b) 권한 거부 + 화면 켜짐: `overlayLaunch`가 검증(화면 미오픈, 알림만).
  - (c) 권한 거부 + 화면 꺼짐(KEYCODE_SLEEP, Asleep 확인) 상태에서 5분만으로 예약된 알람이 울릴 때: 화면이 켜지고(Awake) RingingActivity가 최상위(`...denied-screenoff-ringing.png`). 기존 전체 화면 intent 동작 유지. 이 에뮬레이터는 잠금 화면이 설정되어 있지 않아 키가드 해제 흐름은 확인하지 못했습니다.
  - (d) 중복: 정책 단위 테스트와 사전→본 알람 전환 후 화면 하나 유지(`overlayLaunch`)로 확인. 수동으로 화면을 이미 띄운 뒤 시작하는 경우는 따로 실행하지 않았습니다.
  - (e) 안내 패널: 권한 없음에서 표시, `설정 열기` → 시스템의 다른 앱 위에 표시 화면으로 이동, 권한 허용 후 뒤로 돌아오면 패널이 사라짐(`...panel-hidden-after-return.png`). 가로 모드 한국어 화면에서 문구·버튼 잘림 없음(`...landscape-ko-panel.png`), 영어 세로 화면 정상(`...settings-nopermission.png`).
- `widgetHost` 통과(`ALARM-002-verify-widgetHost.txt`).
- 동작상 주의(제품 결함 아님): 권한이 있으면 사전 알림 시점에 사용자가 쓰던 앱 위로 알람 화면이 바로 올라옵니다. RingingActivity는 `onUserLeaveHint`에서 알람을 끄는 기존 동작이므로, 사용자가 다른 앱을 쓰다가 화면이 가로채지면 홈·다른 앱으로 나가는 순간 알람이 종료됩니다.
- 복원 확인: appops SYSTEM_ALERT_WINDOW=default, 앱 언어 저장값 system, 알람 꺼짐(`enabled=false`, 예약 없음), 기기 언어 en-US, 자동 회전 on·user_rotation 0.
- 미검증: 실제 휴대폰·제조사별 백그라운드 액티비티 시작 제한(Android 10+의 다른 앱 위에 표시 외 조건), 잠금 화면(PIN) 상태, 폴더블, 직접 열기가 막혔을 때(`startActivity` 예외)의 폴백 동작, 권한이 허용된 상태에서 화면이 이미 보일 때 수동 중복 확인.
- 범위: 폴더블·위젯·의존성·릴리스 변경 없음(변경 파일 검토). 권한 선언은 `SYSTEM_ALERT_WINDOW` 하나만 추가됨.

## v1.3.0 한국어·영어 언어 설정 (LANG-001) · 2026-11-01

### 최종 재검증 2 (FOLD-003 수정 2회차 코드, 콜드 부팅) — 최신

- 판정: **통과**. `language` 시나리오를 포함한 `all` 14개가 새 콜드 부팅에서 3회 중 2회 전부 통과했습니다(`FINAL2-verify-all-1.txt`, `...-2.txt`). 단위 테스트 40개(LanguagePolicyTest 6 포함) 통과. 3회차 실패는 `preDismiss` 타이밍 시나리오로 언어와 무관합니다.

### 통합 재검증 (최종, 콜드 부팅 후, FOLD-003 포함 최신 코드) — 최신

- 판정: **통과**(이전 조건부에서 상향). 새로 콜드 부팅한 에뮬레이터에서 `language` 시나리오를 포함한 `all` 14개가 1회 전부 통과했습니다(`FINAL-verify-all-1.txt`). 나머지 2회는 `artwork`에서 FOLD-003의 버튼 위치 변경으로 실패했고 `language`까지 도달하지 못했습니다(언어 기능 결함 아님). 단위 테스트 37개(LanguagePolicyTest 6 포함) 통과. 언어 변경·시스템 따라가기·force-stop 후 유지는 앞선 회차의 실화면 확인을 유지합니다.

### 2회차 재검증 (2026-11-02, 에뮬레이터 재기동 후) — 최신

- 판정: **통과(조건부)**. 이번 변경으로 인한 결함은 찾지 못했습니다. 다만 통합 `all`은 새 빌드에서 5회 중 3회만 14개가 전부 통과했고, 나머지 2회의 실패는 v1.2.7 기준선에서도 재현되는 기존 타이밍 불안정입니다(아래).
- 환경: 멈춘 에뮬레이터를 사용자가 종료한 뒤 `ANDROID_AVD_HOME=C:\repository\.android-tools\avd`로 Usagi_API_35를 새로 기동(부팅 완료 확인). 1회차와 같은 APK(1.3.0 / versionCode 7)를 재설치.
- 통합 `all`(14개, language 포함) 새 빌드 5회: 통과 3회(r1·r2·r5), 실패 2회 — r3 `preDismiss` "missed pre-alarm window", r4 `artwork` "Pre artwork missing". `language` 시나리오는 통과한 3회 모두 통과. 원본 `verification/LANG-001-verify-r2-all-1~5.txt`.
- 기준선 대조: 같은 에뮬레이터에서 HEAD(v1.2.7, 13개) 소스를 별도 폴더에 빌드해 `all` 반복 실행 → 4회째는 `sequence`에서 12분 넘게 멈춰 실행기를 직접 중단했고(5회째 미실행), 완료된 첫 3회는 모두 실패(`Second snooze failed`, `Pre artwork missing`, `Dismiss test missed pre-alarm window`). 같은 실패가 변경 전 코드에서도 나오므로 이번 변경 원인이 아니라 사전 알림 6초 창을 쓰는 시나리오의 기존 타이밍 민감성(느린 에뮬레이터)으로 판단합니다. 원본 `LANG-001-verify-baseline-1.2.7-all-*.txt`. 1회차의 `homeDismiss` "Process crashed"와 3회차 멈춤은 먹통이 된 에뮬레이터 상태에서 발생했고 재기동 후에는 재현되지 않았습니다(crash 버퍼 비어 있음).
- 기기 언어별 시스템 따라가기(루트 adb로 `persist.sys.locale` 변경 후 복원, 앱 언어=시스템): ko-KR → 한국어 화면, ja-JP → 영어 화면, en-US → 영어 화면. `verification/LANG-001-verify-sys-ko-KR.png`, `-sys-ja-JP.png`, `-sys-en.png`. 시험 후 기기 언어 en-US, 자동 회전 설정 원상 복구.
- 명시 선택: 영어 라디오 선택 → 저장소 값 `en`, `am force-stop` 후 재시작해도 영어 유지, 기기 언어 ko-KR에서도 명시 영어 유지(`...en-after-forcestop.png`, `...explicit-en-on-ko-device.png`). 다시 `시스템 설정 따라가기`로 되돌려 저장값 `system` 확인.
- 가로 모드 영어 화면: 문구 잘림·겹침 없음, 언어 패널과 하단 버전 표기 정상(`...landscape-en-top.png`, `...landscape-en-bottom.png`). 세로 화면에서는 언어 패널의 세 번째 항목(English)이 스크롤해야 보이는 위치이며, 스크롤 후 선택 가능(기존 화면 구성 그대로).
- `widgetHost` 통과(`LANG-001-verify-widgetHost.txt`).
- 서명·업그레이드: 새 APK와 기존 v1.2.7 APK의 서명 인증서 SHA-256 지문이 동일(`e72bbefa…50f3`, 공개 지문만 기록). 새 APK의 versionName 1.3.0 / versionCode 7 확인. 에뮬레이터에서 v1.2.7 설치 → 1.3.0 덮어쓰기 설치 성공(versionName 1.2.7 → 1.3.0 확인), 설치 후 앱 정상 실행. 업그레이드 뒤 설정값 보존은 시험용으로 prefs를 직접 작성해 확인한 것이라 실사용 데이터 보존의 증거로는 약합니다. 구버전 한국어 오류 문구는 앱이 시작할 때 오류를 지우므로 화면에서 재현하지 못했고, 위젯 경로의 원문 표시는 코드 검토로만 확인했습니다.
- 여전히 미검증: 언어 변경 직후 실제 홈 화면 위젯의 문구 갱신(`updateAll` 호출은 코드 검토, `language` 시나리오는 `views()` 결과만 검사), 알람이 울리는 중 언어 변경, 실제 휴대폰. 제안: 사전 알림 6초 창을 쓰는 시나리오의 대기·창 여유를 늘려 반복 실행 안정성 개선(제품 코드가 아니라 테스트 쪽 별도 작업).
- 테스트 위생: 계측 실행이 도중에 죽으면 `language` 저장값이 `ko`로 남습니다(시작 시 고정, 끝에서 복원). 이 경우 기기 언어 확인 전에 저장값을 `system`으로 되돌려야 합니다.

### 1회차 (2026-11-01, 에뮬레이터 멈춤으로 중단)

- 판정: **검증 불가** (에뮬레이터 통합 `all`·`language` 시나리오를 완주하지 못함). 정적 리뷰·빌드·단위 테스트·lint는 확인했고, 제품 결함으로 확정된 항목은 아직 없습니다.
- 환경: Windows, JDK 17, Gradle wrapper, 에뮬레이터 Usagi_API_35(emulator-5554, 개발이 켜 둔 인스턴스). 대상은 미커밋 작업 트리(versionName 1.3.0 / versionCode 7).
- 실행 명령: `.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --rerun-tasks` (개발 결과를 재사용하지 않고 전체 재실행) → 성공. 원본 `verification/LANG-001-verify-gradle.txt`, `LANG-001-verify-lint.txt`.
- 단위 테스트 22개 통과(기존 16 + LanguagePolicyTest 6). 실패 확인(red) 후 구현했다는 개발 주장은 사후에 재현할 수 없어 미확인입니다.
- lint 오류 0개, 경고 9개(기존 4 + 신규 5: ApplySharedPref, AppBundleLocaleChanges, PluralsCandidate, TypographyEllipsis 2건). 배포가 APK이므로 차단 사유는 아니나 제안으로 남깁니다.
- 문구 대조: 이전(HEAD) 소스의 한국어 문구 리터럴 57건을 자동 추출해 `values-ko`와 비교, 전부 일치(누락 0). 영어·한국어 키 집합 동일(언어 이름 2건은 `translatable=false`), 포맷 인자 일치, 미사용 문자열 0건. Java/Manifest의 한국어 사용자 문구 잔여 0건(주석 제외). `Locale.KOREA` 제거, `Locale.US`(위젯 AM/PM)·`Locale.ROOT`(UsagiClockView) 유지 확인.
- 업그레이드: 구버전이 prefs에 저장한 한국어 오류 문구는 `errorMessage()`의 default 분기에서 원문 그대로 표시되어 깨지지 않음(코드 검토). 실기기 업그레이드 설치는 미실행.
- 통합 `all` (원본 `verification/LANG-001-verify-instrument-all*.txt`):
  - 1회차: settings~preDismiss 6개 통과 후 `artwork`에서 "Pre artwork missing" 실패. 같은 APK에서 `artwork` 단독 재실행은 통과(`...artwork-rerun.txt`). 간헐 실패로 보이나 원인 미규명.
  - 2회차: artwork·backDismiss까지 통과, `homeDismiss` 시작 직후 "Process crashed"로 계측 종료.
  - 3회차: `settings` 시작 후 에뮬레이터 adb가 응답하지 않아 중단. 에뮬레이터 재기동이 필요했으나 프로세스 종료가 허용되지 않아 진행하지 못함.
  - 따라서 `all` 13개+`language` 14개 통과는 확인하지 못했고, `language` 시나리오는 한 번도 실행하지 못했습니다.
- 미실행(차단): `language` 시나리오, 기기 언어 ko/en/ja에서 시스템 따라가기 화면, `am force-stop` 후 영어 유지, 언어 변경 직후 위젯 갱신, 울리는 중 언어 변경, 가로 모드·영어 문구 UI 잘림 스크린샷, apksigner 서명 비교, `widgetHost`.
- 코드 검토 소견: 테스트의 `language`는 실제 `AlarmWidget.views`·화면·서비스 경로를 사용해 구현 흉내가 아니며, 사전 알림 30초 확대는 해당 시나리오에만 적용되어 다른 시나리오 의미는 바뀌지 않음. 다만 위젯은 `views()`만 검사하고 `updateAll` 호출 여부는 검사하지 않습니다. 울리는 화면(RingingActivity)이 열려 있는 동안 설정 언어가 바뀌면 그 화면은 재생성 전까지 이전 언어를 유지합니다(알람 중 설정 접근은 드물어 제안 수준).

## v1.2.7 현재 시각 위젯 · 2026-10-31

- 대상: 버전 1.2.7(versionCode 6), 에뮬레이터 Usagi_API_35(API 35, 정확한 알람 허용 상태). 실제 휴대폰은 검증하지 않았습니다.
- `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 성공. 단위 테스트 16개 통과.
- Android 통합 시나리오 `all` 13개 통과(settings, sequence, noLead, cancel, repeat, preDismiss, artwork, backDismiss, homeDismiss, snoozePre, snoozeMain, widget, widgetPeriod). 별도 `widgetHost` 시나리오도 통과: 콜론 깜박임과 알람이 꺼진 상태의 분 단위 시각 갱신 확인.
- APK 서명 인증서가 v1.2.6과 같아 기존 설치본 위에 업데이트됩니다.
- 미검증: 실제 휴대폰의 절전·배터리 최적화에서의 갱신 시점, 정확한 알람 권한이 없는 기기의 갱신 지연.

## 현재 시각 위젯 및 다시 알림 홈 이동 · 2026-10-31

- 다시 알림의 사전·본 알람 시나리오 2개에서 5분 예약과 반복 예약 유지, 홈 런처로의 이동을 검증했습니다.
- 위젯은 예약 시각 대신 현재 시각을 표시하며, 얼굴 버튼은 기존 예약 켜기·끄기 동작을 유지합니다.
- widgetPeriod·widget·widgetHost 통합 시나리오 3개 통과. 알람을 끈 실제 AppWidgetHost에서 분 경계를 기다려 현재 시각이 자동으로 바뀌는 것을 확인했습니다.
- 디지털 서체와 AM 위쪽/PM 아래쪽 배치를 유지합니다. 매 분 RTC 비깨우기 예약을 사용하며, 정확한 알람 권한이 없으면 시스템 정책에 따라 갱신이 늦어질 수 있습니다.
- 빌드·단위 테스트 16개·lint 통과. 실제 휴대폰의 절전 정책에 따른 갱신 시점은 별도 검증이 필요합니다.

## v1.2.6 AM/PM 위치 수정 · 2026-10-30

- `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 성공. 단위 테스트 16개 통과.
- Android 15에서 widgetPeriod·widget·widgetHost 통합 시나리오 3개 통과.
- 자정(00시)·오전(08시)·정오(12시)·오후(20시)의 켜짐/꺼짐 8가지 조합에서 실제 숫자 비트맵의 AM/PM 픽셀 위치를 검사했습니다. AM은 위쪽, PM은 아래쪽에 표시됩니다.
- 위젯 켜기·끄기, 아래쪽 중앙 기준 좌우 15° 회전, 1초 간격 전환 및 모몽가 정지 동작을 재검증했습니다.
- APK versionName 1.2.6 / versionCode 5 및 기존 버전과 동일한 서명 인증서를 확인했습니다.

## v1.2.5 위젯 디자인 및 동작 · 2026-10-30

- 3×1 위젯의 AM/PM·디지털 숫자·얼굴 버튼 및 켜짐/꺼짐 액정 색상을 Android 15 홈 런처에서 확인했습니다.
- 실제 AppWidgetHost에서 얼굴 버튼 켜기·끄기, 1초 간격의 네 자세 전환, 아래쪽 중앙 회전축, 좌우 15° 각도와 모몽가 상태의 정지를 검증했습니다.
- 위젯 숫자는 DSEG7 Classic 서체를 비트맵으로 그려 기본 글꼴로 대체되는 것을 방지했습니다. 폰트의 OFL 1.1 라이선스는 앱 assets에 포함했습니다.
- 실제 휴대폰과 제조사별 런처의 배치·동작 검증은 남아 있습니다.

## 다시 알림 및 홈 화면 위젯 · 2026-10-30

- v1.2.0 배포 APK는 versionCode 3 / versionName 1.2.0을 확인했고, v1.1 배포 APK와 서명 인증서 SHA-256이 동일합니다. 에뮬레이터 덮어쓰기 설치 및 다시 알림 시나리오 2개를 재검증했습니다.

- `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 성공. 단위 테스트 16개 통과, lint 오류 0개·기존 항목 경고 4개.
- Android 15 에뮬레이터 통합 시나리오 12개 통과: 기존 9개, 사전 알림 중 다시 알림, 단발 본 알람의 다시 알림, 위젯 상태 및 켜기·끄기.
- 다시 알림은 실제 AlarmManager에 현재 시각에서 5분 후로 등록되는지 검사했습니다. 전달·재생 검증 단계에서만 예약을 2초 후로 앞당겼으며, 실제 5분 대기 검증은 하지 않았습니다.
- 다시 알림 복원, 반복 예약 유지, 사전 알림 없는 재생, 연속 다시 알림, 전체 끄기 시 취소를 검증했습니다.
- 최종 APK에서 artwork 시나리오를 재실행해 버튼 숨김·터치 표시·가운데 하단 배치·본 알람 전환 후 유지·종료를 확인했습니다.
- 별도 widgetHost 시나리오 통과: 실제 Android AppWidgetHost에 위젯을 바인딩하여 표시·버튼 동작·상태 갱신을 확인했습니다. 캡처 `C:/repository/usagi-widget-preview.png`를 시각 확인했습니다. 테스트용 바인딩 허용은 검증 후 해제했습니다.
- 검증용 APK: `C:/repository/usagi_alarm-snooze-widget-debug.apk`. apksigner 서명 검증 통과. 실제 휴대폰과 제조사별 홈 런처 검증은 남아 있습니다.

검증일: 2026-10-27 (Asia/Seoul)

## 환경

- Windows, JDK 17
- Android Gradle Plugin 8.9.2 / Gradle 8.11.1
- Android SDK 35 / build-tools 35.0.0
- Android 15 (API 35) Google APIs x86_64, Pixel 2 가상 기기
- Android Emulator 37.2.12 / AEHD 2.2 (사용자 승인 후 설치)
- 지원 최소 버전: Android 8.0 (API 26)
- 테스트 APK: `app/build/outputs/apk/debug/app-debug.apk`

## 자동 테스트

12개 통과, 실패 및 오류 0개.

- `assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL
- lint: 오류 0개, 경고 0개
- `apksigner verify`: 최종 APK 서명 검증 통과

| 검증 | 개수 | 결과 |
|---|---:|---|
| 1회·요일 반복·주말 건너뛰기·시간대·DST | 5 | 통과 |
| 2초 랜덤 슬롯·정각 절단·intro 1회·chain 반복·지연 시작·버퍼 연속성 | 5 | 통과 |
| 실제 패키징 음원 길이 및 start→chain 3회 반복의 샘플 동일성 | 2 | 통과 |

PCM 테스트는 출력 샘플이 원본 배열을 공백 없이 이어 붙인 결과와 동일한지 확인합니다. Android 기기의 실제 오디오 출력이나 원본 소리의 청감까지 검증한 것은 아닙니다.

## Android 에뮬레이터 통합 테스트

수정된 APK에서 4개 통합 시나리오 통과. 원본 출력: `verification/instrumentation.txt`.

- 사전 5초의 실제 예약 실행 → 본 알람 알림으로 전환 → start 이후 chain 반복 시간 동안 서비스 유지 → 알람 화면에서 종료
- 사전 0초: 시각 전에는 서비스 없음 → 본 알람 시각에 서비스 시작 → 메인 화면 버튼으로 종료
- 예약을 끈 뒤 사전/본 시각이 지나도 실행하지 않음
- 반복 알람 실행 시 다음 예약 생성 → 현재 울림을 꺼도 다음 예약 유지

추가로 재부팅 후 BOOT_COMPLETED에서 PRE/MAIN 예약 복원을 확인했습니다. 원본: `verification/after-reboot-alarms.txt`.

일반 백그라운드 예약에서는 Android 15의 오디오 포커스 거절로 서비스가 종료되는 결함을 발견했습니다. 오디오 포커스 협조와 USAGE_ALARM 스트림의 수명을 분리하고, 포커스를 받지 못한 경우 통화 모드에서 음소거하도록 수정했습니다. 테스트 실행기 안에서의 결과만으로 백그라운드 재생 성공을 판단하지 않습니다.

## 테스트 실행기 밖의 백그라운드 검증

- 일반 UI 저장 버튼으로 02:06 알람/사전 10초를 예약한 후 테스트 실행기를 종료하고 화면을 껐습니다.
- 02:05:50.050에 USAGE_ALARM AudioTrack(pi id 103, session 113)이 시작됐고, 본 알람 이후 02:06:14에도 같은 트랙이 `state:started`, `mutedState:none`으로 유지됐습니다. `background-audio.txt`, `continuous-track.txt`에 기록했습니다.
- AudioFlinger 출력에서 해당 트랙의 underrun 0, 내부 음소거 false를 확인했습니다. `audio-flinger.txt`에 기록했습니다. 물리적 출력 지연이 0이라는 뜻은 아닙니다.
- 사전 알림을 갱신하는 방식으로는 Android 15가 본 알람의 전체 화면 intent를 실행하지 않는 문제를 발견해, 본 알람을 새 알림 항목으로 전환하도록 수정했습니다. 오디오 트랙은 재시작하지 않습니다.
- 수정 후 20초 뒤의 실제 AlarmManager 예약을 남기고 테스트 실행기를 종료했습니다. 화면 꺼짐 상태에서 사전 소리 재생 후 본 알람 화면이 자동으로 열렸습니다. `fullscreen-activity.txt`의 RingingActivity, `fullscreen-power.txt`의 Awake 및 `alarm-screen.png`로 확인했습니다.
- 실제 알람 화면의 끄기 버튼을 터치한 뒤 서비스가 없어졌고, 반복 예약은 켜진 상태로 다음 시각을 유지했습니다. `stopped-service.txt`, `stopped-repeat.xml`에 기록했습니다.
- 발견한 기능 결함 외에, 앱을 열 때 숫자 키보드가 자동으로 화면을 가리던 문제도 수정했습니다.
- 최종 APK에서 4개 통합 시나리오를 다시 통과했고 테스트 예약은 전부 껐습니다. 에뮬레이터 창은 사용자가 볼 수 있도록 열어 두었습니다.
- Windows 창 표시 호환성을 위해 마지막 실행은 CPU 가속(AEHD)을 유지하고 GPU 렌더러만 SwiftShader로 변경했습니다. 정상 표시된 기본 화면은 `verification/main-final.png`입니다.

## 실기기에서 추가 확인할 항목

실제 Android 휴대폰은 연결되지 않았습니다. 다음은 실기기 검증이 남아 있습니다.

- 설치 및 화면 조작, 권한 거부·허용 후 예약
- 제조사 절전 정책 및 깊은 Doze에서 사전/본 알람 실행
- 화면과 알림에서 종료, 반복 예약 유지
- 실제 정각 출력 지연, start→chain 전환 및 반복의 청감
- 다른 앱 오디오·통화·블루투스와의 상호작용

초기 검증은 기본 UI로 수행했습니다. 아래 추가 작업에서는 사용자 요청에 따라 알람 화면에 제공 이미지를 적용했습니다.

## 토끼 이미지 시계 화면 추가 검증

- 사용자 제공 1692×930 PNG를 원본 그대로 `drawable-nodpi/usagi_scene.png`에 복사했습니다. 원본과 리소스의 SHA-256이 동일합니다.
- 이미지의 흰 시계 바탕을 다시 칠하지 않고 현재 시각 `HH:mm` 숫자만 그립니다. 시간 갱신은 화면이 보이는 동안 수행하고, 이미지와 숫자는 같은 비율로 배치합니다.
- 사전 알림부터 전체 화면 intent를 사용하도록 변경했습니다. 기기를 사용 중인 경우 상단 알림으로 표시될 수 있다는 Android 정책은 유지합니다.
- `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 성공. 단위 테스트 12개 통과, lint 오류·경고 0개, 최종 APK 서명 검증 통과.
- 통합 시나리오 5개 통과: 기존 4개에 사전 종료 후 본 알람 재실행 방지/반복 유지 검증을 추가했습니다. 현재 시각이 초 없는 HH:mm인지도 화면에서 확인합니다. 결과: `verification/clock-instrumentation.txt`.
- 새 사전 종료 테스트는 최초 1초 뒤 PRE를 요청해 Android의 최소 예약 여유(5초)에 걸렸습니다. 예약 방식을 바꾸지 않고 테스트를 6초 뒤 PRE로 수정한 후 통과했습니다.
- 테스트 실행기 밖에서 화면을 끈 뒤 사전 시점에 RingingActivity가 자동으로 열렸고, 알림 제목이 '우사기 사전 알림'인 상태의 이미지를 저장했습니다. `clock-pre-activity.txt`, `clock-pre-notification.txt`, `clock-pre.png`.
- 시계의 흰 영역 안에 현재 시각이 표시되는 것을 시각 검증했고, 본 알람으로 전환된 뒤에도 RingingActivity가 유지되는 것을 확인했습니다.
- 검증 종료 시 현재 서비스와 테스트용 반복 예약을 모두 껐습니다. 에뮬레이터는 열어 두었습니다.

## 가로 전체 화면 및 본 알람 이미지 전환

- 사전 화면은 `usagi_scene.png`, 본 알람 시각부터는 추가된 `usagi_alarm.png`를 사용합니다. 두 이미지의 원본 픽셀은 수정하지 않았습니다.
- 알람 Activity만 가로로 고정하고 시스템 표시줄을 숨겼습니다. 그림을 중앙 채우기로 창 전체에 그려 여백을 없앴으며, 종료 버튼은 그림 위에 겹칩니다. 화면비에 따라 가장자리 일부는 잘릴 수 있습니다.
- 두 이미지 각각 흰 시계의 네 모서리에 시간 숫자를 원근 변환해 기울기와 크기를 맞춥니다. 현재 시각 HH:mm은 계속 갱신합니다.
- 이미 알람 화면이 보이는 경우 본 알람 전환은 기존 알림을 조용히 갱신해 시스템 알림이 그림을 가리지 않게 했습니다. 알람 화면이 닫혀 있으면 본 시각에 새 알림으로 다시 표시합니다.
- 빌드 및 단위 테스트 12개 통과. 통합 시나리오 6개 통과(`verification/landscape-instrumentation.txt`): 기존 5개와 사전→본 이미지 전환/가로 방향/그림이 전체 창을 채우는지 검증하는 artwork 시나리오.
- 실제 화면 꺼짐 예약에서 사전 화면 자동 열기와 본 알람 이미지 전환을 확인했습니다. 1920×1080 캡처: `verification/landscape-pre.png`, `verification/landscape-main.png`.
- lint 오류 0개, 경고 2개: 요청된 가로 고정의 기기 호환성 권고 및 사용하지 않는 추가 JPG(`usagi_background.jpg`). 경고를 숨기거나 사용자 이미지를 제거하지 않았습니다.
- 중간에 숫자로 시작하는 새 JPG 리소스 이름 때문에 빌드가 실패해 이름만 규칙에 맞춰 정리했습니다. 최종 리소스 폴더에 있는 `usagi_background.jpg`는 화면에 적용하지 않았으며 최종 APK에 반영해 빌드했습니다.
- 최종 APK 서명 검증 통과. 검증용 현재 울림/반복 예약은 모두 해제했고 서비스가 남지 않은 것을 확인했습니다.

## 세로 방향 중심 크롭 추가

- 알람 화면의 가로 고정을 제거해 기기 방향을 따르게 했습니다. 가로의 기존 중앙 채우기는 유지하고, 세로는 사전/본 이미지마다 토끼와 시계에 맞춘 별도 중심점으로 좌우를 자릅니다.
- 좁은 세로 화면에서는 시계의 흰 영역과 숫자가 화면 밖으로 나가지 않도록 크롭 위치를 제한합니다. 이미지와 숫자의 원근 변환은 같은 좌표 이동을 따릅니다. 캐릭터/시계 테두리 가장자리는 화면비에 따라 일부 잘릴 수 있습니다.
- 빌드 및 단위 테스트 12개 통과. 통합 시나리오 7개 통과: 기존 6개에 재생 중 세로→가로→세로 회전, 전체 창 채우기, 재생 시각 유지 및 회전 후 종료를 검증하는 rotation 시나리오를 추가했습니다. 결과: `verification/portrait-instrumentation.txt`.
- 화면 꺼짐 상태의 실제 예약으로 세로 사전/본 화면을 각각 캡처하고 크롭 중심·시간 표시·여백 없음을 시각 검증했습니다. `verification/portrait-pre.png`, `verification/portrait-main.png`.
- lint 오류 0개, 미사용 사용자 이미지 `usagi_background.jpg` 경고 1개. 가로 고정 권고 경고는 없어졌습니다.

## 세로 지원 롤백 — 현재 버전

- 사용자 요청으로 세로 중심 크롭과 방향 전환 지원만 되돌렸습니다. 사전/본 알람 Activity는 다시 가로 고정 및 중앙 채우기를 사용합니다.
- 세로 크롭 분기와 rotation 통합 테스트를 제거하고, artwork 테스트의 가로 방향 검증을 복원했습니다. 이미지 전환, 시계 원근/현재 시각 표시, 기존 재생/종료 기능은 유지합니다.
- 빌드, 단위 테스트 12개, 에뮬레이터 통합 시나리오 6개 및 APK 서명 검증 통과. 결과: `verification/landscape-rollback-instrumentation.txt`.
- lint 오류 0개, 경고 3개: 요청된 가로 고정 권고 및 미사용 사용자 이미지 `usagi_background.jpg`, `usagi_background_vertical.jpg`. 사용자 이미지 파일은 보존했습니다.
- 위 세로 화면 검증 항목은 이전 버전의 기록이며 현재 APK는 알람 화면을 가로로만 표시합니다.

## 알람 화면의 상단 알림 카드 제거

- 화면을 여는 높은 중요도 알람 채널과 재생 상태를 표시하는 낮은 중요도 채널(`alarm_playback_quiet`)을 분리했습니다.
- RingingActivity가 표시되면 서비스의 포그라운드 알림을 낮은 중요도 항목(id 13)으로 전환하고 기존 상단 카드 항목을 취소합니다. 사전→본 전환에서도 화면이 열려 있으면 낮은 중요도를 유지합니다.
- 화면이 닫혀 있을 때 다시 열기 위한 높은 중요도 알림 경로는 유지합니다. 상태 알림과 종료 동작은 알림 목록에 남습니다.
- 늦게 도착한 화면 표시 명령이 종료된 알람을 다시 시작하지 않도록 방어하고 실제 서비스 명령으로 검증했습니다. Android가 서비스 명령을 재전달하는 경우를 위해 원래 target/lead 값은 보존합니다.
- 빌드, 단위 테스트 12개, 통합 시나리오 6개 및 APK 서명 검증 통과. 실제 게시 알림의 채널 중요도 LOW, fullScreenIntent 없음, 높은 중요도 항목 제거를 사전/본 단계 모두 확인합니다. 결과: `verification/quiet-notification-instrumentation.txt`.
- 테스트 실행기 밖의 화면 꺼짐 예약에서도 자동 화면 열기와 카드 없는 화면을 확인했습니다. `verification/quiet-pre.png`, `verification/quiet-main.png`; 두 알림 덤프 모두 id 13 / importance 2 / alarm_playback_quiet를 확인했습니다.
- 시스템 설정·권한 및 다른 앱 알림은 변경하지 않았습니다. lint는 기존 가로 고정 및 미사용 사용자 이미지 경고 3개, 오류 0개입니다.

## 설정 화면 디자인 및 자동 저장

- 세로는 usagi_background_vertical.jpg, 가로는 usagi_background.jpg를 중앙 채우기로 표시합니다. 원본 이미지는 변경하지 않았습니다.
- 큰 TimePicker와 저장·권한·테스트·현재 울림 종료 버튼을 제거했습니다. 오전/오후, 시·분 입력, 알림 켜기, 반복 요일, 사전 알림 초만 남기고 크림색 패널과 갈색 글자·강조색으로 구성했습니다.
- 변경은 600ms 뒤 자동 저장합니다. 꺼진 알림은 설정만 보존하고, 켜진 알림은 새 값으로 재예약합니다. 유효하지 않은 입력은 기존 예약을 덮어쓰지 않습니다. 켜기 스위치에서 필요한 권한을 순차 안내합니다.
- 회전으로 입력 상태를 복원할 때 자동 저장을 실행하지 않아 울리던 알람이 중단되지 않도록 했습니다.
- 빌드, 단위 테스트 12개, 실제 Android 에뮬레이터 통합 시나리오 7개 및 APK 서명 검증 통과. settings 시나리오는 꺼진 상태 저장, 자정·정오 변환, 켜기, 켜진 상태 예약 갱신, 잘못된 분 입력 보존, 예약 해제를 검증합니다. 기존 재생 시나리오 6개도 통과했습니다.
- lint 오류 0개, 기존 알람 화면의 가로 고정 권고 경고 1개. 두 배경의 미사용 경고는 없어졌습니다.
- Pixel 2 API 35의 가로·세로에서 모든 설정이 한 화면에 표시되는 것을 캡처로 확인했습니다. 작은 화면·큰 시스템 글꼴·키보드 사용 시에는 스크롤할 수 있습니다. 실기기와 권한 거부·복구 경로는 별도 확인이 필요합니다.
- 결과: verification/settings-instrumentation.txt, verification/settings-portrait.png, verification/settings-landscape.png. 검증용 예약과 울림은 해제했습니다.

## 알람 모션 및 터치 후 종료 버튼

- 사전 알림의 그림과 시계 숫자를 함께 좌우 각 4dp로 부드럽게 이동하고 1초마다 방향을 바꿉니다. 본 알람은 재생 중인 PCM의 10ms RMS 에너지에 따라 좌우 최대 14dp·상하 최대 10dp로 흔들립니다.
- 음원에 써 넣은 버퍼 위치가 아니라 AudioTrack의 재생 헤드 위치를 사용합니다. 32비트 재생 헤드의 순환도 누적 처리합니다. 재생 연결 방식과 파일은 변경하지 않았습니다.
- 모션에 필요한 가장자리만 소폭 확대해 빈틈을 방지합니다. 시간 숫자는 그림과 같은 Canvas 변환을 따르고, 종료 버튼은 변환 밖의 고정 위치를 사용합니다.
- 종료 버튼은 처음에는 숨깁니다. 그림을 터치하면 하단 중앙에 둥근 테두리·그림자·누름 효과가 있는 버튼이 나타납니다. 표시 후 사전→본 전환과 Activity 상태 복원에서도 유지합니다.
- 빌드, 단위 테스트 16개(모션 4개 추가), Android API 35 에뮬레이터 통합 7개 및 APK 서명 검증 통과. lint 오류 0개, 기존 가로 고정 권고 경고 1개.
- artwork 통합 검증은 실제 터치 이벤트 후 클릭 콜백을 기다려 숨겨진 버튼 표시·중앙/하단 배치·전환 후 유지 및 본 음원의 흔들림 강도 전달을 확인합니다. 결과: verification/motion-instrumentation.txt, verification/motion-artwork.txt.
- 출력 장치의 물리적 지연(특히 블루투스)과 실제 기기에서 느끼는 흔들림 강도는 청감·실기기 확인이 필요합니다.
- 테스트 실행기 밖의 화면 꺼짐 예약에서도 사전 알람이 RingingActivity를 여는 로그를 확인했습니다. 캡처 시점에는 이미 화면이 닫혀 이 추가 확인에서 모션을 시각 검증했다고 주장하지 않습니다. 통합 artwork 검증의 버튼 동작과 녹화에서 사전 알림의 둥근 버튼 표시는 확인했습니다. 테스트 예약은 enabled=false, target=0으로 해제하고 실행 중 서비스가 없는 것을 확인했습니다.
