package com.usagi.alarm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Predicate;

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

    /** 기기 지역과 무관하게 한국 공휴일 기준이다. 음력 달력은 처음 쓸 때 만든다. */
    private static KoreanHolidays koreanHolidays;

    private static synchronized KoreanHolidays koreanHolidays() {
        if (koreanHolidays == null) koreanHolidays = new KoreanHolidays(new AndroidLunarCalendar());
        return koreanHolidays;
    }

    /** 캘린더 공휴일까지 쓰는 상태인지: 두 스위치가 모두 켜져 있고 캘린더 읽기 권한이 있을 때만 true. */
    boolean calendarActive() {
        return store.skipHolidays() && store.calendarHolidays() && AndroidHolidayCalendar.permitted(context);
    }

    /**
     * 저장된 '공휴일엔 울리지 않기' 설정에 따라 건너뛸 날짜 조건을 돌려준다(꺼져 있으면 null).
     * 캘린더는 after 날짜부터 한 번만 읽는다.
     */
    private Predicate<LocalDate> skipDate(long after) {
        if (!store.skipHolidays()) return null;
        LocalDate from = Instant.ofEpochMilli(after).atZone(ZoneId.systemDefault()).toLocalDate();
        return new HolidayPolicy(koreanHolidays(), calendarActive() ? new AndroidHolidayCalendar(context) : null, from)::isHoliday;
    }

    private long nextTarget(int hour, int minute, int days, long after) {
        return AlarmTimes.next(hour, minute, days, after, ZoneId.systemDefault(), skipDate(after));
    }

    /**
     * 울리기 직전 재확인: target 날짜가 지금 기준으로 공휴일이면 울리지 않고 다음 날짜로 옮겨 다시 예약하고 true.
     * 조회가 실패하거나 지연되면 공휴일이 아닌 것으로 보아 울린다(false). 스누즈에는 쓰지 않는다.
     */
    boolean skipIfHoliday(long target) {
        if (!store.skipHolidays()) return false;
        try {
            LocalDate date = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!skipDate(target).test(date)) return false;
            long next = nextTarget(store.hour(), store.minute(), store.days(), Math.max(target, System.currentTimeMillis()));
            store.prefs.edit().putLong("target", next).commit();
            schedule();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 캘린더 가져오기 설정을 저장하고, 알람이 켜져 있으면 새 설정으로 다시 계산해 예약한다. */
    void setCalendarHolidays(boolean use) {
        store.prefs.edit().putBoolean("calendarHolidays", use).commit();
        if (store.enabled()) restore(true);
        else AlarmWidget.updateAll(context);
    }

    /**
     * 공휴일 건너뛰기 설정을 저장한다. 알람이 켜져 있으면 다음 울림 시각을 새 설정으로 다시 계산해 예약하고
     * 위젯도 갱신한다. 꺼져 있으면 설정만 저장한다. 다시 울리는 스누즈는 영향 없다.
     */
    void setSkipHolidays(boolean skip) {
        store.prefs.edit().putBoolean("skipHolidays", skip).commit();
        if (store.enabled()) restore(true);
        else AlarmWidget.updateAll(context);
    }

    void save(int hour, int minute, int days, int lead) {
        disable();
        long target = nextTarget(hour, minute, days, System.currentTimeMillis());
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
            long next = nextTarget(store.hour(), store.minute(), store.days(), System.currentTimeMillis());
            store.prefs.edit().putLong("target", next).commit();
        }
        schedule();
    }

    void schedule() {
        cancel();
        AlarmWidget.updateAll(context);
        if (!store.enabled()) return;
        if (!permitted()) {
            store.error(AlarmStore.ERROR_PERMISSION);
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
            store.error(AlarmStore.ERROR_EXACT_ALARM);
        }
        AlarmWidget.updateAll(context);
    }

    void advance() {
        if (store.days() == 0) {
            store.prefs.edit().putBoolean("enabled", false).putLong("target", 0).commit();
            cancel();
            AlarmWidget.updateAll(context);
        } else {
            long next = nextTarget(store.hour(), store.minute(), store.days(),
                    Math.max(store.target(), System.currentTimeMillis()));
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
        if (!permitted()) { store.error(AlarmStore.ERROR_SNOOZE_PERMISSION); return false; }
        PendingIntent show = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(retry, show), pending(SNOOZE, 3, retry));
            store.error("");
            return true;
        } catch (SecurityException e) {
            store.error(AlarmStore.ERROR_SNOOZE_EXACT_ALARM);
            return false;
        }
    }
}
