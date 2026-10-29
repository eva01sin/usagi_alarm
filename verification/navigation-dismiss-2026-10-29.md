# 알람 뒤로가기·홈 종료 검증

- 검증일: 2026-10-29 (Asia/Seoul)
- 환경: Usagi_API_35, Android 15 (API 35), emulator-5554
- 변경된 앱 및 테스트 APK를 기존 디버그 서명으로 빌드하고 업데이트 설치함.
- 별도 빌드 출력 경로: C:/repository/alarm-validation-build/app
- assembleDebug, assembleDebugAndroidTest, testDebugUnitTest, lintDebug: BUILD SUCCESSFUL
- 기존 Gradle 및 빌드 산출물 접근 권한 문제를 별도 프로젝트 캐시와 출력 디렉터리로 우회함. 프로젝트 빌드 설정은 변경하지 않음.

## 에뮬레이터 통합 테스트

실행: `adb -s emulator-5554 shell am instrument -w -e scenario <scenario> com.usagi.alarm.test/com.usagi.alarm.AlarmInstrumentation`

```text
RUN backDismiss
PASS backDismiss
PASS: 1 Android integration scenarios

RUN homeDismiss
PASS homeDismiss
PASS: 1 Android integration scenarios
```

두 시나리오 모두 실제 뒤로가기/홈 키 이벤트를 사용해 사전 알림과 본 알람 재생이 종료되는지 확인함. 사전 알림 종료 후 원래 정각까지 기다려 재실행되지 않는지 확인하고 다음 반복 예약 유지도 검증함. 뒤로가기는 알람 화면 종료도 검증함.

테스트 종료 시 예약 및 재생은 테스트 실행기의 정리 처리로 해제됨.

이번 실행 범위는 추가한 두 통합 시나리오이며 전체 통합 시나리오는 재실행하지 않음. Android 13 미만과 제스처 내비게이션의 실제 스와이프 입력은 별도 검증하지 않음.

## v1.1 배포 검증 (2026-10-30)

- versionName 1.1, versionCode 2 및 화면 하단 버전 표기를 반영해 재빌드함.
- assembleDebug, assembleDebugAndroidTest, testDebugUnitTest(16개), lintDebug 통과. lint에는 하위 API에서 무시되는 enableOnBackInvokedCallback 속성 경고 1건이 있음.
- APK 서명 검증 통과 및 기존 앱에 업데이트 설치 성공.
- 새 APK에서도 backDismiss, homeDismiss 각각 PASS.
- 배포 파일: usagi_alarm_v1.1.apk
- SHA-256: 5e20790aa7e1ca70083d8adaeda76cdc22e42becc8fde306336a30e50b4f7220
