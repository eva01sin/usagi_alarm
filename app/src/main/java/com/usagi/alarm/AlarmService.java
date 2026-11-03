package com.usagi.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import java.util.Random;

public final class AlarmService extends Service {
    static final String STOP = "com.usagi.alarm.STOP";
    static final String SCREEN_VISIBLE = "com.usagi.alarm.SCREEN_VISIBLE";
    static final String CHANNEL = "alarm_playback";
    static final String PLAYBACK_CHANNEL = "alarm_playback_quiet";
    private static final int NOTIFICATION = 10;
    private static final int MAIN_NOTIFICATION = 12;
    private static final int PLAYBACK_NOTIFICATION = 13;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile AudioTrack track;
    private volatile float focusVolume = 1f;
    private final Object audioLock = new Object();
    private Thread worker;
    private PowerManager.WakeLock wakeLock;
    private AudioFocusRequest focus;
    private boolean hasAudioFocus;
    private long target;
    private int notificationId;
    static volatile float soundLevel;
    static volatile boolean active;
    static volatile long activeTarget;
    static volatile int activeLeadSeconds;

    // 앱이 꺼진 상태에서 시작돼도 알림 문구가 설정 언어를 따르도록 Context에 언어를 적용한다.
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(LocaleHelper.wrap(base)); }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.channel_alarm_name), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(getString(R.string.channel_alarm_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        NotificationChannel playback = new NotificationChannel(PLAYBACK_CHANNEL, getString(R.string.channel_playback_name), NotificationManager.IMPORTANCE_LOW);
        playback.setDescription(getString(R.string.channel_playback_description));
        playback.setSound(null, null);
        playback.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(playback);
    }

    // The user explicitly ends playback; the wake lock is released in onDestroy.
    @SuppressLint("WakelockTimeout")
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            if (intent != null && running) {
                AlarmStore store = new AlarmStore(this);
                // Before MAIN is delivered, dismiss this occurrence as well as its sound.
                // Already-advanced repeats and preview playback have a different target.
                if (store.enabled() && store.target() == target) new AlarmScheduler(this).advance();
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        if (SCREEN_VISIBLE.equals(intent.getAction()) && !running && (flags & START_FLAG_REDELIVERY) == 0) {
            // A delayed UI refresh must not resurrect a dismissed alarm. Only an
            // explicit Android redelivery may restore playback after process death.
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        long requestedTarget = intent.getLongExtra("target", 0);
        if (requestedTarget == 0) { stopSelf(); return START_NOT_STICKY; }
        if (running) {
            // The exact main alarm is a fallback, not a second audio player.
            if (requestedTarget != target) new AlarmStore(this).error(AlarmStore.ERROR_ALREADY_PLAYING);
            else if (SCREEN_VISIBLE.equals(intent.getAction()) && RingingActivity.visible) showQuietNotification();
            return START_REDELIVER_INTENT;
        }
        target = requestedTarget;
        activeTarget = target;
        activeLeadSeconds = intent.getIntExtra("lead", 0);
        boolean pre = target > System.currentTimeMillis();
        notificationId = pre ? NOTIFICATION : MAIN_NOTIFICATION;
        startForeground(notificationId, notification(pre, false));
        openScreenIfAllowed();
        soundLevel = 0f;
        running = true;
        active = true;
        PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UsagiAlarm:Playback");
        wakeLock.acquire();
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS) {
                        new AlarmStore(this).error(AlarmStore.ERROR_FOCUS_LOST);
                        stopSelf();
                    } else {
                        focusVolume = change == AudioManager.AUDIOFOCUS_GAIN ? 1f : 0f;
                        synchronized (audioLock) {
                            if (track != null) track.setVolume(focusVolume);
                        }
                    }
                }, handler).build();
        AudioManager audio = getSystemService(AudioManager.class);
        hasAudioFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!hasAudioFocus) {
            // Audio focus controls cooperation with other apps; it is not permission to
            // play a user-scheduled USAGE_ALARM track in a mediaPlayback foreground service.
            // Android 15 can deny focus to an exact-alarm-started FGS. Keep the alarm
            // alive, without ducking other apps, and mute while call audio is in use.
            Log.w("UsagiAlarm", "Audio focus unavailable; using alarm stream with call-aware muting");
            focusVolume = audio.getMode() == AudioManager.MODE_NORMAL ? 1f : 0f;
        }
        int lead = intent.getIntExtra("lead", 0);
        worker = new Thread(() -> play(attributes, lead), "UsagiPcm");
        worker.start();
        if (pre) handler.postDelayed(showMain, Math.max(0, target - System.currentTimeMillis()));
        return START_REDELIVER_INTENT;
    }

    private final Runnable showMain = () -> {
        if (running) {
            if (RingingActivity.visible) {
                showQuietNotification();
                return;
            }
            // A high-importance entry is only needed to reopen a closed alarm screen.
            int previousId = notificationId;
            notificationId = MAIN_NOTIFICATION;
            startForeground(notificationId, notification(false, false));
            if (previousId != notificationId) getSystemService(NotificationManager.class).cancel(previousId);
            openScreenIfAllowed();
        }
    };

    /**
     * 기기를 사용 중이면 전체 화면 intent가 상단 알림 카드로만 표시되므로, '다른 앱 위에 표시' 권한이 있을 때
     * 알람 화면을 서비스가 직접 연다. 권한이 없거나 화면이 이미 떠 있으면 아무것도 하지 않는다(알림은 항상 폴백으로 유지).
     * singleTop + SINGLE_TOP 플래그라 열리는 중에 다시 불려도 화면이 겹치지 않는다.
     */
    private void openScreenIfAllowed() {
        if (!ScreenLaunchPolicy.shouldOpenDirectly(Settings.canDrawOverlays(this), RingingActivity.visible)) return;
        try {
            startActivity(new Intent(this, RingingActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (RuntimeException e) {
            // 직접 열기가 막혀도 알림의 전체 화면 intent와 알림 탭이 남아 있으므로 알람 재생은 계속한다.
            Log.w("UsagiAlarm", "Could not open the alarm screen directly", e);
        }
    }

    private void showQuietNotification() {
        int previousId = notificationId;
        notificationId = PLAYBACK_NOTIFICATION;
        startForeground(notificationId, notification(System.currentTimeMillis() < target, true));
        if (previousId != notificationId) getSystemService(NotificationManager.class).cancel(previousId);
    }

    private Notification notification(boolean pre, boolean quiet) {
        Intent open = new Intent(this, RingingActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent screen = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 11, new Intent(this, AlarmService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, quiet ? PLAYBACK_CHANNEL : CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(getString(pre ? R.string.notification_pre_title : R.string.notification_main_title))
                .setContentText(getString(pre ? R.string.notification_pre_text : R.string.notification_main_text))
                .setCategory(quiet ? Notification.CATEGORY_SERVICE : Notification.CATEGORY_ALARM)
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(screen)
                .addAction(new Notification.Action.Builder(null, getString(R.string.stop_alarm), stop).build());
        if (!quiet && !RingingActivity.visible && (Build.VERSION.SDK_INT < 34
                || getSystemService(NotificationManager.class).canUseFullScreenIntent())) {
            builder.setFullScreenIntent(screen, true);
        }
        return builder.build();
    }

    private void play(AudioAttributes attributes, int lead) {
        AudioTrack local = null;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            short[] type1 = PcmAssets.load(this, "type_1");
            short[] type2 = PcmAssets.load(this, "type_2");
            short[] start = PcmAssets.load(this, "alarm_start");
            short[] chain = PcmAssets.load(this, "alarm_chain");
            int minimum = AudioTrack.getMinBufferSize(PcmSequence.RATE, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("Unsupported audio format");
            local = new AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(PcmSequence.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum * 2, 17640)).build();
            if (local.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("AudioTrack initialization failed");
            synchronized (audioLock) { track = local; local.setVolume(focusVolume); }
            long remaining = Math.max(0, target - System.currentTimeMillis());
            long untilMain = remaining * PcmSequence.RATE / 1000;
            long elapsed = Math.max(0, lead * (long) PcmSequence.RATE - untilMain);
            PcmSequence sequence = new PcmSequence(type1, type2, start, chain, untilMain, elapsed, new Random());
            short[] block = new short[882]; // 10 ms, no timer gaps between clips.
            AudioManager audio = getSystemService(AudioManager.class);
            int blocks = 0;
            long previousHead = 0, playedFrames = 0;
            float callVolume = focusVolume;
            local.play();
            while (running) {
                if (!hasAudioFocus && blocks++ % 10 == 0) {
                    callVolume = audio.getMode() == AudioManager.MODE_NORMAL ? 1f : 0f;
                    local.setVolume(callVolume);
                }
                sequence.read(block);
                int offset = 0;
                while (running && offset < block.length) {
                    int count = local.write(block, offset, block.length - offset, AudioTrack.WRITE_BLOCKING);
                    if (count < 0) throw new IllegalStateException("AudioTrack write failed: " + count);
                    if (count == 0) SystemClock.sleep(1);
                    offset += count;
                }
                long head = Integer.toUnsignedLong(local.getPlaybackHeadPosition());
                playedFrames += (head - previousHead) & 0xffffffffL;
                previousHead = head;
                soundLevel = (hasAudioFocus ? focusVolume : callVolume)
                        * AlarmMotion.level(start, chain, playedFrames - untilMain);
            }
        } catch (Exception e) {
            if (running) {
                new AlarmStore(this).error(AlarmStore.ERROR_AUDIO_FAILED, String.valueOf(e.getMessage()));
                handler.post(this::stopSelf);
            }
        } finally {
            synchronized (audioLock) {
                track = null;
                soundLevel = 0f;
                if (local != null) {
                    if (local.getState() == AudioTrack.STATE_INITIALIZED) local.stop();
                    local.release();
                }
            }
        }
    }

    @Override public void onDestroy() {
        running = false;
        active = false;
        soundLevel = 0f;
        activeTarget = 0;
        activeLeadSeconds = 0;
        handler.removeCallbacksAndMessages(null);
        synchronized (audioLock) {
            if (track != null) { track.pause(); track.flush(); }
        }
        if (worker != null) {
            try { worker.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (focus != null && hasAudioFocus) getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
