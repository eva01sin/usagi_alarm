package com.usagi.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        boolean clockChanged = Intent.ACTION_TIME_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction());
        if (clockChanged) context.stopService(new Intent(context, AlarmService.class));
        // Boot restores reservations only; it does not launch a playback service.
        new AlarmScheduler(context).restore(clockChanged);
    }
}
