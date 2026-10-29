package com.usagi.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AlarmStore store = new AlarmStore(context);
        long target = intent.getLongExtra("target", 0);
        if (AlarmScheduler.SNOOZE.equals(intent.getAction())) {
            if (target == 0 || target != store.snoozeTarget()
                    || intent.getLongExtra("generation", -1) != store.generation()) return;
            start(context, store, target, 0);
            new AlarmScheduler(context).cancelSnooze();
            return;
        }
        if (!store.enabled() || target != store.target()
                || intent.getLongExtra("generation", -1) != store.generation()) return;
        if (!AlarmScheduler.PRE.equals(intent.getAction()) && !AlarmScheduler.MAIN.equals(intent.getAction())) return;
        start(context, store, target, store.leadSeconds());
        if (AlarmScheduler.MAIN.equals(intent.getAction())) new AlarmScheduler(context).advance();
    }

    private void start(Context context, AlarmStore store, long target, int lead) {
        try {
            context.startForegroundService(new Intent(context, AlarmService.class)
                    .putExtra("target", target).putExtra("lead", lead));
        } catch (RuntimeException e) {
            store.error("알람 서비스를 시작하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
