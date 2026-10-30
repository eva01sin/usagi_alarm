package com.usagi.alarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.text.DateFormat;
import java.util.Date;

public final class AlarmWidget extends AppWidgetProvider {
    static final String TOGGLE = "com.usagi.alarm.WIDGET_TOGGLE";
    private static final String CLOCK_TICK = "com.usagi.alarm.WIDGET_CLOCK_TICK";

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (CLOCK_TICK.equals(intent.getAction())) { updateAll(context); return; }
        if (!TOGGLE.equals(intent.getAction())) return;
        AlarmStore store = new AlarmStore(context);
        AlarmScheduler scheduler = new AlarmScheduler(context);
        if (store.enabled() || store.snoozeTarget() > 0) scheduler.disable();
        else if (scheduler.permitted()) scheduler.save(store.hour(), store.minute(), store.days(), store.leadSeconds());
        updateAll(context);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, views(context));
        scheduleClock(context, ids.length > 0);
    }

    @Override public void onDisabled(Context context) { scheduleClock(context, false); }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, AlarmWidget.class));
        if (ids.length > 0) manager.updateAppWidget(ids, views(context));
        scheduleClock(context, ids.length > 0);
    }

    static RemoteViews views(Context context) {
        return views(context, System.currentTimeMillis());
    }

    private static void scheduleClock(Context context, boolean present) {
        android.app.AlarmManager manager = context.getSystemService(android.app.AlarmManager.class);
        PendingIntent tick = PendingIntent.getBroadcast(context, 22,
                new Intent(context, AlarmWidget.class).setAction(CLOCK_TICK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!present) { manager.cancel(tick); return; }
        long nextMinute = (System.currentTimeMillis() / 60_000 + 1) * 60_000;
        // RTC does not wake a sleeping device merely to redraw the clock.
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                manager.setExact(android.app.AlarmManager.RTC, nextMinute, tick);
                return;
            }
        } catch (SecurityException ignored) {
            // Permission may be revoked between checking and scheduling.
        }
        manager.set(android.app.AlarmManager.RTC, nextMinute, tick);
    }

    static RemoteViews views(Context context, long now) {
        AlarmStore store = new AlarmStore(context);
        AlarmScheduler scheduler = new AlarmScheduler(context);
        boolean enabled = store.enabled() || store.snoozeTarget() > 0;
        long next = store.enabled() ? store.target() : 0;
        boolean snoozed = store.snoozeTarget() > 0 && (next == 0 || store.snoozeTarget() < next);
        if (snoozed) next = store.snoozeTarget();
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.alarm_widget);
        String status = enabled ? (snoozed ? "다시 알림 · " : "다음 알람 · ")
                + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(next)) : "알람이 꺼져 있어요";
        if (!scheduler.permitted()) status = "눌러서 알람 권한을 확인해 주세요";
        else if (!store.error().isEmpty()) status = store.error();
        java.time.LocalTime clockTime = java.time.Instant.ofEpochMilli(now)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        String time = clockTime.format(java.time.format.DateTimeFormatter.ofPattern("a hh:mm", java.util.Locale.US));
        views.setImageViewBitmap(R.id.widget_status, clockDigits(context, time, enabled, true));
        views.setImageViewBitmap(R.id.widget_status_blank, clockDigits(context, time, enabled, false));
        views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background);
        views.setInt(R.id.widget_clock, "setBackgroundResource", enabled ? R.drawable.widget_dial : R.drawable.widget_dial_off);
        views.setContentDescription(R.id.widget_status, time + ", 현재 시각, " + status);
        views.setContentDescription(R.id.widget_clock, time + ", 현재 시각, " + status);
        views.removeAllViews(R.id.widget_toggle);
        views.addView(R.id.widget_toggle, new RemoteViews(context.getPackageName(),
                enabled ? R.layout.widget_face_on : R.layout.widget_face_off));
        views.setContentDescription(R.id.widget_toggle, enabled ? "우사기 · 알람 끄기" : "모몽가 · 알람 켜기");
        PendingIntent open = PendingIntent.getActivity(context, 20, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle = PendingIntent.getBroadcast(context, 21,
                new Intent(context, AlarmWidget.class).setAction(TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_clock, open);
        views.setOnClickPendingIntent(R.id.widget_toggle, enabled || scheduler.permitted() ? toggle : open);
        return views;
    }

    private static android.graphics.Bitmap clockDigits(Context context, String time, boolean enabled, boolean colonVisible) {
        android.graphics.Paint digits = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        digits.setTypeface(context.getResources().getFont(R.font.dseg7_regular));
        digits.setTextSize(80);
        digits.setColor(enabled ? 0xff51382b : 0xff625d55);
        android.graphics.Paint period = new android.graphics.Paint(digits);
        period.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        period.setTextSize(28);
        String label = time.substring(0, 2), numbers = time.substring(3);
        float labelWidth = period.measureText(label), gap = 12;
        android.graphics.Rect digitBounds = new android.graphics.Rect(), labelBounds = new android.graphics.Rect();
        digits.getTextBounds(numbers, 0, numbers.length(), digitBounds);
        period.getTextBounds(label, 0, label.length(), labelBounds);
        int top = Math.min(digitBounds.top, labelBounds.top), bottom = Math.max(digitBounds.bottom, labelBounds.bottom);
        int width = (int) Math.ceil(labelWidth + gap + digits.measureText(numbers)) + 8;
        int height = bottom - top + 8;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        float baseline = 4 - top;
        float labelBaseline = label.equals("AM") ? 4 - labelBounds.top : height - 4 - labelBounds.bottom;
        canvas.drawText(label, 4, labelBaseline, period);
        canvas.drawText(colonVisible ? numbers : numbers.replace(':', ' '), 4 + labelWidth + gap, baseline, digits);
        return bitmap;
    }
}
