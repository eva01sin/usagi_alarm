package com.usagi.alarm;

/**
 * 현재 창의 가로·세로 크기(dp)로 화면 배치 방식을 정하는 규칙. Android 클래스에 의존하지 않아 단위 테스트할 수 있다.
 *
 * 방향(orientation)이나 기기 이름이 아니라 창 크기로만 판단한다. 폴더블의 펼친 안쪽 화면과 태블릿은
 * 방향과 무관하게 짧은 변이 길어 {@link Mode#LARGE}가 되고, 일반 폰은 세로·가로로 나뉜다.
 * 접힌 폴더블의 길고 좁은 창은 일반 폰과 같은 {@link Mode#PHONE_PORTRAIT}로 다룬다.
 */
final class WindowLayoutPolicy {
    enum Mode { PHONE_PORTRAIT, PHONE_LANDSCAPE, LARGE }

    /**
     * 큰 창으로 보는 짧은 변의 최소 길이(dp). 가장 큰 일반 폰의 가로 모드 짧은 변(약 450dp 이하)보다 크고,
     * 폴더블 안쪽 화면 근사값(짧은 변 약 586dp)과 태블릿(600dp 이상)을 포함하도록 540으로 둔다.
     */
    static final int LARGE_SHORT_SIDE_DP = 540;

    /** 큰 창이어도 높이가 이보다 낮으면(예: 폴더블 안쪽 가로 약 558dp) 2열로도 세로 공간이 모자라 여백·글자를 줄인다. */
    static final int LOW_HEIGHT_DP = 640;

    private WindowLayoutPolicy() {}

    /**
     * 알람 화면의 버튼 줄을 시계 숫자판을 피해 옮겨도 되는 창인지. 큰 창이거나, 21:9보다 더 길쭉한 접힌 창일 때만 true이다.
     * 일반 폰(세로·가로 16:9~21:9)은 false라서 버튼이 항상 아래 가운데에 있다(기존 모습 유지).
     */
    static boolean mayMoveAlarmControls(int widthDp, int heightDp) {
        if (modeOf(widthDp, heightDp) == Mode.LARGE) return true;
        int longSide = Math.max(widthDp, heightDp), shortSide = Math.min(widthDp, heightDp);
        // 정수로 비교해 정확히 21:9인 창에서 부동소수 오차가 생기지 않게 한다.
        return longSide * 9 > shortSide * 21;
    }

    /** 큰 창 중 높이가 낮아 압축 배치가 필요한지. 폰 크기 창은 항상 false(기존 모습 유지). */
    static boolean needsCompactLayout(int widthDp, int heightDp) {
        return modeOf(widthDp, heightDp) == Mode.LARGE && heightDp < LOW_HEIGHT_DP;
    }

    static Mode modeOf(int widthDp, int heightDp) {
        if (Math.min(widthDp, heightDp) >= LARGE_SHORT_SIDE_DP) return Mode.LARGE;
        return widthDp > heightDp ? Mode.PHONE_LANDSCAPE : Mode.PHONE_PORTRAIT;
    }
}
