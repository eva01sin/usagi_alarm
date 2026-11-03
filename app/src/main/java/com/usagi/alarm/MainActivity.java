package com.usagi.alarm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.Context;
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
    private LinearLayout overlayPanel;
    private boolean updating = true, dirty, pendingEnable, fullScreenAsked;
    // 큰 창(폴더블 안쪽·태블릿)에서만 글자와 여백을 키운다. 폰은 항상 1배·기본 여백이라 기존 모습이 그대로다.
    private static final int LARGE_CONTENT_MAX_WIDTH_DP = 860;
    private boolean largeWindow;
    private boolean compactWindow;
    private float textScale = 1f;
    private Locale locale;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveDraft = () -> save(false);
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1000); }
    };

    // 모든 화면 문구가 설정 언어를 따르도록 Context에 언어를 적용한다.
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(LocaleHelper.wrap(base)); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        locale = LocaleHelper.localeOf(this);
        store = new AlarmStore(this); scheduler = new AlarmScheduler(this);
        if (saved != null) {
            pendingEnable = saved.getBoolean("pendingEnable");
            fullScreenAsked = saved.getBoolean("fullScreenAsked");
        }
        getWindow().setStatusBarColor(0xffffe599);
        getWindow().setNavigationBarColor(0xffffe599);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        // 방향이 아니라 현재 창의 크기로 배치를 정한다(접기·펴기, 분할 화면에서도 같은 기준).
        Configuration configuration = getResources().getConfiguration();
        WindowLayoutPolicy.Mode mode = WindowLayoutPolicy.modeOf(configuration.screenWidthDp, configuration.screenHeightDp);
        largeWindow = mode == WindowLayoutPolicy.Mode.LARGE;
        compactWindow = WindowLayoutPolicy.needsCompactLayout(configuration.screenWidthDp, configuration.screenHeightDp);
        textScale = !largeWindow ? 1f : compactWindow ? 1f : 1.1f;
        boolean wide = configuration.screenWidthDp >= configuration.screenHeightDp;
        boolean landscape = largeWindow ? wide : mode == WindowLayoutPolicy.Mode.PHONE_LANDSCAPE;
        FrameLayout root = new FrameLayout(this);
        ImageView background = new ImageView(this);
        background.setImageResource(landscape ? R.drawable.usagi_background : R.drawable.usagi_background_vertical);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        if (compactWindow) content.setPadding(dp(32), dp(24), dp(32), dp(12));
        else if (largeWindow) content.setPadding(dp(32), dp(28), dp(32), dp(20));
        else content.setPadding(dp(24), dp(landscape ? 12 : 24), dp(24), dp(16));
        content.setFocusableInTouchMode(true);
        TextView title = text(getString(R.string.app_name), compactWindow ? 27 : largeWindow ? 30 : 27);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);
        TextView subtitle = text(getString(R.string.subtitle), 13);
        subtitle.setPadding(0, dp(4), 0, dp(compactWindow ? 8 : 14)); content.addView(subtitle);
        LinearLayout timePanel = panel(); timePanel.addView(text(getString(R.string.alarm_time), 14));
        period = new RadioGroup(this); period.setId(R.id.time_period); period.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(i == 0 ? R.id.period_am : R.id.period_pm);
            option.setText(i == 0 ? R.string.period_am : R.string.period_pm); option.setTextColor(INK);
            option.setButtonTintList(ColorStateList.valueOf(ACCENT));
            period.addView(option, new RadioGroup.LayoutParams(0, dp(48), 1));
        }
        period.check(store.hour() < 12 ? R.id.period_am : R.id.period_pm); timePanel.addView(period);
        LinearLayout clock = new LinearLayout(this); clock.setGravity(Gravity.CENTER_VERTICAL);
        hour = number(R.id.time_hour, getString(R.string.hour_unit), store.hour() % 12 == 0 ? 12 : store.hour() % 12, 38);
        minute = number(R.id.time_minute, getString(R.string.minute_unit), store.minute(), 38);
        minute.setText(String.format(locale, "%02d", store.minute()));
        clock.addView(hour, new LinearLayout.LayoutParams(0, dp(64), 1));
        TextView colon = text(":", 32); colon.setPadding(dp(12), 0, dp(12), 0); clock.addView(colon);
        clock.addView(minute, new LinearLayout.LayoutParams(0, dp(64), 1)); timePanel.addView(clock);
        LinearLayout options = panel();
        enabled = new Switch(this); enabled.setId(R.id.alarm_enabled);
        enabled.setText(R.string.enable_alarm); enabled.setTextSize(17 * textScale); enabled.setTextColor(INK);
        enabled.setThumbTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}}, new int[] {ACCENT, 0xffa99c84}));
        enabled.setChecked(store.enabled() || pendingEnable); options.addView(enabled, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView repeat = text(getString(R.string.repeat_days), 14); repeat.setPadding(0, dp(10), 0, dp(4)); options.addView(repeat);
        LinearLayout dayRow = new LinearLayout(this);
        String[] names = getResources().getStringArray(R.array.weekday_short);
        String[] repeatDescriptions = getResources().getStringArray(R.array.weekday_repeat_description);
        days = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            CheckBox day = new CheckBox(this); days[i] = day;
            day.setId(new int[] {R.id.weekday_monday, R.id.weekday_tuesday, R.id.weekday_wednesday, R.id.weekday_thursday, R.id.weekday_friday, R.id.weekday_saturday, R.id.weekday_sunday}[i]);
            day.setText(names[i]); day.setContentDescription(repeatDescriptions[i]);
            day.setButtonDrawable(null); day.setGravity(Gravity.CENTER); day.setMinWidth(0);
            day.setPadding(0, 0, 0, 0); day.setTextSize(14);
            day.setChecked((store.days() & (1 << i)) != 0); styleDay(day);
            day.setOnCheckedChangeListener((button, checked) -> { styleDay(day); changed(); });
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(48), 1);
            cell.setMargins(dp(1), 0, dp(1), 0); dayRow.addView(day, cell);
        }
        options.addView(dayRow);
        TextView once = text(getString(R.string.repeat_once_hint), 12); once.setPadding(0, dp(4), 0, dp(8)); options.addView(once);
        LinearLayout leadRow = new LinearLayout(this); leadRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(getString(R.string.lead_label), 14); label.setLabelFor(R.id.lead_seconds);
        leadRow.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        lead = number(R.id.lead_seconds, getString(R.string.lead_description), store.leadSeconds(), 20);
        leadRow.addView(lead, new LinearLayout.LayoutParams(dp(94), dp(48))); leadRow.addView(text(getString(R.string.lead_suffix), 14)); options.addView(leadRow);
        status = text("", 12); status.setPadding(0, dp(10), 0, 0); options.addView(status);
        LinearLayout languagePanel = languagePanel();
        overlayPanel = overlayPanel();
        if (largeWindow) addLargeColumns(content, timePanel, options, overlayPanel, languagePanel);
        else addPhonePanels(content, landscape, timePanel, options, overlayPanel, languagePanel);
        // 큰 창에서는 콘텐츠 폭을 제한하고 가운데에 두어 패널이 화면 가득 늘어나지 않게 한다.
        scroll.addView(content, new FrameLayout.LayoutParams(largeWindow ? Math.min(dp(LARGE_CONTENT_MAX_WIDTH_DP), getResources().getDisplayMetrics().widthPixels) : -1,
                -2, Gravity.CENTER_HORIZONTAL));
        FrameLayout.LayoutParams scrollBounds = new FrameLayout.LayoutParams(-1, -1);
        // 큰 창은 버전 표기를 오른쪽 아래 모서리에 겹쳐 두어(아래 참고) 스크롤 영역이 창 끝까지 쓰이게 한다.
        scrollBounds.bottomMargin = largeWindow ? 0 : dp(52);
        root.addView(scroll, scrollBounds);
        TextView footer = text("ver.1.3.1\neva01sin@naver.com", 10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(10), dp(4), dp(10), dp(4));
        footer.setBackground(shape(0xE6FFFAF0, 8));
        FrameLayout.LayoutParams footerBounds = new FrameLayout.LayoutParams(-2, -2,
                largeWindow ? Gravity.BOTTOM | Gravity.END : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        if (largeWindow) footerBounds.rightMargin = dp(32);
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
    /** 폰 배치: 세로는 시간·옵션을 위아래로, 가로는 나란히 두고 그 아래에 권한 안내와 언어 패널을 붙인다(기존 구성 그대로). */
    private void addPhonePanels(LinearLayout content, boolean landscape, LinearLayout timePanel, LinearLayout options,
                                LinearLayout overlay, LinearLayout languagePanel) {
        LinearLayout panels = new LinearLayout(this);
        panels.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(landscape ? 0 : -1, -2, landscape ? 1 : 0);
        first.setMargins(0, 0, landscape ? dp(14) : 0, landscape ? 0 : dp(12)); panels.addView(timePanel, first);
        panels.addView(options, new LinearLayout.LayoutParams(landscape ? 0 : -1, -2, landscape ? 1.3f : 0));
        LinearLayout.LayoutParams languageBounds = new LinearLayout.LayoutParams(-1, -2);
        languageBounds.topMargin = dp(12);
        LinearLayout.LayoutParams overlayBounds = new LinearLayout.LayoutParams(-1, -2);
        overlayBounds.topMargin = dp(12);
        content.addView(panels); content.addView(overlay, overlayBounds);
        content.addView(languagePanel, languageBounds);
    }

    /**
     * 큰 창 배치: 왼쪽 열에 시간·옵션, 오른쪽 열에 권한 안내·언어를 둔다. 방향과 상관없이 2열이며,
     * 권한 안내가 숨겨지면(허용됨) 오른쪽 열 위쪽 여백도 함께 사라지도록 간격은 안내 패널 아래쪽에 둔다.
     */
    private void addLargeColumns(LinearLayout content, LinearLayout timePanel, LinearLayout options,
                                 LinearLayout overlay, LinearLayout languagePanel) {
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = column(), right = column();
        left.addView(timePanel);
        LinearLayout.LayoutParams optionsBounds = new LinearLayout.LayoutParams(-1, -2);
        optionsBounds.topMargin = dp(compactWindow ? 10 : 16);
        left.addView(options, optionsBounds);
        LinearLayout.LayoutParams overlayBounds = new LinearLayout.LayoutParams(-1, -2);
        overlayBounds.bottomMargin = dp(compactWindow ? 10 : 16);
        right.addView(overlay, overlayBounds);
        right.addView(languagePanel);
        LinearLayout.LayoutParams leftBounds = new LinearLayout.LayoutParams(0, -2, 1);
        leftBounds.rightMargin = dp(16);
        columns.addView(left, leftBounds);
        columns.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(columns);
    }

    /**
     * '다른 앱 위에 표시' 권한 안내 패널. 이 권한이 있어야 다른 앱을 쓰는 중에도 알람 화면이 바로 열린다.
     * 표시 여부는 {@link #updateOverlayPanel()}이 권한 상태에 맞춰 정한다.
     */
    private LinearLayout overlayPanel() {
        LinearLayout box = panel(); box.setId(R.id.overlay_panel);
        box.addView(text(getString(R.string.overlay_hint), 13));
        Button open = new Button(this); open.setId(R.id.overlay_open_settings);
        open.setText(R.string.overlay_open_settings); open.setTextColor(0xffffffff); open.setAllCaps(false);
        open.setBackground(shape(ACCENT, 12)); open.setPadding(dp(20), 0, dp(20), 0);
        open.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        LinearLayout.LayoutParams openBounds = new LinearLayout.LayoutParams(-2, dp(48));
        openBounds.topMargin = dp(8);
        box.addView(open, openBounds);
        return box;
    }
    /** 권한이 없을 때만 안내 패널을 보여 준다. 설정에서 돌아올 때(onResume)마다 다시 읽는다. */
    private void updateOverlayPanel() {
        overlayPanel.setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
    }
    /** 언어 선택 패널. 고르면 저장하고 위젯을 갱신한 뒤 화면을 새 언어로 다시 만든다. */
    private LinearLayout languagePanel() {
        LinearLayout box = panel();
        box.addView(text(getString(R.string.language_title), 14));
        int[] labels = {R.string.language_system, R.string.language_korean, R.string.language_english};
        String[] choices = {LanguagePolicy.SYSTEM, LanguagePolicy.KOREAN, LanguagePolicy.ENGLISH};
        String[] names = new String[labels.length];
        int selected = 0;
        for (int i = 0; i < labels.length; i++) {
            names[i] = getString(labels[i]);
            if (choices[i].equals(store.language())) selected = i;
        }
        Spinner spinner = new Spinner(this); spinner.setId(R.id.language_spinner);
        // 선택은 항상 저장소 값에서 정하므로 화면 상태 복원이 선택을 바꾸지 못하게 한다.
        spinner.setSaveEnabled(false);
        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, names) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                return tint(super.getView(position, convertView, parent));
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return tint(super.getDropDownView(position, convertView, parent));
            }
            private View tint(View row) { ((TextView) row).setTextColor(INK); return row; }
        });
        spinner.setSelection(selected, false);
        // 리스너는 초기 선택 뒤에 달아, 화면을 만드는 중에는 다시 그리기가 일어나지 않는다.
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updating || choices[position].equals(store.language())) return;
                store.language(choices[position]);
                AlarmWidget.updateAll(MainActivity.this);
                recreate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        box.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
        return box;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size * textScale); v.setTextColor(INK); return v;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout panel() {
        LinearLayout v = column(); if (compactWindow) v.setPadding(dp(18), dp(8), dp(18), dp(10));
        else if (largeWindow) v.setPadding(dp(20), dp(16), dp(20), dp(18));
        else v.setPadding(dp(16), dp(12), dp(16), dp(14));
        v.setBackground(shape(0xf5fffaf0, 22)); return v;
    }
    private EditText number(int id, String description, int value, int size) {
        EditText v = new EditText(this); v.setId(id); v.setContentDescription(description);
        v.setInputType(InputType.TYPE_CLASS_NUMBER); v.setSingleLine(true); v.setSelectAllOnFocus(true);
        v.setTextSize(size * textScale); v.setTextColor(INK); v.setGravity(Gravity.CENTER); v.setPadding(dp(6), 0, dp(6), 0);
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
        field.setError(!ok && show ? getString(R.string.range_error, min, max) : null); return ok;
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
        AlarmWidget.updateAll(this);
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
        if (!pendingEnable) setEnabled(store.enabled() || store.snoozeTarget() > 0);
        status.setText(pendingEnable ? getString(R.string.status_checking) : dirty ? getString(R.string.status_dirty)
                : !store.error().isEmpty() && (store.enabled() || store.snoozeTarget() > 0) ? store.errorMessage(this)
                : store.snoozeTarget() > 0 ? getString(R.string.status_snooze, DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(new Date(store.snoozeTarget())))
                : store.enabled() ? getString(R.string.status_next, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(new Date(store.target())))
                : getString(R.string.status_off));
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
        super.onResume(); updateOverlayPanel(); if (store.target() > System.currentTimeMillis() || store.snoozeTarget() > 0) scheduler.restore(false);
        handler.removeCallbacks(refresh); handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); handler.removeCallbacks(saveDraft); save(false); super.onPause(); }
}
