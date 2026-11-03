package com.usagi.alarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.os.SystemClock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Artwork fills the display; each clock's perspective is expressed in artwork pixels. */
final class UsagiClockView extends View {
    static final int BACKGROUND = Color.rgb(249, 244, 222);
    static final int INK = Color.rgb(77, 46, 36);
    private final Bitmap scene;
    private final Bitmap alarmScene;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix clockTransform = new Matrix();
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private static final float[] PANEL = {0, 0, 300, 0, 300, 110, 0, 110};
    // Insets inside the white panels: top-left, top-right, bottom-right, bottom-left.
    private static final float[] PRE_PANEL = {1047, 640, 1344, 644, 1342, 749, 1047, 746};
    private static final float[] ALARM_PANEL = {1010, 477, 1153, 519, 1140, 567, 1000, 528};
    private final DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private String time = "";
    private boolean alarmRinging;

    UsagiClockView(Context context) {
        super(context);
        scene = BitmapFactory.decodeResource(getResources(), R.drawable.usagi_scene);
        alarmScene = BitmapFactory.decodeResource(getResources(), R.drawable.usagi_alarm);
        paint.setColor(INK);
        paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setBackgroundColor(BACKGROUND);
        clockTransform.setPolyToPoly(PANEL, 0, PRE_PANEL, 0, 4);
        updateTime(System.currentTimeMillis());
    }

    void setAlarmRinging(boolean ringing) {
        if (alarmRinging == ringing) return;
        alarmRinging = ringing;
        clockTransform.setPolyToPoly(PANEL, 0, ringing ? ALARM_PANEL : PRE_PANEL, 0, 4);
        updateDescription();
        invalidate();
    }

    private void updateDescription() {
        setContentDescription(getContext().getString(R.string.clock_description,
                getContext().getString(alarmRinging ? R.string.clock_main : R.string.clock_pre), time));
    }

    /** 현재 창 크기에서 그림을 채우는 위치. 창 비율이 바뀌면(폴드·회전) 매번 다시 계산하므로 별도 상태가 없다. */
    private ArtworkCrop cropFor(Bitmap artwork) {
        return ArtworkCrop.of(getWidth(), getHeight(), artwork.getWidth(), artwork.getHeight(),
                alarmRinging ? ArtworkCrop.ALARM_KEEP : ArtworkCrop.SCENE_KEEP,
                alarmRinging ? ArtworkCrop.ALARM_ESSENTIAL : ArtworkCrop.SCENE_ESSENTIAL);
    }

    /**
     * 시계 숫자가 그려지는 흰 영역의 창 안 위치(흔들림 없음). 숫자가 화면 밖으로 나가거나 버튼에 가려지는지
     * 계측 시험에서 확인하는 용도이며 onDraw와 같은 크롭 결과를 쓴다.
     */
    RectF clockPanelBounds() {
        float[] panel = alarmRinging ? ALARM_PANEL : PRE_PANEL;
        ArtworkCrop crop = cropFor(alarmRinging ? alarmScene : scene);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i += 2) {
            float x = panel[i] * crop.scale + crop.left, y = panel[i + 1] * crop.scale + crop.top;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        return new RectF(minX, minY, maxX, maxY);
    }

    void updateTime(long now) {
        String value = format.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(now));
        if (!time.equals(value)) {
            time = value;
            updateDescription();
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap artwork = alarmRinging ? alarmScene : scene;
        ArtworkCrop crop = cropFor(artwork);
        float scale = crop.scale, left = crop.left, top = crop.top;
        canvas.save();
        float density = getResources().getDisplayMetrics().density;
        double t = SystemClock.uptimeMillis() / 1000.0;
        float x = alarmRinging ? (float) (Math.sin(t * 73) * 0.7 + Math.sin(t * 117) * 0.3)
                * AlarmService.soundLevel * 14 * density
                : AlarmMotion.preOffset(System.currentTimeMillis() - AlarmService.activeTarget) * 4 * density;
        float y = alarmRinging ? (float) (Math.sin(t * 91) * 0.7 + Math.sin(t * 139) * 0.3)
                * AlarmService.soundLevel * 10 * density : 0;
        // Overscan the artwork and its clock together so motion never reveals blank edges.
        float margin = (alarmRinging ? 15 : 5) * density;
        float zoom = Math.max((getWidth() + 2 * margin) / getWidth(), (getHeight() + 2 * margin) / getHeight());
        canvas.translate(getWidth() / 2f + x, getHeight() / 2f + y);
        canvas.scale(zoom, zoom);
        canvas.translate(-getWidth() / 2f, -getHeight() / 2f);
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        canvas.drawBitmap(artwork, 0, 0, paint);
        // Map the digits into the panel's four corners, retaining the original white pixels.
        canvas.concat(clockTransform);
        paint.setTextSize(84f);
        paint.getFontMetrics(metrics);
        canvas.drawText(time, 150, 55 - (metrics.ascent + metrics.descent) / 2, paint);
        canvas.restore();
        if (AlarmService.active && isShown()) postInvalidateOnAnimation();
    }
}
