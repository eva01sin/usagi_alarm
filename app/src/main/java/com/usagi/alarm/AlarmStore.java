package com.usagi.alarm;

import android.content.Context;
import android.content.SharedPreferences;

final class AlarmStore {
    final SharedPreferences prefs;
    AlarmStore(Context context) { prefs = context.getSharedPreferences("alarm", Context.MODE_PRIVATE); }
    int hour() { return prefs.getInt("hour", 8); }
    int minute() { return prefs.getInt("minute", 0); }
    int days() { return prefs.getInt("days", 0); }
    int leadSeconds() { return prefs.getInt("lead", 10); }
    boolean enabled() { return prefs.getBoolean("enabled", false); }
    long target() { return prefs.getLong("target", 0); }
    long snoozeTarget() { return prefs.getLong("snooze", 0); }
    long generation() { return prefs.getLong("generation", 0); }
    String error() { return prefs.getString("error", ""); }
    void error(String text) { prefs.edit().putString("error", text).apply(); }
}
