package com.usagi.alarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Matrix;
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
        setContentDescription((alarmRinging ? "본 알람" : "사전 알림") + ", 현재 시각 " + time);
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
        float scale = Math.max(getWidth() / (float) artwork.getWidth(), getHeight() / (float) artwork.getHeight());
        float left = (getWidth() - artwork.getWidth() * scale) / 2;
        float top = (getHeight() - artwork.getHeight() * scale) / 2;
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
