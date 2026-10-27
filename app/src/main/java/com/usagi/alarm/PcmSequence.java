package com.usagi.alarm;

import java.util.Random;

/** One continuous interleaved PCM stream; all boundaries are sample-frame aligned. */
final class PcmSequence {
    static final int RATE = 44100;
    static final int CHANNELS = 2;
    private final short[][] types;
    private final short[] start;
    private final short[] chain;
    private final Random random;
    private long preFrames;
    private int typePosition;
    private int typeIndex;
    private int mainPosition;
    private boolean started;

    PcmSequence(short[] type1, short[] type2, short[] start, short[] chain,
                long remainingPreFrames, long elapsedPreFrames, Random random) {
        if (type1.length != RATE * 2 * CHANNELS || type2.length != type1.length
                || start.length == 0 || chain.length == 0
                || start.length % CHANNELS != 0 || chain.length % CHANNELS != 0) {
            throw new IllegalArgumentException("Invalid PCM clip lengths");
        }
        this.types = new short[][]{type1, type2};
        this.start = start;
        this.chain = chain;
        this.random = random;
        preFrames = Math.max(0, remainingPreFrames);
        typePosition = (int) (Math.max(0, elapsedPreFrames) % (RATE * 2)) * CHANNELS;
        typeIndex = random.nextInt(2);
    }

    void read(short[] output) {
        if (output.length % CHANNELS != 0) throw new IllegalArgumentException("Incomplete PCM frame");
        int written = 0;
        while (written < output.length) {
            short[] source;
            int position;
            int count;
            if (preFrames > 0) {
                source = types[typeIndex];
                position = typePosition;
                count = (int) Math.min(Math.min((long) output.length - written,
                        source.length - position), preFrames * CHANNELS);
                typePosition += count;
                preFrames -= count / CHANNELS;
                if (typePosition == source.length) {
                    typePosition = 0;
                    typeIndex = random.nextInt(2);
                }
            } else {
                source = started ? chain : start;
                position = mainPosition;
                count = Math.min(output.length - written, source.length - position);
                mainPosition += count;
                if (mainPosition == source.length) {
                    mainPosition = 0;
                    started = true;
                }
            }
            System.arraycopy(source, position, output, written, count);
            written += count;
        }
    }
}
