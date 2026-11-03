package com.usagi.alarm;

import android.content.Context;
import android.content.SharedPreferences;

final class AlarmStore {
    // 오류는 언어와 무관하게 "키" 또는 "키:세부내용"으로 저장하고, 보여줄 때 설정 언어로 문구를 만든다.
    static final String ERROR_SERVICE_START = "service_start";
    static final String ERROR_PERMISSION = "permission";
    static final String ERROR_EXACT_ALARM = "exact_alarm";
    static final String ERROR_SNOOZE_PERMISSION = "snooze_permission";
    static final String ERROR_SNOOZE_EXACT_ALARM = "snooze_exact_alarm";
    static final String ERROR_ALREADY_PLAYING = "already_playing";
    static final String ERROR_FOCUS_LOST = "focus_lost";
    static final String ERROR_AUDIO_FAILED = "audio_failed";

    final SharedPreferences prefs;
    AlarmStore(Context context) { prefs = context.getSharedPreferences("alarm", Context.MODE_PRIVATE); }
    int hour() { return prefs.getInt("hour", 8); }
    int minute() { return prefs.getInt("minute", 0); }
    int days() { return prefs.getInt("days", 0); }
    int leadSeconds() { return prefs.getInt("lead", 10); }
    boolean skipHolidays() { return prefs.getBoolean("skipHolidays", false); }
    boolean calendarHolidays() { return prefs.getBoolean("calendarHolidays", false); }
    boolean enabled() { return prefs.getBoolean("enabled", false); }
    long target() { return prefs.getLong("target", 0); }
    long snoozeTarget() { return prefs.getLong("snooze", 0); }
    long generation() { return prefs.getLong("generation", 0); }
    String language() { return prefs.getString("language", LanguagePolicy.SYSTEM); }
    void language(String choice) { prefs.edit().putString("language", choice).commit(); }
    String error() { return prefs.getString("error", ""); }
    void error(String text) { prefs.edit().putString("error", text).apply(); }
    void error(String key, String detail) { error(key + ":" + detail); }

    /** 저장된 오류를 localized(설정 언어가 적용된 Context)의 언어 문구로 바꾼다. 오류가 없으면 빈 문자열. */
    String errorMessage(Context localized) {
        String raw = error();
        int separator = raw.indexOf(':');
        String key = separator < 0 ? raw : raw.substring(0, separator);
        String detail = separator < 0 ? "" : raw.substring(separator + 1);
        switch (key) {
            case ERROR_SERVICE_START: return localized.getString(R.string.error_service_start, detail);
            case ERROR_PERMISSION: return localized.getString(R.string.error_permission);
            case ERROR_EXACT_ALARM: return localized.getString(R.string.error_exact_alarm);
            case ERROR_SNOOZE_PERMISSION: return localized.getString(R.string.error_snooze_permission);
            case ERROR_SNOOZE_EXACT_ALARM: return localized.getString(R.string.error_snooze_exact_alarm);
            case ERROR_ALREADY_PLAYING: return localized.getString(R.string.error_already_playing);
            case ERROR_FOCUS_LOST: return localized.getString(R.string.error_focus_lost);
            case ERROR_AUDIO_FAILED: return localized.getString(R.string.error_audio_failed, detail);
            default: return raw;
        }
    }
}
