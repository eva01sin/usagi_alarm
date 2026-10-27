package com.usagi.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AlarmStore store = new AlarmStore(context);
        long target = intent.getLongExtra("target", 0);
        if (!store.enabled() || target != store.target()
                || intent.getLongExtra("generation", -1) != store.generation()) return;
        if (!AlarmScheduler.PRE.equals(intent.getAction()) && !AlarmScheduler.MAIN.equals(intent.getAction())) return;
        try {
            context.startForegroundService(new Intent(context, AlarmService.class)
                    .putExtra("target", target).putExtra("lead", store.leadSeconds()));
        } catch (RuntimeException e) {
            store.error("알람 서비스를 시작하지 못했습니다: " + e.getClass().getSimpleName());
        }
        if (AlarmScheduler.MAIN.equals(intent.getAction())) new AlarmScheduler(context).advance();
    }
}
