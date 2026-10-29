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

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (!TOGGLE.equals(intent.getAction())) return;
        AlarmStore store = new AlarmStore(context);
        AlarmScheduler scheduler = new AlarmScheduler(context);
        if (store.enabled() || store.snoozeTarget() > 0) scheduler.disable();
        else if (scheduler.permitted()) scheduler.save(store.hour(), store.minute(), store.days(), store.leadSeconds());
        updateAll(context);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, views(context));
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, AlarmWidget.class));
        if (ids.length > 0) manager.updateAppWidget(ids, views(context));
    }

    static RemoteViews views(Context context) {
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
        views.setTextViewText(R.id.widget_status, status);
        views.setTextViewText(R.id.widget_toggle, enabled ? "끄기" : "켜기");
        PendingIntent open = PendingIntent.getActivity(context, 20, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle = PendingIntent.getBroadcast(context, 21,
                new Intent(context, AlarmWidget.class).setAction(TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_content, open);
        views.setOnClickPendingIntent(R.id.widget_toggle, enabled || scheduler.permitted() ? toggle : open);
        return views;
    }
}
