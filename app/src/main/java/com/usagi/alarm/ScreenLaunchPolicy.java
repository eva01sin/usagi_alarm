package com.usagi.alarm;

/**
 * 알람이 시작될 때 서비스가 알람 화면(RingingActivity)을 직접 열지 정하는 규칙.
 * Android 클래스에 의존하지 않아 단위 테스트할 수 있다.
 *
 * 기기를 사용 중이면 전체 화면 intent는 상단 알림 카드로만 표시되므로, '다른 앱 위에 표시' 권한이 있을 때만
 * 서비스가 화면을 직접 연다. 권한이 없으면 기존 알림(전체 화면 intent 포함) 동작을 그대로 쓴다.
 */
final class ScreenLaunchPolicy {
    private ScreenLaunchPolicy() {}

    /** 권한이 허용돼 있고 알람 화면이 아직 보이지 않을 때만 직접 연다(이미 떠 있으면 중복으로 열지 않는다). */
    static boolean shouldOpenDirectly(boolean overlayAllowed, boolean screenVisible) {
        return overlayAllowed && !screenVisible;
    }
}
