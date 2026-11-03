package com.usagi.alarm;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Runs against real Android services and AlarmManager, with no test-only app entry points. */
public final class AlarmInstrumentation extends Instrumentation {
    private Context context;
    private AlarmStore store;
    private AlarmScheduler scheduler;
    private int passed;
    private String selected;
    private String originalLanguage;

    @Override public void onCreate(Bundle args) {
        selected = args == null ? "all" : args.getString("scenario", "all");
        start();
    }

    @Override public void onStart() {
        context = getTargetContext();
        store = new AlarmStore(context);
        scheduler = new AlarmScheduler(context);
        // 기존 시나리오의 한국어 문구 검사가 기기 언어와 무관하게 통과하도록 시작 시 한국어로 고정하고, 끝나면 되돌린다.
        originalLanguage = store.language();
        store.language(LanguagePolicy.KOREAN);
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_OK;
        try {
            check(scheduler.permitted(), "Required emulator test permissions are missing");
            if (selected.equals("prepare")) run("prepare", this::prepare);
            if (selected.equals("background")) run("background", () -> {
                open(MainActivity.class);
                schedule(20, 5, 127);
            });
            if (selected.equals("all") || selected.equals("settings")) run("settings", this::settings);
            if (selected.equals("all") || selected.equals("sequence")) run("sequence", this::sequence);
            if (selected.equals("all") || selected.equals("noLead")) run("noLead", this::noLead);
            if (selected.equals("all") || selected.equals("cancel")) run("cancel", this::cancel);
            if (selected.equals("all") || selected.equals("repeat")) run("repeat", this::repeat);
            if (selected.equals("all") || selected.equals("preDismiss")) run("preDismiss", this::preDismiss);
            if (selected.equals("all") || selected.equals("artwork")) run("artwork", this::artwork);
            if (selected.equals("all") || selected.equals("backDismiss")) run("backDismiss", this::backDismiss);
            if (selected.equals("all") || selected.equals("homeDismiss")) run("homeDismiss", this::homeDismiss);
            if (selected.equals("all") || selected.equals("snooze")) {
                run("snoozePre", () -> snooze(true));
                run("snoozeMain", () -> snooze(false));
            }
            if (selected.equals("all") || selected.equals("widget")) run("widget", this::widget);
            if (selected.equals("all") || selected.equals("widgetPeriod")) run("widgetPeriod", this::widgetPeriod);
            if (selected.equals("all") || selected.equals("language")) run("language", this::language);
            if (selected.equals("foldWindow")) run("foldWindow", this::foldWindow);
            if (selected.equals("foldSettings")) run("foldSettings", this::foldSettings);
            if (selected.equals("overlayLaunch")) run("overlayLaunch", this::overlayLaunch);
            if (selected.equals("widgetHost")) run("widgetHost", this::widgetHost);
            result.putString("stream", "\nPASS: " + passed + " Android integration scenarios\n");
        } catch (Throwable error) {
            result.putString("stream", "\nFAIL: " + error + "\n" + android.util.Log.getStackTraceString(error));
            resultCode = Activity.RESULT_CANCELED;
        } finally {
            if (!selected.equals("prepare") && !selected.equals("background")) runOnMainSync(scheduler::disable);
            store.language(originalLanguage);
        }
        finish(resultCode, result);
    }

    private interface Scenario { void run() throws Exception; }
    private void run(String name, Scenario scenario) throws Exception {
        Bundle progress = new Bundle();
        progress.putString("stream", "\nRUN " + name + "\n");
        sendStatus(1, progress);
        runOnMainSync(scheduler::disable);
        scenario.run();
        passed++;
        progress.putString("stream", "PASS " + name + "\n");
        sendStatus(0, progress);
    }

    private Activity open(Class<? extends Activity> activity) {
        return startActivitySync(new Intent(context, activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    private long schedule(int seconds, int lead, int days) {
        long target = System.currentTimeMillis() + seconds * 1000L;
        runOnMainSync(() -> {
            store.prefs.edit().putLong("target", target).putInt("lead", lead)
                    .putInt("days", days).putInt("hour", 8).putInt("minute", 0)
                    .putBoolean("enabled", true).putString("error", "").commit();
            scheduler.schedule();
        });
        check(store.error().isEmpty(), store.error());
        return target;
    }

    private boolean title(String text) {
        for (StatusBarNotification entry : context.getSystemService(NotificationManager.class).getActiveNotifications()) {
            CharSequence title = entry.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
            if (text.contentEquals(title == null ? "" : title)) return true;
        }
        return false;
    }

    private boolean quietPlayback() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        boolean found = false;
        for (StatusBarNotification entry : manager.getActiveNotifications()) {
            Notification notification = entry.getNotification();
            if (AlarmService.CHANNEL.equals(notification.getChannelId())) return false;
            if (AlarmService.PLAYBACK_CHANNEL.equals(notification.getChannelId())) {
                if (notification.fullScreenIntent != null) return false;
                if (manager.getNotificationChannel(notification.getChannelId()).getImportance()
                        != NotificationManager.IMPORTANCE_LOW) return false;
                found = true;
            }
        }
        return found;
    }

    private void sequence() {
        open(MainActivity.class);
        long target = schedule(9, 5, 0);
        await(() -> AlarmService.active, 8000, "PRE receiver did not start playback");
        check(System.currentTimeMillis() < target, "Pre-alarm started after target");
        await(() -> title("우사기 사전 알림"), 2000, "Pre-alarm notification missing");
        await(() -> title("우사기 알람"), 10000, "Main alarm did not replace pre-alarm");
        await(() -> !store.enabled(), 3000, "One-shot was not consumed");
        SystemClock.sleep(5500); // Intro is 2.5 s; allow several chain loops.
        check(AlarmService.active, "Alarm stopped before the user pressed stop: " + store.error());
        Activity screen = open(RingingActivity.class);
        runOnMainSync(() -> {
            UsagiClockView clock = findClock(screen.getWindow().getDecorView());
            check(clock != null, "Artwork clock missing");
            String expected = "현재 시각 " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));
            check(clock.getContentDescription().toString().endsWith(expected), "Clock must show current HH:mm without seconds");
            check(clock.getContentDescription().toString().startsWith("본 알람"), "Main artwork was not selected");
        });
        click(screen, "알람 끄기");
        await(() -> !AlarmService.active, 3000, "Ringing screen stop failed");
        check(store.error().isEmpty(), store.error());
    }

    private void noLead() {
        open(MainActivity.class);
        schedule(4, 0, 0);
        SystemClock.sleep(1000);
        check(!AlarmService.active, "Zero-lead alarm played before target");
        await(() -> AlarmService.active, 7000, "Zero-lead main receiver did not start playback");
        await(() -> title("우사기 알람"), 2000, "Zero-lead main notification missing");
        Activity screen = open(RingingActivity.class);
        click(screen, "알람 끄기");
        await(() -> !AlarmService.active, 3000, "Main screen stop failed");
    }

    private void cancel() {
        open(MainActivity.class);
        schedule(5, 2, 0);
        runOnMainSync(scheduler::disable);
        SystemClock.sleep(6000);
        check(!AlarmService.active, "Cancelled alarm still fired");
        check(!store.enabled() && store.target() == 0, "Disabled reservation remains");
    }

