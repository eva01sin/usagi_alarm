package com.usagi.alarm;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;

public final class RingingActivity extends Activity {
    static volatile boolean visible;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private UsagiClockView clock;
    private Button stop;
    private View controls;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!AlarmService.active) { finish(); return; }
            long now = System.currentTimeMillis();
            clock.setAlarmRinging(now >= AlarmService.activeTarget);
            clock.updateTime(now);
            handler.postDelayed(this, 250);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::stopAlarm);
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(UsagiClockView.BACKGROUND);
        clock = new UsagiClockView(this);
        clock.setAlarmRinging(System.currentTimeMillis() >= AlarmService.activeTarget);
        layout.addView(clock, new FrameLayout.LayoutParams(-1, -1));
        stop = new Button(this);
        stop.setText("알람 끄기");
        stop.setTextColor(UsagiClockView.INK);
        stop.setTextSize(18);
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable decoration = new GradientDrawable();
        decoration.setColor(0xF5FFF3D5);
        decoration.setCornerRadius(28 * density);
        decoration.setStroke(Math.max(1, (int) (2 * density)), UsagiClockView.INK);
        stop.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33754B32), decoration, null));
        stop.setElevation(6 * density);
        stop.setVisibility(saved != null && saved.getBoolean("stopVisible") ? View.VISIBLE : View.INVISIBLE);
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        controls = row;
        controls.setVisibility(stop.getVisibility());
        stop.setVisibility(View.VISIBLE);
        clock.setOnClickListener(view -> controls.setVisibility(View.VISIBLE));
        stop.setOnClickListener(view -> stopAlarm());
        int margin = (int) (20 * density);
        FrameLayout.LayoutParams button = new FrameLayout.LayoutParams(-2,
                (int) (56 * density), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        button.setMargins(margin, margin, margin, margin);
        Button snooze = new Button(this);
        snooze.setText("5분만...");
        snooze.setTextColor(UsagiClockView.INK);
        snooze.setTextSize(18);
        snooze.setBackground(stop.getBackground().getConstantState().newDrawable().mutate());
        snooze.setElevation(6 * density);
        snooze.setOnClickListener(view ->
                startService(new Intent(this, AlarmService.class).setAction(AlarmService.SNOOZE)));
        android.widget.LinearLayout.LayoutParams snoozeBounds = new android.widget.LinearLayout.LayoutParams((int) (150 * density), -1);
        snoozeBounds.rightMargin = (int) (8 * density);
        row.addView(snooze, snoozeBounds);
        row.addView(stop, new android.widget.LinearLayout.LayoutParams((int) (150 * density), -1));
        layout.addView(row, button);
        setContentView(layout);
        hideSystemBars();
    }

    private void stopAlarm() {
        startService(new Intent(this, AlarmService.class).setAction(AlarmService.STOP));
        finish();
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        stopAlarm();
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        stopAlarm();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("stopVisible", controls.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(state);
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        if (AlarmService.active) {
            // Preserve restart parameters if Android redelivers this service command.
            startService(new Intent(this, AlarmService.class).setAction(AlarmService.SCREEN_VISIBLE)
                    .putExtra("target", AlarmService.activeTarget).putExtra("lead", AlarmService.activeLeadSeconds));
        }
        handler.post(refresh);
    }
    @Override protected void onPause() { visible = false; handler.removeCallbacks(refresh); super.onPause(); }
}
