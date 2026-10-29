package com.usagi.alarm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.ZoneId;

// Persist the occurrence before handing it to AlarmManager or leaving a receiver.
@SuppressLint("ApplySharedPref")
final class AlarmScheduler {
    static final String SNOOZE = "com.usagi.alarm.SNOOZE";
    static final String PRE = "com.usagi.alarm.PRE";
    static final String MAIN = "com.usagi.alarm.MAIN";
    private final Context context;
    private final AlarmManager manager;
    private final AlarmStore store;

    AlarmScheduler(Context context) {
        this.context = context;
        manager = context.getSystemService(AlarmManager.class);
        store = new AlarmStore(context);
    }

    boolean permitted() {
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) return false;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        return context.getSystemService(android.app.NotificationManager.class).areNotificationsEnabled();
    }

    private PendingIntent pending(String action, int requestCode, long target) {
        Intent intent = new Intent(context, AlarmReceiver.class).setAction(action)
                .putExtra("target", target).putExtra("generation", store.generation());
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    void cancel() {
        manager.cancel(pending(PRE, 1, 0));
        manager.cancel(pending(MAIN, 2, 0));
    }

    void disable() {
        cancel();
        cancelSnooze();
        store.prefs.edit().putBoolean("enabled", false).putLong("target", 0)
                .putLong("generation", store.generation() + 1).commit();
        context.stopService(new Intent(context, AlarmService.class));
        AlarmWidget.updateAll(context);
    }

    void save(int hour, int minute, int days, int lead) {
        disable();
        long target = AlarmTimes.next(hour, minute, days, System.currentTimeMillis(), ZoneId.systemDefault());
        store.prefs.edit().putInt("hour", hour).putInt("minute", minute).putInt("days", days)
                .putInt("lead", lead).putBoolean("enabled", true).putLong("target", target)
                .putString("error", "").commit();
        schedule();
    }

    void restore(boolean recalculate) {
        long retry = store.snoozeTarget();
        if (retry > System.currentTimeMillis()) scheduleSnooze(retry);
        else if (retry != 0) cancelSnooze();
        AlarmWidget.updateAll(context);
        if (!store.enabled()) return;
        if (recalculate || store.target() <= System.currentTimeMillis()) {
            long next = AlarmTimes.next(store.hour(), store.minute(), store.days(),
                    System.currentTimeMillis(), ZoneId.systemDefault());
            store.prefs.edit().putLong("target", next).commit();
        }
        schedule();
    }

    void schedule() {
        cancel();
        AlarmWidget.updateAll(context);
        if (!store.enabled()) return;
        if (!permitted()) {
            store.error("알림 및 정확한 알람 권한을 허용한 뒤 다시 켜 주세요.");
            AlarmWidget.updateAll(context);
            return;
        }
        long target = store.target();
        PendingIntent show = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(target, show), pending(MAIN, 2, target));
            if (store.leadSeconds() > 0) {
                long pre = Math.max(System.currentTimeMillis() + 100, target - store.leadSeconds() * 1000L);
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pre, pending(PRE, 1, target));
            }
            store.error("");
        } catch (SecurityException e) {
            cancel();
            store.error("정확한 알람 권한이 없어 예약할 수 없습니다.");
        }
        AlarmWidget.updateAll(context);
    }

    void advance() {
        if (store.days() == 0) {
            store.prefs.edit().putBoolean("enabled", false).putLong("target", 0).commit();
            cancel();
            AlarmWidget.updateAll(context);
        } else {
            long next = AlarmTimes.next(store.hour(), store.minute(), store.days(),
                    Math.max(store.target(), System.currentTimeMillis()), ZoneId.systemDefault());
            store.prefs.edit().putLong("target", next).commit();
            schedule();
        }
    }

    void cancelSnooze() {
        manager.cancel(pending(SNOOZE, 3, 0));
        store.prefs.edit().remove("snooze").commit();
        AlarmWidget.updateAll(context);
    }

    boolean snooze(long occurrence) {
        long retry = System.currentTimeMillis() + 5 * 60_000L;
        if (!scheduleSnooze(retry)) return false;
        store.prefs.edit().putLong("snooze", retry).commit();
        if (store.enabled() && store.target() == occurrence) advance();
        AlarmWidget.updateAll(context);
        return true;
    }

    private boolean scheduleSnooze(long retry) {
        if (!permitted()) { store.error("다시 알림 권한을 확인해 주세요."); return false; }
        PendingIntent show = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(retry, show), pending(SNOOZE, 3, retry));
            store.error("");
            return true;
        } catch (SecurityException e) {
            store.error("정확한 알람 권한이 없어 다시 알림을 예약하지 못했습니다.");
            return false;
        }
    }
}