    private void repeat() {
        open(MainActivity.class);
        long target = schedule(5, 3, 127);
        await(() -> AlarmService.active, 6000, "Repeating pre-alarm did not start");
        await(() -> store.target() > target, 8000, "Next repeating occurrence was not scheduled");
        check(store.enabled(), "Repeating alarm became disabled");
        long next = store.target();
        Activity screen = open(RingingActivity.class);
        click(screen, "알람 끄기");
        await(() -> !AlarmService.active, 3000, "Repeat stop failed");
        check(store.enabled() && store.target() == next, "Stopping playback cancelled repeat reservation");
    }

    private void settings() {
        Activity screen = open(MainActivity.class);
        runOnMainSync(() -> {
            ((EditText) screen.findViewById(R.id.time_hour)).setText("12");
            ((EditText) screen.findViewById(R.id.time_minute)).setText("34");
            ((EditText) screen.findViewById(R.id.lead_seconds)).setText("20");
            ((RadioGroup) screen.findViewById(R.id.time_period)).check(R.id.period_am);
        });
        SystemClock.sleep(900);
        check(!store.enabled() && store.hour() == 0 && store.minute() == 34 && store.leadSeconds() == 20,
                "Disabled draft or midnight conversion failed");
        runOnMainSync(() -> ((RadioGroup) screen.findViewById(R.id.time_period)).check(R.id.period_pm));
        SystemClock.sleep(900);
        check(store.hour() == 12, "Noon conversion failed");
        runOnMainSync(() -> ((Switch) screen.findViewById(R.id.alarm_enabled)).setChecked(true));
        check(store.enabled() && store.target() > System.currentTimeMillis(), "Enable did not schedule");
        long first = store.target();
        runOnMainSync(() -> ((EditText) screen.findViewById(R.id.time_minute)).setText("35"));
        SystemClock.sleep(900);
        check(store.enabled() && store.minute() == 35 && store.target() != first, "Enabled edit did not reschedule");
        runOnMainSync(() -> ((EditText) screen.findViewById(R.id.time_minute)).setText("99"));
        SystemClock.sleep(900);
        check(store.minute() == 35, "Invalid minute replaced valid reservation");
        runOnMainSync(() -> ((EditText) screen.findViewById(R.id.time_minute)).setText("35"));
        runOnMainSync(() -> ((Switch) screen.findViewById(R.id.alarm_enabled)).setChecked(false));
        check(!store.enabled() && store.target() == 0, "Disable did not cancel");
    }
    private void prepare() {
        Activity screen = open(MainActivity.class);
        ZonedDateTime alarm = ZonedDateTime.now().plusMinutes(2).withSecond(0).withNano(0);
        runOnMainSync(() -> configure(screen.getWindow().getDecorView(), alarm.getHour(), alarm.getMinute()));
        runOnMainSync(() -> ((Switch) screen.findViewById(R.id.alarm_enabled)).setChecked(true));
        check(store.enabled() && store.target() > System.currentTimeMillis(), "UI did not save reservation");
        Bundle info = new Bundle();
        info.putString("stream", "Prepared real UI reservation: target=" + store.target() + " lead=" + store.leadSeconds() + "\n");
        sendStatus(0, info);
    }

    private void preDismiss() {
        open(MainActivity.class);
        // Allow Android's minimum alarm futurity (5 s on the emulator) before PRE.
        long target = schedule(12, 6, 127);
        await(() -> AlarmService.active, 9000, "Pre-alarm did not start");
        Activity screen = open(RingingActivity.class);
        check(System.currentTimeMillis() < target, "Dismiss test missed pre-alarm window");
        click(screen, "알람 끄기");
        await(() -> !AlarmService.active, 3000, "Pre-alarm stop failed");
        check(store.enabled() && store.target() > target, "Pre-alarm dismissal must advance repeat");
        long end = target + 1500;
        while (System.currentTimeMillis() < end) SystemClock.sleep(50);
        check(!AlarmService.active, "Dismissed occurrence rang again at MAIN");
    }

    private void homeDismiss() throws Exception {
        open(MainActivity.class);
        long target = schedule(12, 6, 127);
        await(() -> AlarmService.active, 9000, "Pre-alarm did not start");
        open(RingingActivity.class);
        check(System.currentTimeMillis() < target, "Home test missed pre-alarm window");
        getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME").close();
        await(() -> !AlarmService.active, 3000, "Home did not stop pre-alarm");
        check(store.enabled() && store.target() > target, "Home must preserve next repeat");
        long end = target + 1500;
        while (System.currentTimeMillis() < end) SystemClock.sleep(50);
        check(!AlarmService.active, "Home-dismissed occurrence rang again at MAIN");

        open(MainActivity.class);
        runOnMainSync(() -> context.startForegroundService(new Intent(context, AlarmService.class)
                .putExtra("target", System.currentTimeMillis()).putExtra("lead", 0)));
        await(() -> AlarmService.active, 3000, "Main playback did not start");
        long next = store.target();
        open(RingingActivity.class);
        getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME").close();
        await(() -> !AlarmService.active, 3000, "Home did not stop main alarm");
        check(store.enabled() && store.target() == next, "Main home changed repeat reservation");
    }

    private void backDismiss() {
        open(MainActivity.class);
        long target = schedule(12, 6, 127);
        await(() -> AlarmService.active, 9000, "Pre-alarm did not start");
        Activity screen = open(RingingActivity.class);
        check(System.currentTimeMillis() < target, "Back test missed pre-alarm window");
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        await(() -> !AlarmService.active, 3000, "Back did not stop pre-alarm");
        check(screen.isFinishing() || screen.isDestroyed(), "Back did not close alarm screen");
        check(store.enabled() && store.target() > target, "Back must preserve next repeat");
        long end = target + 1500;
        while (System.currentTimeMillis() < end) SystemClock.sleep(50);
        check(!AlarmService.active, "Back-dismissed occurrence rang again at MAIN");

        open(MainActivity.class);
        runOnMainSync(() -> context.startForegroundService(new Intent(context, AlarmService.class)
                .putExtra("target", System.currentTimeMillis()).putExtra("lead", 0)));
        await(() -> AlarmService.active, 3000, "Main playback did not start");
        long next = store.target();
        open(RingingActivity.class);
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        await(() -> !AlarmService.active, 3000, "Back did not stop main alarm");
        check(store.enabled() && store.target() == next, "Main back changed repeat reservation");
    }

