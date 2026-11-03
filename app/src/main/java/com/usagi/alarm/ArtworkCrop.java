package com.usagi.alarm;

/**
 * 알람 화면 그림을 창에 가득 채울 때(중앙 채우기) 어느 부분을 남기고 자를지 정하는 계산. Android 클래스에 의존하지 않는다.
 *
 * 창 비율이 그림과 다르면 한 축은 넘쳐서 잘린다. 기본은 가운데 맞춤이고, 그대로 두면 캐릭터나 시계가 잘릴 때만
 * 최소한으로 옮긴다. 그래서 일반 폰 가로(그림과 비슷하거나 더 긴 비율)의 모습은 그대로이고,
 * 폴더블 안쪽처럼 정사각형에 가깝거나 세로가 긴 창에서는 캐릭터와 시계 쪽으로 자르는 위치가 이동한다.
 * 결과는 그림 좌표 → 창 좌표 변환(x * scale + left, y * scale + top)이며 UsagiClockView가 그림과 시계 숫자에 같이 적용한다.
 */
final class ArtworkCrop {
    // 화면에 남겨야 하는 그림 영역 {left, top, right, bottom}(그림 픽셀).
    // KEEP: 캐릭터 얼굴·몸과 시계 전체(가능하면 모두 보이게). ESSENTIAL: 시계 숫자판(아주 좁은 창에서도 반드시 보이게).
    static final float[] SCENE_KEEP = {790, 380, 1420, 800};
    static final float[] SCENE_ESSENTIAL = {1000, 600, 1390, 790};
    static final float[] ALARM_KEEP = {955, 250, 1520, 760};
    static final float[] ALARM_ESSENTIAL = {950, 450, 1200, 620};

    final float scale, left, top;

    private ArtworkCrop(float scale, float left, float top) { this.scale = scale; this.left = left; this.top = top; }

    /**
     * @param preferred 가능하면 모두 보이게 할 영역
     * @param essential preferred가 창에 다 들어가지 않을 때 대신 반드시 보이게 할 영역
     */
    static ArtworkCrop of(float viewWidth, float viewHeight, float artWidth, float artHeight, float[] preferred, float[] essential) {
        float scale = Math.max(viewWidth / artWidth, viewHeight / artHeight);
        float left = offset(viewWidth, artWidth * scale, preferred[0] * scale, preferred[2] * scale, essential[0] * scale, essential[2] * scale);
        float top = offset(viewHeight, artHeight * scale, preferred[1] * scale, preferred[3] * scale, essential[1] * scale, essential[3] * scale);
        return new ArtworkCrop(scale, left, top);
    }

    /** 한 축의 그림 시작 위치. 모든 값은 이미 scale이 적용된 창 단위이다. */
    private static float offset(float view, float drawn, float keepStart, float keepEnd, float mustStart, float mustEnd) {
        float centered = (view - drawn) / 2;
        float lowest = view - drawn; // 그림 끝이 창 끝에 닿는 위치(빈 가장자리가 생기지 않는 한계)
        // 부동소수 오차로 거의 같은 크기일 때는 이동할 여지가 없다.
        if (drawn - view < 0.5f) return Math.min(0, centered);
        // 영역이 창 안에 들어오려면 시작 위치는 [-영역 시작, 창 길이 - 영역 끝] 범위여야 한다.
        if (keepEnd - keepStart <= view) return clamp(centered, Math.max(lowest, -keepStart), Math.min(0, view - keepEnd));
        float middle = view / 2 - (mustStart + mustEnd) / 2;
        return clamp(clamp(middle, lowest, 0), -mustStart, view - mustEnd);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
