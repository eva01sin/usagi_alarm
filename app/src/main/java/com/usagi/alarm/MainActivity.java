package com.usagi.alarm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int INK = 0xff51382b, ACCENT = 0xffa3442c;
    private AlarmStore store;
    private AlarmScheduler scheduler;
    private EditText hour, minute, lead;
    private RadioGroup period;
    private Switch enabled;
    private CheckBox[] days;
    private TextView status;
    private boolean updating = true, dirty, pendingEnable, fullScreenAsked;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveDraft = () -> save(false);
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new AlarmStore(this); scheduler = new AlarmScheduler(this);
        if (saved != null) {
            pendingEnable = saved.getBoolean("pendingEnable");
            fullScreenAsked = saved.getBoolean("fullScreenAsked");
        }
        getWindow().setStatusBarColor(0xffffe599);
        getWindow().setNavigationBarColor(0xffffe599);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        FrameLayout root = new FrameLayout(this);
        ImageView background = new ImageView(this);
        background.setImageResource(landscape ? R.drawable.usagi_background : R.drawable.usagi_background_vertical);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(24), dp(landscape ? 12 : 24), dp(24), dp(16));
        content.setFocusableInTouchMode(true);
        TextView title = text("우사기 알람", 27);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);
        TextView subtitle = text("작은 소리부터, 기분 좋은 아침", 13);
        subtitle.setPadding(0, dp(4), 0, dp(14)); content.addView(subtitle);
        LinearLayout panels = new LinearLayout(this);
        panels.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout timePanel = panel(); timePanel.addView(text("알람 시간", 14));
        period = new RadioGroup(this); period.setId(R.id.time_period); period.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(i == 0 ? R.id.period_am : R.id.period_pm);
            option.setText(i == 0 ? "오전" : "오후"); option.setTextColor(INK);
            option.setButtonTintList(ColorStateList.valueOf(ACCENT));
            period.addView(option, new RadioGroup.LayoutParams(0, dp(48), 1));
        }
        period.check(store.hour() < 12 ? R.id.period_am : R.id.period_pm); timePanel.addView(period);
        LinearLayout clock = new LinearLayout(this); clock.setGravity(Gravity.CENTER_VERTICAL);
        hour = number(R.id.time_hour, "시", store.hour() % 12 == 0 ? 12 : store.hour() % 12, 38);
        minute = number(R.id.time_minute, "분", store.minute(), 38);
        minute.setText(String.format(Locale.KOREA, "%02d", store.minute()));
        clock.addView(hour, new LinearLayout.LayoutParams(0, dp(64), 1));
        TextView colon = text(":", 32); colon.setPadding(dp(12), 0, dp(12), 0); clock.addView(colon);
        clock.addView(minute, new LinearLayout.LayoutParams(0, dp(64), 1)); timePanel.addView(clock);
        LinearLayout options = panel();
        enabled = new Switch(this); enabled.setId(R.id.alarm_enabled);
        enabled.setText("알림 켜기"); enabled.setTextSize(17); enabled.setTextColor(INK);
        enabled.setThumbTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}}, new int[] {ACCENT, 0xffa99c84}));
        enabled.setChecked(store.enabled() || pendingEnable); options.addView(enabled, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView repeat = text("반복 요일", 14); repeat.setPadding(0, dp(10), 0, dp(4)); options.addView(repeat);
        LinearLayout dayRow = new LinearLayout(this);
        String[] names = {"월", "화", "수", "목", "금", "토", "일"}; days = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            CheckBox day = new CheckBox(this); days[i] = day;
            day.setId(new int[] {R.id.weekday_monday, R.id.weekday_tuesday, R.id.weekday_wednesday, R.id.weekday_thursday, R.id.weekday_friday, R.id.weekday_saturday, R.id.weekday_sunday}[i]);
            day.setText(names[i]); day.setContentDescription(names[i] + "요일 반복");
            day.setButtonDrawable(null); day.setGravity(Gravity.CENTER); day.setMinWidth(0);
            day.setPadding(0, 0, 0, 0); day.setTextSize(14);
            day.setChecked((store.days() & (1 << i)) != 0); styleDay(day);
            day.setOnCheckedChangeListener((button, checked) -> { styleDay(day); changed(); });
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(48), 1);
            cell.setMargins(dp(1), 0, dp(1), 0); dayRow.addView(day, cell);
        }
        options.addView(dayRow);
        TextView once = text("선택하지 않으면 한 번만 울려요", 12); once.setPadding(0, dp(4), 0, dp(8)); options.addView(once);
        LinearLayout leadRow = new LinearLayout(this); leadRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("사전 알림", 14); label.setLabelFor(R.id.lead_seconds);
        leadRow.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        lead = number(R.id.lead_seconds, "사전 알림 시간 (초)", store.leadSeconds(), 20);
        leadRow.addView(lead, new LinearLayout.LayoutParams(dp(94), dp(48))); leadRow.addView(text("  초 전", 14)); options.addView(leadRow);
        status = text("", 12); status.setPadding(0, dp(10), 0, 0); options.addView(status);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(landscape ? 0 : -1, -2, landscape ? 1 : 0);
        first.setMargins(0, 0, landscape ? dp(14) : 0, landscape ? 0 : dp(12)); panels.addView(timePanel, first);
        panels.addView(options, new LinearLayout.LayoutParams(landscape ? 0 : -1, -2, landscape ? 1.3f : 0));
        content.addView(panels); scroll.addView(content);
        FrameLayout.LayoutParams scrollBounds = new FrameLayout.LayoutParams(-1, -1);
        scrollBounds.bottomMargin = dp(52);
        root.addView(scroll, scrollBounds);
        TextView footer = text("ver.1.1\neva01sin@naver.com", 10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(10), dp(4), dp(10), dp(4));
        footer.setBackground(shape(0xE6FFFAF0, 8));
        FrameLayout.LayoutParams footerBounds = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        footerBounds.bottomMargin = dp(20);
        root.addView(footer, footerBounds);
        setContentView(root); content.requestFocus();
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { changed(); }
            public void afterTextChanged(Editable value) {}
        };
        for (EditText input : new EditText[] {hour, minute, lead}) {
            input.addTextChangedListener(watcher);
            input.setOnFocusChangeListener((view, focused) -> { if (!focused && dirty) save(true); });
        }
        period.setOnCheckedChangeListener((group, id) -> changed());
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (updating) return;
            handler.removeCallbacks(saveDraft);
            if (!checked) { pendingEnable = false; scheduler.disable(); dirty = true; save(false); }
            else if (valid(true)) { pendingEnable = true; fullScreenAsked = false; continueEnable(); }
            else setEnabled(false);
            refreshStatus();
        });
        updating = false;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(INK); return v;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout panel() {
        LinearLayout v = column(); v.setPadding(dp(16), dp(12), dp(16), dp(14)); v.setBackground(shape(0xf5fffaf0, 22)); return v;
    }
    private EditText number(int id, String description, int value, int size) {
        EditText v = new EditText(this); v.setId(id); v.setContentDescription(description);
        v.setInputType(InputType.TYPE_CLASS_NUMBER); v.setSingleLine(true); v.setSelectAllOnFocus(true);
        v.setTextSize(size); v.setTextColor(INK); v.setGravity(Gravity.CENTER); v.setPadding(dp(6), 0, dp(6), 0);
        v.setBackground(shape(0xfff2e7d2, 12)); v.setText(String.valueOf(value)); return v;
    }
    private void styleDay(CheckBox day) {
        day.setTextColor(day.isChecked() ? 0xffffffff : INK); day.setBackground(shape(day.isChecked() ? ACCENT : 0xfff2e7d2, 12));
    }
    private void changed() { if (!updating) { dirty = true; handler.removeCallbacks(saveDraft); handler.postDelayed(saveDraft, 600); } }
    private int value(EditText field) { return Integer.parseInt(field.getText().toString().trim()); }
    private boolean range(EditText field, int min, int max, boolean show) {
        boolean ok;
        try { int n = value(field); ok = n >= min && n <= max; } catch (NumberFormatException e) { ok = false; }
        field.setError(!ok && show ? min + "~" + max + " 사이로 입력해 주세요" : null); return ok;
    }
    private boolean valid(boolean show) {
        boolean h = range(hour, 1, 12, show), m = range(minute, 0, 59, show), l = range(lead, 0, 86400, show); return h && m && l;
    }
    private void save(boolean show) {
        if (!dirty || !valid(show)) return;
        int h = value(hour) % 12 + (period.getCheckedRadioButtonId() == R.id.period_pm ? 12 : 0), mask = 0;
        for (int i = 0; i < 7; i++) if (days[i].isChecked()) mask |= 1 << i;
        if (store.enabled() && scheduler.permitted()) scheduler.save(h, value(minute), mask, value(lead));
        else store.prefs.edit().putInt("hour", h).putInt("minute", value(minute)).putInt("days", mask).putInt("lead", value(lead)).apply();
        dirty = false; refreshStatus();
    }
    private void setEnabled(boolean checked) { updating = true; enabled.setChecked(checked); updating = false; }
    private void continueEnable() {
        if (!pendingEnable) return;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1); return;
        }
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            startActivityForResult(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()), 2); return;
        }
        if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
            startActivityForResult(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())), 3); return;
        }
        if (Build.VERSION.SDK_INT >= 34 && !fullScreenAsked && !getSystemService(NotificationManager.class).canUseFullScreenIntent()) {
            fullScreenAsked = true;
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())), 4); return;
        }
        pendingEnable = false;
        if (!valid(true)) { setEnabled(false); return; }
        dirty = true; save(true);
        scheduler.save(store.hour(), store.minute(), store.days(), store.leadSeconds()); refreshStatus();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 1 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) continueEnable();
        else { pendingEnable = false; setEnabled(false); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        boolean allowed = request == 4 || (request == 2 && getSystemService(NotificationManager.class).areNotificationsEnabled())
                || (request == 3 && (Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager.class).canScheduleExactAlarms()));
        if (allowed) continueEnable(); else { pendingEnable = false; setEnabled(false); }
    }
    private void refreshStatus() {
        if (!pendingEnable) setEnabled(store.enabled());
        status.setText(pendingEnable ? "알림 권한을 확인하고 있어요" : dirty ? "입력을 마치면 자동 저장돼요"
                : !store.error().isEmpty() && store.enabled() ? store.error()
                : store.enabled() ? "다음 알림 · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(store.target()))
                : "알림이 꺼져 있어요 · 설정은 자동 저장돼요");
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("pendingEnable", pendingEnable); state.putBoolean("fullScreenAsked", fullScreenAsked); super.onSaveInstanceState(state);
    }
    @Override protected void onRestoreInstanceState(Bundle state) {
        updating = true;
        super.onRestoreInstanceState(state);
        updating = false;
    }
    @Override protected void onResume() {
        super.onResume(); if (store.target() > System.currentTimeMillis()) scheduler.restore(false);
        handler.removeCallbacks(refresh); handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); handler.removeCallbacks(saveDraft); save(false); super.onPause(); }
}