    private UsagiClockView findClock(View view) {
        if (view instanceof UsagiClockView) return (UsagiClockView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                UsagiClockView clock = findClock(group.getChildAt(i));
                if (clock != null) return clock;
            }
        }
        return null;
    }

    private void artwork() {
        open(MainActivity.class);
        long target = System.currentTimeMillis() + 10000;
        runOnMainSync(() -> context.startForegroundService(new Intent(context, AlarmService.class)
                .putExtra("target", target).putExtra("lead", 10)));
        await(() -> AlarmService.active, 3000, "Artwork preview did not start");
        Activity screen = open(RingingActivity.class);
        waitForIdleSync();
        UsagiClockView clock = findClock(screen.getWindow().getDecorView());
        check(clock != null, "Clock artwork missing");
        await(() -> clock.getContentDescription().toString().startsWith("사전 알림"), 2000, "Pre artwork missing");
        await(this::quietPlayback, 3000, "Visible pre-alarm must use only a low-importance playback notification");
        runOnMainSync(() -> {
            Button stop = find(screen.getWindow().getDecorView(), "알람 끄기");
            check(stop != null && !stop.isShown(), "Stop must initially be hidden");
            long now = SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
                    android.view.MotionEvent.ACTION_DOWN, clock.getWidth() / 2f, clock.getHeight() / 2f, 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 40,
                    android.view.MotionEvent.ACTION_UP, clock.getWidth() / 2f, clock.getHeight() / 2f, 0);
            clock.dispatchTouchEvent(down);
            clock.dispatchTouchEvent(up);
            down.recycle(); up.recycle();
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            Button stop = find(screen.getWindow().getDecorView(), "알람 끄기");
            check(stop.isShown(), "Touch did not reveal stop button");
            View decor = screen.getWindow().getDecorView();
            View controls = (View) stop.getParent();
            check(Math.abs(controls.getLeft() + controls.getWidth() / 2 - decor.getWidth() / 2) <= 1,
                    "Alarm controls must be horizontally centered");
            check(controls.getTop() > decor.getHeight() / 2, "Alarm controls must be at the bottom");
            check(stop.getBackground() instanceof android.graphics.drawable.RippleDrawable,
                    "Stop button decoration missing");
        });
        runOnMainSync(() -> {
            check(screen.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE,
                    "Alarm must be landscape");
            View decor = screen.getWindow().getDecorView();
            check(clock.getWidth() == decor.getWidth() && clock.getHeight() == decor.getHeight(),
                    "Artwork must occupy the entire window");
        });
        await(() -> clock.getContentDescription().toString().startsWith("본 알람"), 12000,
                "Artwork did not switch at target time");
        await(this::quietPlayback, 3000, "Main transition must not restore a heads-up notification");
        await(() -> AlarmService.soundLevel > 0.01f, 3000, "Main sound must drive shake intensity");
        runOnMainSync(() -> check(find(screen.getWindow().getDecorView(), "알람 끄기").isShown(),
                "Revealed stop button must remain visible through main transition"));
        click(screen, "알람 끄기");
        await(() -> !AlarmService.active, 3000, "Artwork screen stop failed");
        open(MainActivity.class);
        runOnMainSync(() -> context.startService(new Intent(context, AlarmService.class)
                .setAction(AlarmService.SCREEN_VISIBLE).putExtra("target", target).putExtra("lead", 10)));
        SystemClock.sleep(1000);
        check(!AlarmService.active, "A stale screen-visible command restarted a dismissed alarm");
    }

    private void snooze(boolean pre) {
        open(MainActivity.class);
        long occurrence = schedule(pre ? 12 : 1, pre ? 10 : 0, pre ? 127 : 0);
        await(() -> AlarmService.active, 6000, "Alarm did not start");
        Activity screen = open(RingingActivity.class);
        click(screen, "5분만...");
        await(() -> !AlarmService.active && store.snoozeTarget() > 0, 3000, "Snooze did not stop playback");
        await(this::homeVisible, 6000, "Snooze must return to the home screen");
        long retry = store.snoozeTarget();
        check(Math.abs(retry - System.currentTimeMillis() - 300_000L) < 3000, "Snooze must be five minutes");
        check(pre ? store.enabled() && store.target() > occurrence : !store.enabled(), "Repeat state changed");
        check(context.getSystemService(android.app.AlarmManager.class).getNextAlarmClock().getTriggerTime() == retry,
                "Snooze was not registered with AlarmManager");
        runOnMainSync(() -> scheduler.restore(false));
        check(store.snoozeTarget() == retry, "Restore lost snooze");
        long nextRepeat = store.target();
        // Keep the five-minute assertion above; accelerate only the delivery phase.
        runOnMainSync(() -> {
            store.prefs.edit().putLong("snooze", System.currentTimeMillis() + 2000).commit();
            scheduler.restore(false);
        });
        await(() -> AlarmService.active && store.snoozeTarget() == 0, 6000, "Snooze did not ring");
        check(AlarmService.activeLeadSeconds == 0, "Snooze must skip pre-alarm audio");
        check(store.target() == nextRepeat, "Snooze delivery changed repeat");
        screen = open(RingingActivity.class);
        click(screen, "5분만...");
        await(() -> !AlarmService.active && store.snoozeTarget() > 0, 3000, "Second snooze failed");
        await(this::homeVisible, 6000, "Second snooze must return to the home screen");
        runOnMainSync(scheduler::disable);
        check(store.snoozeTarget() == 0, "Disable must cancel snooze");
    }

    private boolean homeVisible() {
        String resolved = shellOutput("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").trim();
        String home = resolved.substring(resolved.lastIndexOf('\n') + 1).trim();
        for (String line : shellOutput("dumpsys activity activities").split("\n")) {
            if (line.contains("topResumedActivity=") && line.contains(" " + home + " ")) return true;
        }
        return false;
    }

    private String shellOutput(String command) {
        try (android.os.ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(command);
             java.io.FileInputStream input = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new AssertionError("Could not inspect the active home activity", error);
        }
    }

    private void widget() {
        Activity screen = open(MainActivity.class);
        final View[] widget = new View[1];
        runOnMainSync(() -> {
            store.prefs.edit().putInt("hour", 20).putInt("minute", 5).commit();
            widget[0] = AlarmWidget.views(context).apply(context, new android.widget.FrameLayout(context));
            String current = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("a hh:mm", Locale.US));
            check(widget[0].findViewById(R.id.widget_status).getContentDescription().toString().startsWith(current + ", 현재 시각"),
                    "Widget must show current time instead of the configured alarm time");
            check(widget[0].findViewById(R.id.widget_status).getContentDescription().toString().contains("꺼져"),
                    "Widget disabled status missing");
            check(widget[0].findViewById(R.id.widget_toggle).getContentDescription().toString().startsWith("모몽가"),
                    "Disabled widget must show Momonga");
            widget[0].findViewById(R.id.widget_toggle).performClick();
        });
        await(store::enabled, 3000, "Widget did not enable alarm");
        runOnMainSync(() -> {
            widget[0] = AlarmWidget.views(context).apply(context, new android.widget.FrameLayout(context));
            check(widget[0].findViewById(R.id.widget_status).getContentDescription().toString().contains("다음 알람"),
                    "Widget next alarm missing");
            check(widget[0].findViewById(R.id.widget_toggle).getContentDescription().toString().startsWith("우사기"),
                    "Enabled widget must show Usagi");
            check(scheduler.snooze(0), "Widget snooze setup failed");
            widget[0] = AlarmWidget.views(context).apply(context, new android.widget.FrameLayout(context));
            check(widget[0].findViewById(R.id.widget_status).getContentDescription().toString().contains("다시 알림"),
                    "Widget snooze status missing");
            widget[0].findViewById(R.id.widget_toggle).performClick();
        });
        await(() -> !store.enabled() && store.snoozeTarget() == 0, 3000, "Widget did not cancel all reservations");
        runOnMainSync(() -> screen.finish());
    }

    /** 언어 선택: English로 바꾸면 설정·알람 화면·알림·위젯이 영어로, 한국어로 되돌리면 한국어로 나오고 선택이 유지된다. */
    private void language() {
        Activity screen = open(MainActivity.class);
        check(hasText(screen.getWindow().getDecorView(), "알람 시간"), "Korean settings screen expected before the switch");
        runOnMainSync(() -> ((RadioButton) screen.findViewById(R.id.language_english)).setChecked(true));
        check(LanguagePolicy.ENGLISH.equals(store.language()), "English choice was not stored");
        await(screen::isDestroyed, 3000, "Settings screen was not redrawn after the language change");

        Activity english = open(MainActivity.class);
        waitForIdleSync();
        View englishRoot = english.getWindow().getDecorView();
        for (String text : new String[] {"Alarm time", "Turn on alarm", "Repeat on", "Language", "Follow system settings", "Mon"}) {
            check(hasText(englishRoot, text), "English settings text missing: " + text);
        }
        check(((RadioButton) english.findViewById(R.id.language_english)).isChecked(), "English radio must be checked");

        long target = System.currentTimeMillis() + 30000; // 느린 기기에서도 사전 알림 단계가 유지되도록 넉넉히 둔다.
        runOnMainSync(() -> context.startForegroundService(new Intent(context, AlarmService.class)
                .putExtra("target", target).putExtra("lead", 30)));
        await(() -> AlarmService.active, 3000, "Language preview did not start");
        await(() -> title("Usagi pre-alarm"), 3000, "English notification title missing");
        Activity ringing = open(RingingActivity.class);
        waitForIdleSync();
        UsagiClockView clock = findClock(ringing.getWindow().getDecorView());
        check(clock != null && clock.getContentDescription().toString().startsWith("Pre-alarm"),
                "English alarm screen description missing: " + (clock == null ? "no clock" : clock.getContentDescription()));
        check(find(ringing.getWindow().getDecorView(), "5 more min...") != null, "English snooze button missing");
        click(ringing, "Turn off alarm");
        await(() -> !AlarmService.active, 3000, "English stop button failed");

        runOnMainSync(() -> {
            View widget = AlarmWidget.views(context).apply(context, new android.widget.FrameLayout(context));
            check(widget.findViewById(R.id.widget_status).getContentDescription().toString().contains("current time, Alarm is off"),
                    "English widget status missing");
            check(widget.findViewById(R.id.widget_toggle).getContentDescription().toString().startsWith("Momonga"),
                    "English widget toggle missing");
        });

        // 선택이 저장소에 남아 있어 새 화면(재시작과 같은 경로)에서도 English로 열린다.
        Activity restarted = open(MainActivity.class);
        waitForIdleSync();
        check(LanguagePolicy.ENGLISH.equals(new AlarmStore(context).language()), "Language choice was not persisted");
        check(((RadioButton) restarted.findViewById(R.id.language_english)).isChecked()
                && hasText(restarted.getWindow().getDecorView(), "Alarm time"), "Language choice was lost on reopen");

        runOnMainSync(() -> ((RadioButton) restarted.findViewById(R.id.language_korean)).setChecked(true));
        await(restarted::isDestroyed, 3000, "Settings screen was not redrawn after returning to Korean");
        Activity korean = open(MainActivity.class);
        waitForIdleSync();
        for (String text : new String[] {"알람 시간", "알림 켜기", "반복 요일", "언어", "시스템 설정 따라가기", "월"}) {
            check(hasText(korean.getWindow().getDecorView(), text), "Korean settings text missing: " + text
                    + " (language=" + store.language() + ", locale=" + korean.getResources().getConfiguration().getLocales() + ")");
        }
        runOnMainSync(() -> {
            View widget = AlarmWidget.views(context).apply(context, new android.widget.FrameLayout(context));
            check(widget.findViewById(R.id.widget_status).getContentDescription().toString().contains("꺼져"),
                    "Korean widget status missing after switching back");
        });
        runOnMainSync(korean::finish);
    }

    /**
     * 사용 중 알람 화면 직접 열기(ALARM-002). 기기를 켜고 홈 화면이 앞에 있는 상태에서
     * (a) '다른 앱 위에 표시'를 허용하면 서비스가 알람 화면을 포그라운드로 직접 열고,
     * (b) 거부하면 화면은 열리지 않고 기존처럼 알림만 남는지 확인한다. 끝나면 appops를 원래 값으로 돌린다.
     */
    private void overlayLaunch() {
        String original = overlayMode();
        try {
            shellOutput("appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
            await(() -> android.provider.Settings.canDrawOverlays(context), 3000, "appops allow was not applied");
            checkOverlayPanel(false);
            showHomeScreen();
            long target = schedule(12, 6, 0);
            await(() -> AlarmService.active, 9000, "Allowed: pre-alarm did not start");
            await(() -> RingingActivity.visible, 4000, "Allowed: alarm screen was not opened by the service");
            check(System.currentTimeMillis() < target, "Allowed: screen opened only after the main alarm");
            await(this::ringingInFront, 3000, "Allowed: alarm screen is not the foreground activity");
            await(() -> System.currentTimeMillis() >= target + 500, 9000, "Allowed: main alarm time not reached");
            check(RingingActivity.visible && ringingInFront(), "Allowed: pre to main transition must keep the single alarm screen");
            stopAlarmService();
            await(() -> !AlarmService.active, 3000, "Allowed: stop failed");
            await(() -> !RingingActivity.visible, 3000, "Allowed: alarm screen did not close after stop");

            shellOutput("appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW deny");
            await(() -> !android.provider.Settings.canDrawOverlays(context), 3000, "appops deny was not applied");
            checkOverlayPanel(true);
            showHomeScreen();
            long deniedTarget = schedule(12, 6, 0);
            await(() -> AlarmService.active, 9000, "Denied: pre-alarm did not start");
            await(() -> title("우사기 사전 알림"), 3000, "Denied: pre-alarm notification missing");
            await(() -> System.currentTimeMillis() >= deniedTarget + 1500, 12000, "Denied: main alarm time not reached");
            await(() -> title("우사기 알람"), 3000, "Denied: main alarm notification missing");
            check(!RingingActivity.visible && !ringingInFront() && homeVisible(),
                    "Denied: alarm screen must not open; only the notification is expected");
            stopAlarmService();
            await(() -> !AlarmService.active, 3000, "Denied: stop failed");
        } finally {
            shellOutput("appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW " + original);
        }
    }

    /** foldWindow가 흉내 내는 창: wm size(px)와 wm density(dpi), 그리고 창 크기로 예상되는 레이아웃 모드. */
    private static final class FoldWindow {
        final String name, size, density;
        final WindowLayoutPolicy.Mode mode;
        FoldWindow(String name, String size, String density, WindowLayoutPolicy.Mode mode) {
            this.name = name; this.size = size; this.density = density; this.mode = mode;
        }
    }

    // 실제 삼성 기기 해상도는 확인되지 않았다. 아래 값은 폴드 안쪽·접힘 상태를 흉내 낸 근사 시험값이다.
    private static final FoldWindow[] FOLD_WINDOWS = {
        new FoldWindow("phone-portrait", "1080x1920", "420", WindowLayoutPolicy.Mode.PHONE_PORTRAIT),
        new FoldWindow("phone-landscape", "1920x1080", "420", WindowLayoutPolicy.Mode.PHONE_LANDSCAPE),
        new FoldWindow("inner-wide", "2176x1812", "374", WindowLayoutPolicy.Mode.LARGE),
        new FoldWindow("inner-wide-dense", "2176x1812", "520", WindowLayoutPolicy.Mode.LARGE),
        new FoldWindow("inner-tall", "1812x2176", "374", WindowLayoutPolicy.Mode.LARGE),
        new FoldWindow("folded", "904x2316", "420", WindowLayoutPolicy.Mode.PHONE_PORTRAIT),
    };

    /**
     * 폴더블·큰 창 대응(FOLD-003). `wm size`/`wm density`로 여러 창 크기를 흉내 내어
     * (1) 설정 화면의 핵심 뷰가 창 안에 들어오는지, (2) 알람 화면(사전→본)의 시계 숫자판·버튼이 창 안에 있고 서로 겹치지 않는지,
     * (3) 알람이 울리는 중 창 크기를 바꿔도 서비스 재생과 알람 화면이 유지되는지 확인한다.
     * 끝나면(실패해도) wm size/density를 원래대로 되돌린다. 스크린샷은 앱의 외부 파일 폴더에 FOLD-003-*.png로 남긴다.
     * 전역 화면 설정을 바꾸므로 `all`에는 넣지 않고 따로 실행한다.
     */
    private void foldWindow() throws Exception {
        resetWindow(); // 이전 실행이 중간에 죽어 남은 wm size/density Override가 있으면 먼저 되돌린다.
        shellOutput("input keyevent KEYCODE_WAKEUP");
        shellOutput("wm dismiss-keyguard");
        try {
            for (FoldWindow window : FOLD_WINDOWS) {
                setWindow(window);
                checkSettingsWindow(window);
                checkRingingWindow(window);
            }
            checkResizeWhileRinging();
        } finally {
            resetWindow();
        }
    }

    /** foldWindow 중 설정 화면 검사만 따로 실행한다(알람 소리·재생 없음). 화면 배치를 고칠 때 빠르게 확인하는 용도. */
    private void foldSettings() throws Exception {
        shellOutput("input keyevent KEYCODE_WAKEUP");
        shellOutput("wm dismiss-keyguard");
        try {
            for (FoldWindow window : FOLD_WINDOWS) {
                setWindow(window);
                checkSettingsWindow(window);
            }
        } finally {
            resetWindow();
        }
    }

    private void setWindow(FoldWindow window) {
        shellOutput("wm size " + window.size);
        shellOutput("wm density " + window.density);
        SystemClock.sleep(1500);
    }

    private void resetWindow() {
        shellOutput("wm size reset");
        shellOutput("wm density reset");
        SystemClock.sleep(1500);
    }

    private void checkSettingsWindow(FoldWindow window) throws Exception {
        Activity screen = open(MainActivity.class);
        SystemClock.sleep(400); // 계속 움직이는 알람 화면에서는 waitForIdleSync가 끝나지 않을 수 있어 짧게 기다린다.
        SystemClock.sleep(500);
        String where = window.name + " settings: ";
        onMain(() -> {
            Configuration configuration = screen.getResources().getConfiguration();
            check(WindowLayoutPolicy.modeOf(configuration.screenWidthDp, configuration.screenHeightDp) == window.mode,
                    where + "window " + configuration.screenWidthDp + "x" + configuration.screenHeightDp + "dp is not " + window.mode);
            View decor = screen.getWindow().getDecorView();
            boolean large = window.mode == WindowLayoutPolicy.Mode.LARGE;
            int[] ids = {R.id.time_period, R.id.time_hour, R.id.time_minute, R.id.alarm_enabled, R.id.weekday_monday, R.id.weekday_sunday,
                    R.id.lead_seconds, R.id.language_group, R.id.language_system, R.id.language_korean, R.id.language_english};
            java.util.List<android.graphics.Rect> boxes = new java.util.ArrayList<>();
            for (int id : ids) {
                View view = screen.findViewById(id);
                String name = screen.getResources().getResourceEntryName(id);
                check(view != null && view.isShown(), where + "view missing or hidden: " + name);
                // 폰은 기존처럼 아래쪽이 스크롤 영역일 수 있어 가로만 보고, 큰 창은 스크롤 없이 모두 보여야 한다.
                boxes.add(checkInsideWindow(view, decor, where + name, large));
                if (large) checkFullyVisible(view, where + name);
            }
            if (screen.findViewById(R.id.overlay_panel).getVisibility() == View.VISIBLE) {
                boxes.add(checkInsideWindow(screen.findViewById(R.id.overlay_open_settings), decor, where + "overlay button", large));
                if (large) checkFullyVisible(screen.findViewById(R.id.overlay_open_settings), where + "overlay button");
            }
            if (large) {
                // 큰 창은 콘텐츠 폭이 제한돼 가운데에 모이고, 하단 버전 표기와 겹치지 않아야 한다.
                int maxWidth = Math.round(880 * screen.getResources().getDisplayMetrics().density);
                android.graphics.Rect bounds = new android.graphics.Rect(boxes.get(0));
                for (android.graphics.Rect box : boxes) bounds.union(box);
                check(bounds.width() <= maxWidth, where + "content wider than the limit: " + bounds.width());
                View footer = findTextStartingWith(decor, "ver.");
                check(footer != null, where + "version footer missing");
                checkFullyVisible(footer, where + "footer");
                android.graphics.Rect footerBox = checkInsideWindow(footer, decor, where + "footer", false);
                check(footerBox.top >= 0 && footerBox.bottom <= decor.getHeight(), where + "footer is outside the window: " + footerBox);
                for (android.graphics.Rect box : boxes) {
                    check(!android.graphics.Rect.intersects(box, footerBox), where + "content overlaps the version footer");
                }
            }
        });
        captureWidget("FOLD-003-" + window.name + "-settings.png");
        onMain(screen::finish);
    }

    private void checkRingingWindow(FoldWindow window) throws Exception {
        String where = window.name + " ringing: ";
        long target = System.currentTimeMillis() + 12000;
        onMain(() -> context.startForegroundService(new Intent(context, AlarmService.class)
                .putExtra("target", target).putExtra("lead", 12)));
        await(() -> AlarmService.active, 3000, where + "preview did not start");
        Activity screen = open(RingingActivity.class);
        awaitLiveClock(screen, where);
        // 큰 세로 창은 처음 가로로 뜬 뒤 방향이 풀리며 뷰가 다시 만들어지므로 조건이 맞을 때까지 기다린다.
        awaitCheck(() -> {
            Configuration configuration = screen.getResources().getConfiguration();
            boolean large = WindowLayoutPolicy.modeOf(configuration.screenWidthDp, configuration.screenHeightDp) == WindowLayoutPolicy.Mode.LARGE;
            check(large == (window.mode == WindowLayoutPolicy.Mode.LARGE), where + "mode mismatch");
            // 폰 크기 창은 기존처럼 가로 고정이다. 큰 창은 방향을 잠그지 않는다.
            if (!large) check(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE, where + "phone alarm must stay landscape");
            checkClockFillsWindow(screen, where + "pre");
            checkClockPanel(liveClock(screen), where + "pre clock panel");
        }, 5000);
        captureWidget("FOLD-003-" + window.name + "-alarm-pre.png");
        revealControls(screen, where + "pre");
        awaitCheck(() -> checkControls(screen, where + "pre"), 4000);
        await(() -> {
            UsagiClockView clock = liveClock(screen);
            return clock != null && clock.getContentDescription().toString().startsWith("본 알람");
        }, 14000, where + "main artwork did not appear");
        awaitCheck(() -> {
            checkClockFillsWindow(screen, where + "main");
            checkClockPanel(liveClock(screen), where + "main clock panel");
            checkControls(screen, where + "main");
        }, 4000);
        captureWidget("FOLD-003-" + window.name + "-alarm-main.png");
        click(screen, "알람 끄기");
        await(() -> !AlarmService.active, 3000, where + "stop failed");
        await(() -> screen.isFinishing() || screen.isDestroyed(), 3000, where + "alarm screen did not close");
    }

    /**
     * 화면에 붙어 있는 현재 시계 뷰. RingingActivity는 창 크기·밀도가 바뀌면 뷰를 새로 만들므로(buildScreen),
     * 이전에 잡아 둔 뷰는 분리돼 크기가 0이 된다. 그래서 검사할 때마다 새로 조회하고 분리된 뷰는 돌려주지 않는다.
     */
    private UsagiClockView liveClock(Activity screen) {
        UsagiClockView clock = findClock(screen.getWindow().getDecorView());
        return clock != null && clock.isAttachedToWindow() ? clock : null;
    }

    /** 현재 시계 뷰가 창 전체 크기로 배치돼 있는지(메인 스레드에서 호출). */
    private void checkClockFillsWindow(Activity screen, String what) {
        View decor = screen.getWindow().getDecorView();
        UsagiClockView clock = liveClock(screen);
        check(clock != null, what + ": no attached clock view");
        check(clock.getWidth() > 0 && clock.getWidth() == decor.getWidth() && clock.getHeight() == decor.getHeight(),
                what + ": artwork must fill the window: " + clock.getWidth() + "x" + clock.getHeight() + " vs " + decor.getWidth() + "x" + decor.getHeight());
    }

    /** 붙어 있는 새 시계 뷰가 창 크기로 배치될 때까지 폴링한다. 시간 초과 시 마지막 상태를 담아 실패한다. */
    private UsagiClockView awaitLiveClock(Activity screen, String what) {
        UsagiClockView[] found = new UsagiClockView[1];
        awaitCheck(() -> { checkClockFillsWindow(screen, what + "clock"); found[0] = liveClock(screen); }, 5000);
        return found[0];
    }

    /** 검사를 통과할 때까지 반복한다(고정 sleep 대신 조건 기반 대기). 시간 초과 시 마지막 실패를 그대로 던진다. */
    private void awaitCheck(Runnable body, long timeoutMillis) {
        long end = SystemClock.elapsedRealtime() + timeoutMillis;
        while (true) {
            try {
                onMain(body);
                return;
            } catch (AssertionError last) {
                if (SystemClock.elapsedRealtime() >= end) throw last;
                SystemClock.sleep(100);
            }
        }
    }

    /** 메인 스레드에서 실행하되 검사 실패는 계측 프로세스를 죽이지 않고 호출한 쪽으로 전달한다(wm 설정 복원이 반드시 실행되도록). */
    private void onMain(Runnable body) {
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            try { body.run(); } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] instanceof AssertionError) throw (AssertionError) failure[0];
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    /** 시계를 눌러 버튼을 나타나게 하고, 버튼이 실제로 보일 때까지 기다린다(현재 시계 뷰를 매번 다시 조회). */
    private void revealControls(Activity screen, String what) {
        awaitCheck(() -> {
            UsagiClockView clock = liveClock(screen);
            check(clock != null, what + ": no attached clock view to tap");
            Button stop = find(screen.getWindow().getDecorView(), "알람 끄기");
            if (stop == null || !stop.isShown()) clock.performClick();
            stop = find(screen.getWindow().getDecorView(), "알람 끄기");
            check(stop != null && stop.isShown() && stop.isAttachedToWindow(), what + ": stop button is not shown after the tap");
        }, 4000);
    }

    /** 시계 숫자판이 창 안에 완전히 들어오는지 확인한다(숫자는 이 영역 안에 그려진다). */
    private void checkClockPanel(UsagiClockView clock, String what) {
        check(clock != null, what + ": no attached clock view");
        android.graphics.RectF panel = clock.clockPanelBounds();
        check(panel.left >= 0 && panel.top >= 0 && panel.right <= clock.getWidth() && panel.bottom <= clock.getHeight(),
                what + " is outside the window: " + panel + " in " + clock.getWidth() + "x" + clock.getHeight());
    }

    /** `5분만...`/`알람 끄기` 버튼이 창 안에 있고 서로, 그리고 시계 숫자판과 겹치지 않는지 확인한다(메인 스레드에서 호출). */
    private void checkControls(Activity screen, String what) {
        View decor = screen.getWindow().getDecorView();
        UsagiClockView clock = liveClock(screen);
        check(clock != null, what + ": no attached clock view");
        Button stop = find(decor, "알람 끄기"), snooze = find(decor, "5분만...");
        check(stop != null && snooze != null && stop.isShown() && snooze.isShown() && stop.isAttachedToWindow(),
                what + ": control buttons are not shown");
        android.graphics.Rect stopBox = checkInsideWindow(stop, decor, what + " stop button", true);
        android.graphics.Rect snoozeBox = checkInsideWindow(snooze, decor, what + " snooze button", true);
        check(!android.graphics.Rect.intersects(stopBox, snoozeBox), what + ": buttons overlap");
        android.graphics.RectF panel = clock.clockPanelBounds();
        android.graphics.RectF buttons = new android.graphics.RectF(Math.min(stopBox.left, snoozeBox.left), Math.min(stopBox.top, snoozeBox.top),
                Math.max(stopBox.right, snoozeBox.right), Math.max(stopBox.bottom, snoozeBox.bottom));
        // 일반 폰(21:9 이하)은 기존처럼 버튼이 아래 가운데에 고정이라 숫자판과 일부 겹칠 수 있다(기존 동작). 그 밖의 창만 겹침을 금지한다.
        Configuration configuration = screen.getResources().getConfiguration();
        if (WindowLayoutPolicy.mayMoveAlarmControls(configuration.screenWidthDp, configuration.screenHeightDp)) {
            check(!android.graphics.RectF.intersects(panel, buttons), what + ": buttons cover the clock panel " + panel + " vs " + buttons);
        }
    }

    /** 알람이 울리는 중 창 크기·밀도를 바꿔도 서비스 재생이 이어지고 알람 화면이 같은 인스턴스로 살아있는지 확인한다. */
    private void checkResizeWhileRinging() throws Exception {
        String where = "resize while ringing: ";
        resetWindow();
        onMain(() -> context.startForegroundService(new Intent(context, AlarmService.class)
                .putExtra("target", System.currentTimeMillis()).putExtra("lead", 0)));
        await(() -> AlarmService.active, 3000, where + "main alarm did not start");
        Activity screen = open(RingingActivity.class);
        awaitLiveClock(screen, where + "before resizing: ");
        revealControls(screen, where + "before resizing");
        await(() -> AlarmService.soundLevel > 0.01f, 3000, where + "no sound before resizing");
        for (FoldWindow window : FOLD_WINDOWS) {
            setWindow(window);
            String step = where + window.name + ": ";
            check(AlarmService.active, step + "alarm service stopped");
            check(!screen.isFinishing() && !screen.isDestroyed(), step + "alarm screen was closed or recreated");
            check(RingingActivity.visible, step + "alarm screen is not visible");
            await(() -> AlarmService.soundLevel > 0.01f, 3000, step + "sound level stayed silent");
            // 설계상 창이 바뀌면 뷰를 새로 만든다. 새 뷰가 붙고 새 창 크기로 배치될 때까지 기다린 뒤, 그 뷰를 검사한다.
            awaitCheck(() -> {
                checkClockFillsWindow(screen, step + "artwork");
                checkClockPanel(liveClock(screen), step + "clock panel");
                // 이미 나타난 버튼은 새 창에서도 유지돼야 한다.
                checkControls(screen, step + "controls");
            }, 5000);
        }
        check(store.error().isEmpty(), where + store.error());
        resetWindow();
        click(screen, "알람 끄기");
        await(() -> !AlarmService.active, 3000, where + "stop failed");

        // 설정 화면: 창이 바뀌어도 입력 중이던 값은 저장소에 남고 설정 화면이 계속 앞에 있다.
        Activity settings = open(MainActivity.class);
        onMain(() -> {
            ((EditText) settings.findViewById(R.id.time_hour)).setText("6");
            ((EditText) settings.findViewById(R.id.time_minute)).setText("47");
        });
        SystemClock.sleep(900);
        for (FoldWindow window : FOLD_WINDOWS) {
            setWindow(window);
            await(() -> topActivityIs("MainActivity"), 3000, where + window.name + ": settings screen is not in front");
            check(store.minute() == 47, where + window.name + ": draft minute was lost");
        }
        resetWindow();
        onMain(settings::finish);
    }

    private boolean topActivityIs(String simpleName) {
        for (String line : shellOutput("dumpsys activity activities").split("\n")) {
            if (line.contains("topResumedActivity=") && line.contains(simpleName)) return true;
        }
        return false;
    }

    /** view가 창 안에 있는지 확인하고 창 기준 좌표 상자를 돌려준다. 세로 확인에서는 시스템 막대 영역을 제외한다. */
    private android.graphics.Rect checkInsideWindow(View view, View decor, String what, boolean checkVertical) {
        int[] at = new int[2];
        view.getLocationInWindow(at);
        android.graphics.Rect box = new android.graphics.Rect(at[0], at[1], at[0] + view.getWidth(), at[1] + view.getHeight());
        android.view.WindowInsets insets = decor.getRootWindowInsets();
        android.graphics.Insets bars = insets == null ? android.graphics.Insets.NONE : insets.getInsets(android.view.WindowInsets.Type.systemBars());
        check(box.left >= 0 && box.right <= decor.getWidth(), what + " is outside the window horizontally: " + box + " in " + decor.getWidth());
        if (checkVertical) {
            check(box.top >= bars.top && box.bottom <= decor.getHeight() - bars.bottom,
                    what + " is outside the window vertically: " + box + " in " + decor.getHeight() + " bars=" + bars);
        }
        return box;
    }

    /** 뷰의 상자가 아니라 실제로 화면에 그려지는 영역(스크롤 영역 등에 잘린 뒤)이 뷰 전체인지 확인한다. */
    private void checkFullyVisible(View view, String what) {
        android.graphics.Rect visible = new android.graphics.Rect();
        boolean any = view.getGlobalVisibleRect(visible);
        check(any && visible.width() == view.getWidth() && visible.height() == view.getHeight(),
                what + " is cut off: visible " + (any ? visible.width() + "x" + visible.height() : "none") + " of " + view.getWidth() + "x" + view.getHeight());
    }

    private View findTextStartingWith(View view, String prefix) {
        if (view instanceof android.widget.TextView && ((android.widget.TextView) view).getText().toString().startsWith(prefix)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTextStartingWith(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 설정 화면의 권한 안내 패널이 권한 없음일 때만 보이는지 확인한다(버튼 포함). */
    private void checkOverlayPanel(boolean expectedVisible) {
        Activity screen = open(MainActivity.class);
        waitForIdleSync();
        runOnMainSync(() -> {
            View panel = screen.findViewById(R.id.overlay_panel);
            check(panel != null && (panel.getVisibility() == View.VISIBLE) == expectedVisible,
                    "Overlay guidance panel visible must be " + expectedVisible);
            check(screen.findViewById(R.id.overlay_open_settings) != null, "Overlay settings button missing");
            if (expectedVisible) check(hasText(screen.getWindow().getDecorView(), screen.getString(R.string.overlay_hint)),
                    "Overlay guidance text missing");
        });
        runOnMainSync(screen::finish);
    }

    /** 현재 SYSTEM_ALERT_WINDOW appops 모드(allow/deny/ignore/default 등). 기록이 없으면 default. */
    private String overlayMode() {
        String output = shellOutput("appops get " + context.getPackageName() + " SYSTEM_ALERT_WINDOW").trim();
        java.util.regex.Matcher mode = java.util.regex.Pattern.compile("SYSTEM_ALERT_WINDOW: (\\w+)").matcher(output);
        return mode.find() ? mode.group(1) : "default";
    }

    /** 화면을 켜고 잠금을 푼 뒤 홈 화면을 앞으로 가져온다(다른 앱을 쓰는 중인 상태를 대신한다). */
    private void showHomeScreen() {
        shellOutput("input keyevent KEYCODE_WAKEUP");
        shellOutput("wm dismiss-keyguard");
        shellOutput("input keyevent KEYCODE_HOME");
        await(this::homeVisible, 6000, "Home screen did not come to the front");
    }

    private boolean ringingInFront() {
        for (String line : shellOutput("dumpsys activity activities").split("\n")) {
            if (line.contains("topResumedActivity=") && line.contains("RingingActivity")) return true;
        }
        return false;
    }

    private void stopAlarmService() {
        runOnMainSync(() -> context.startService(new Intent(context, AlarmService.class).setAction(AlarmService.STOP)));
    }

    private boolean hasText(View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private void widgetPeriod() {
        runOnMainSync(() -> {
            for (boolean enabled : new boolean[] {false, true}) {
                for (int hour : new int[] {0, 8, 12, 20}) {
                    long target = ZonedDateTime.now().withHour(hour).withMinute(5).toInstant().toEpochMilli();
                    store.prefs.edit().putInt("hour", hour).putInt("minute", 5)
                            .putBoolean("enabled", enabled).putLong("target", target).commit();
                    View widget = AlarmWidget.views(context, target).apply(context, new android.widget.FrameLayout(context));
                    android.widget.ImageView display = widget.findViewById(R.id.widget_status);
                    android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) display.getDrawable()).getBitmap();
                    android.widget.ImageView blank = widget.findViewById(R.id.widget_status_blank);
                    android.graphics.Bitmap hidden = ((android.graphics.drawable.BitmapDrawable) blank.getDrawable()).getBitmap();
                    check(bitmap.getWidth() == hidden.getWidth() && bitmap.getHeight() == hidden.getHeight(), "Blink must preserve clock size");
                    int changed = 0;
                    for (int y = 0; y < bitmap.getHeight(); y++) {
                        for (int x = 0; x < bitmap.getWidth(); x++) {
                            if (bitmap.getPixel(x, y) != hidden.getPixel(x, y)) {
                                changed++;
                                check(x > bitmap.getWidth() * 0.4f && x < bitmap.getWidth() * 0.7f,
                                        "Blink must not move the digits or AM/PM label");
                            }
                        }
                    }
                    check(changed > 0, "Colon must disappear in the alternate frame");
                    long totalY = 0;
                    int pixels = 0;
                    // Inspect the actual AM/PM pixels to the left of the time digits.
                    for (int y = 0; y < bitmap.getHeight(); y++) {
                        for (int x = 0; x < 45; x++) {
                            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 128) {
                                totalY += y;
                                pixels++;
                            }
                        }
                    }
                    check(pixels > 0, "AM/PM label is missing");
                    float center = totalY / (float) pixels / bitmap.getHeight();
                    check(hour < 12 ? center < 0.35f : center > 0.65f,
                            "AM must be at top and PM at bottom: hour=" + hour + ", enabled=" + enabled);
                    check(display.getContentDescription().toString().startsWith(hour < 12 ? "AM" : "PM"),
                            "Incorrect period at midnight or noon");
                }
            }
        });
    }

    private void widgetHost() throws Exception {
        Activity screen = open(MainActivity.class);
        android.appwidget.AppWidgetHost host = new android.appwidget.AppWidgetHost(context, 501);
        android.appwidget.AppWidgetManager manager = android.appwidget.AppWidgetManager.getInstance(context);
        int id = host.allocateAppWidgetId();
        try {
            check(manager.bindAppWidgetIdIfAllowed(id, new android.content.ComponentName(context, AlarmWidget.class)),
                    "Widget host binding requires emulator grantbind");
            final View[] hosted = new View[1];
            runOnMainSync(() -> {
                host.startListening();
                android.widget.FrameLayout frame = new android.widget.FrameLayout(screen);
                frame.setBackgroundColor(0xffe9dfcd);
                hosted[0] = host.createView(screen, id, manager.getAppWidgetInfo(id));
                int density = (int) (screen.getResources().getDisplayMetrics().density * 100);
                android.widget.FrameLayout.LayoutParams bounds = new android.widget.FrameLayout.LayoutParams(
                        density * 210 / 100, density * 64 / 100, android.view.Gravity.CENTER);
                frame.addView(hosted[0], bounds);
                screen.setContentView(frame);
                AlarmWidget.updateAll(context);
            });
            await(() -> hosted[0].findViewById(R.id.widget_toggle) != null, 3000, "Host did not inflate widget");
            await(() -> hosted[0].findViewById(R.id.widget_status).getContentDescription() != null
                    && hosted[0].findViewById(R.id.widget_status).getContentDescription().toString().contains("꺼져"),
                    3000, "Host did not receive initial widget state");
            SystemClock.sleep(500);
            captureWidget("widget-off-preview.png");
            android.widget.ViewFlipper clock = hosted[0].findViewById(R.id.widget_clock);
            check(clock != null && clock.getChildCount() == 2 && clock.getFlipInterval() == 1000,
                    "Clock colon must blink every second");
            int colonFrame = clock.getDisplayedChild();
            await(() -> clock.getDisplayedChild() != colonFrame, 1500, "Colon did not blink with alarm disabled");
            await(() -> clock.getDisplayedChild() == colonFrame, 1500, "Colon did not reappear");
            runOnMainSync(() -> hosted[0].findViewById(R.id.widget_toggle).performClick());
            await(store::enabled, 3000, "Hosted widget did not enable alarm");
            await(() -> hosted[0].findViewById(R.id.widget_toggle).getContentDescription().toString().equals("우사기 · 알람 끄기"),
                    3000, "Hosted widget did not update");
            android.widget.ViewFlipper motion = hosted[0].findViewById(R.id.widget_motion);
            check(motion != null && motion.getFlipInterval() == 1000, "Usagi must move every second");
            check(motion.getChildCount() == 4, "Rotation must contain four poses");
            float[] angles = {-15, 0, 15, 0};
            for (int pose = 0; pose < angles.length; pose++) {
                android.graphics.drawable.Drawable face = ((android.widget.ImageView) motion.getChildAt(pose)).getDrawable();
                if (angles[pose] != 0) {
                    check(face instanceof android.graphics.drawable.RotateDrawable, "Tilt drawable missing");
                    android.graphics.drawable.RotateDrawable tilt = (android.graphics.drawable.RotateDrawable) face;
                    check(tilt.getFromDegrees() == angles[pose] && tilt.getToDegrees() == angles[pose], "Incorrect Usagi tilt");
                    check(tilt.getPivotX() == 0.5f && tilt.getPivotY() == 1f, "Tilt must pivot at bottom center");
                } else check(!(face instanceof android.graphics.drawable.RotateDrawable), "Front pose must be upright");
            }
            int firstFrame = motion.getDisplayedChild();
            for (int step = 1; step <= 4; step++) {
                int expected = (firstFrame + step) % 4;
                await(() -> motion.getDisplayedChild() == expected, 1500, "Usagi did not advance to the next pose");
            }
            SystemClock.sleep(500);
            captureWidget("widget-preview.png");
            runOnMainSync(() -> hosted[0].findViewById(R.id.widget_toggle).performClick());
            await(() -> !store.enabled(), 3000, "Hosted widget did not disable alarm");
            await(() -> hosted[0].findViewById(R.id.widget_motion) == null, 3000, "Momonga must remain still");
            String before = hosted[0].findViewById(R.id.widget_status).getContentDescription().toString().substring(0, 8);
            long untilNextMinute = 60_000 - System.currentTimeMillis() % 60_000;
            await(() -> !hosted[0].findViewById(R.id.widget_status).getContentDescription().toString().startsWith(before),
                    untilNextMinute + 5000, "Clock must advance with the alarm disabled");
        } finally {
            runOnMainSync(host::stopListening);
            host.deleteAppWidgetId(id);
            runOnMainSync(screen::finish);
        }
    }

    private void captureWidget(String name) throws java.io.IOException {
        java.io.File image = new java.io.File(context.getExternalFilesDir(null), name);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(image)) {
            android.graphics.Bitmap screenshot = getUiAutomation().takeScreenshot();
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            screenshot.recycle();
        }
    }

    private void configure(View view, int hour, int minute) {
        if (view instanceof RadioGroup) ((RadioGroup) view).check(hour < 12 ? R.id.period_am : R.id.period_pm);
        if (view instanceof EditText) {
            int n = view.getId() == R.id.time_hour ? (hour % 12 == 0 ? 12 : hour % 12)
                    : view.getId() == R.id.time_minute ? minute : 10;
            ((EditText) view).setText(String.valueOf(n));
        }
        if (view instanceof CheckBox) ((CheckBox) view).setChecked(true);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) configure(group.getChildAt(i), hour, minute);
        }
    }

    private void click(Activity screen, String label) {
        onMain(() -> {
            Button button = find(screen.getWindow().getDecorView(), label);
            check(button != null, "Button not found: " + label);
            if (!button.isShown()) {
                UsagiClockView clock = findClock(screen.getWindow().getDecorView());
                check(clock != null, "Hidden stop button requires artwork tap");
                clock.performClick();
            }
            check(button.isShown(), "Stop button did not appear after tap");
            button.performClick();
        });
    }

    private Button find(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = find(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void await(BooleanSupplier condition, long timeout, String message) {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50);
        check(condition.getAsBoolean(), message + "; " + store.error());
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
