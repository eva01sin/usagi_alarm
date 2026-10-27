package com.usagi.alarm;

/** Motion follows the samples at AudioTrack's playback head, rather than queued writes. */
final class AlarmMotion {
    private AlarmMotion() {}

    static float preOffset(long elapsedMillis) {
        return (float) Math.cos(Math.PI * (elapsedMillis % 2000) / 1000.0);
    }

    static float level(short[] start, short[] chain, long mainFrame) {
        if (mainFrame < 0) return 0f;
        double squares = 0;
        int frames = PcmSequence.RATE / 100; // A 10 ms energy window.
        long startFrames = start.length / PcmSequence.CHANNELS;
        long chainFrames = chain.length / PcmSequence.CHANNELS;
        for (int i = 0; i < frames; i++) {
            long frame = mainFrame + i;
            short[] clip = frame < startFrames ? start : chain;
            int index = (int) (frame < startFrames ? frame : (frame - startFrames) % chainFrames) * PcmSequence.CHANNELS;
            for (int channel = 0; channel < PcmSequence.CHANNELS; channel++) {
                double sample = clip[index + channel] / 32768.0;
                squares += sample * sample;
            }
        }
        return Math.min(1f, (float) Math.sqrt(squares / (frames * PcmSequence.CHANNELS)) * 3f);
    }
}