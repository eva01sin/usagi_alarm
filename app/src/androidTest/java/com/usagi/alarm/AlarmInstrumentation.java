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

    @Override public void onCreate(Bundle args) {
        selected = args == null ? "all" : args.getString("scenario", "all");
        start();
    }

    @Override public void onStart() {
        context = getTargetContext();
        store = new AlarmStore(context);
        scheduler = new AlarmScheduler(context);
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
            result.putString("stream", "\nPASS: " + passed + " Android integration scenarios\n");
        } catch (Throwable error) {
            result.putString("stream", "\nFAIL: " + error + "\n" + android.util.Log.getStackTraceString(error));
            resultCode = Activity.RESULT_CANCELED;
        } finally {
            if (!selected.equals("prepare") && !selected.equals("background")) runOnMainSync(scheduler::disable);
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
            check(stop != null && stop.getVisibility() == View.INVISIBLE, "Stop must initially be hidden");
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
            check(Math.abs(stop.getLeft() + stop.getWidth() / 2 - decor.getWidth() / 2) <= 1,
                    "Stop button must be horizontally centered");
            check(stop.getTop() > decor.getHeight() / 2, "Stop button must be at the bottom");
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
        runOnMainSync(() -> {
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
