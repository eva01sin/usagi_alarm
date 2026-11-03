package com.usagi.alarm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.RectF;
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
    private boolean snoozing;
    private int builtDensityDpi, builtWidthDp, builtHeightDp;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!AlarmService.active) { finish(); return; }
            long now = System.currentTimeMillis();
            clock.setAlarmRinging(now >= AlarmService.activeTarget);
            clock.updateTime(now);
            placeControls();
            handler.postDelayed(this, 250);
        }
    };

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(LocaleHelper.wrap(base)); }

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
        applyOrientationPolicy();
        buildScreen(saved != null && saved.getBoolean("stopVisible"));
        hideSystemBars();
    }

    /**
     * 알람 화면의 뷰를 만든다. 접고 펴거나 화면 밀도가 바뀔 때(onConfigurationChanged)도 다시 불러, dp 기준 크기·글자·
     * 버튼 폭을 새 창에 맞춘다. 서비스(소리)와 무관하므로 화면을 다시 만들어도 알람은 끊기지 않는다.
     *
     * @param controlsVisible `5분만...`/`알람 끄기` 버튼이 이미 나타나 있는지(다시 만들 때 유지)
     */
    private void buildScreen(boolean controlsVisible) {
        Configuration built = getResources().getConfiguration();
        builtDensityDpi = built.densityDpi; builtWidthDp = built.screenWidthDp; builtHeightDp = built.screenHeightDp;
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(UsagiClockView.BACKGROUND);
        clock = new UsagiClockView(this);
        clock.setAlarmRinging(System.currentTimeMillis() >= AlarmService.activeTarget);
        layout.addView(clock, new FrameLayout.LayoutParams(-1, -1));
        stop = new Button(this);
        stop.setText(R.string.stop_alarm);
        stop.setTextColor(UsagiClockView.INK);
        stop.setTextSize(18);
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable decoration = new GradientDrawable();
        decoration.setColor(0xF5FFF3D5);
        decoration.setCornerRadius(28 * density);
        decoration.setStroke(Math.max(1, (int) (2 * density)), UsagiClockView.INK);
        stop.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33754B32), decoration, null));
        stop.setElevation(6 * density);
        stop.setVisibility(controlsVisible ? View.VISIBLE : View.INVISIBLE);
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
        // 좁은 창(분할 화면 등)에서도 두 버튼이 화면 밖으로 나가지 않도록 폭을 창에 맞춰 줄인다(최대 150dp).
        int availableWidth = getResources().getDisplayMetrics().widthPixels - 2 * margin - (int) (8 * density);
        int buttonWidth = Math.max((int) (110 * density), Math.min((int) (150 * density), availableWidth / 2));
        Button snooze = new Button(this);
        snooze.setText(R.string.snooze_button);
        snooze.setTextColor(UsagiClockView.INK);
        snooze.setTextSize(18);
        snooze.setBackground(stop.getBackground().getConstantState().newDrawable().mutate());
        snooze.setElevation(6 * density);
        snooze.setOnClickListener(view -> snoozeAlarm());
        android.widget.LinearLayout.LayoutParams snoozeBounds = new android.widget.LinearLayout.LayoutParams(buttonWidth, -1);
        snoozeBounds.rightMargin = (int) (8 * density);
        row.addView(snooze, snoozeBounds);
        row.addView(stop, new android.widget.LinearLayout.LayoutParams(buttonWidth, -1));
        layout.addView(row, button);
        setContentView(layout);
    }

    /**
     * `5분만...`/`알람 끄기` 버튼 줄은 기본적으로 아래 가운데에 둔다. 21:9처럼 아주 긴 창은 그림이 위아래로 많이 잘려 시계 숫자판이
     * 버튼 줄 높이까지 내려오므로, 가운데 배치가 숫자판을 가리면 숫자판의 반대쪽 아래 모서리로 옮긴다.
     * 이동은 {@link WindowLayoutPolicy#mayMoveAlarmControls}가 허용한 창에서만 한다.
     * 사전→본 알람으로 그림이 바뀌면 숫자판 위치도 바뀌므로 화면 갱신 주기마다 다시 판단한다.
     */
    private void placeControls() {
        Configuration configuration = getResources().getConfiguration();
        // 일반 폰(16:9~21:9)은 숫자판과 일부 겹쳐도 기존처럼 아래 가운데에 둔다. 옮기는 것은 큰 창·더 길쭉한 접힌 창뿐이다.
        if (!WindowLayoutPolicy.mayMoveAlarmControls(configuration.screenWidthDp, configuration.screenHeightDp)) return;
        if (clock == null || controls == null || clock.getWidth() == 0 || controls.getWidth() == 0) return;
        FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) controls.getLayoutParams();
        float rowTop = clock.getHeight() - bounds.height - bounds.topMargin - bounds.bottomMargin;
        float rowLeft = (clock.getWidth() - controls.getWidth()) / 2f;
        RectF panel = clock.clockPanelBounds();
        boolean covers = panel.bottom > rowTop && panel.right > rowLeft && panel.left < rowLeft + controls.getWidth();
        int gravity = Gravity.BOTTOM | (!covers ? Gravity.CENTER_HORIZONTAL
                : panel.centerX() > clock.getWidth() / 2f ? Gravity.START : Gravity.END);
        if (bounds.gravity == gravity) return;
        bounds.gravity = gravity;
        controls.setLayoutParams(bounds);
    }

    /**
     * 알람 화면의 방향 잠금을 창 크기로 정한다. 폰(작은 창)은 기존처럼 가로 고정이고, 폴더블 안쪽·태블릿처럼
     * 큰 창은 잠그지 않아 기기 자세를 따른다. 매니페스트의 가로 고정은 폰의 첫 표시 때 회전 깜박임이 없도록 남겨 두고,
     * 큰 창일 때만 여기서 풀어 준다.
     */
    private void applyOrientationPolicy() {
        android.content.res.Configuration configuration = getResources().getConfiguration();
        boolean large = WindowLayoutPolicy.modeOf(configuration.screenWidthDp, configuration.screenHeightDp)
                == WindowLayoutPolicy.Mode.LARGE;
        int wanted = large ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        // 같은 값을 다시 요청하면 시스템이 불필요한 설정 변경을 만들 수 있어 바뀔 때만 호출한다.
        if (getRequestedOrientation() != wanted) setRequestedOrientation(wanted);
    }

    /**
     * 접기·펴기, 창 크기·밀도 변경은 매니페스트의 configChanges로 여기서 받는다. 액티비티를 다시 만들지 않으므로
     * 알람 상태(AlarmService)와 화면 표시 여부는 그대로이고, 뷰만 새 창에 맞춰 다시 배치한다.
     */
    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientationPolicy();
        // 밀도나 창 크기가 실제로 바뀐 경우에만 뷰를 다시 만든다(같은 값의 반복 통지로 비트맵을 다시 읽지 않는다).
        if (newConfig.densityDpi == builtDensityDpi && newConfig.screenWidthDp == builtWidthDp && newConfig.screenHeightDp == builtHeightDp) return;
        boolean controlsVisible = controls != null && controls.getVisibility() == View.VISIBLE;
        buildScreen(controlsVisible);
        hideSystemBars();
    }

    private void stopAlarm() {
        startService(new Intent(this, AlarmService.class).setAction(AlarmService.STOP));
        finish();
    }

    private void snoozeAlarm() {
        if (!AlarmService.active) return;
        if (!new AlarmScheduler(this).snooze(AlarmService.activeTarget)) {
            android.widget.Toast.makeText(this, new AlarmStore(this).errorMessage(this), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        snoozing = true;
        stopService(new Intent(this, AlarmService.class));
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        stopAlarm();
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!snoozing) stopAlarm();
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
