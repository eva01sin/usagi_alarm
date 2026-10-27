package com.usagi.alarm;

import org.junit.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.Assert.*;

public class PcmSequenceTest {
    private static short[] clip(int frames, int value) {
        short[] samples = new short[frames * 2];
        Arrays.fill(samples, (short) value);
        return samples;
    }

    private static class Alternating extends Random {
        int calls;
        @Override public int nextInt(int bound) { return calls++ % bound; }
    }

    private static PcmSequence sequence(long preFrames, long elapsed, Random random) {
        return new PcmSequence(clip(88200, 1), clip(88200, 2), clip(3, 3), clip(2, 4), preFrames, elapsed, random);
    }

    @Test public void changesRandomTypeEveryTwoSeconds() {
        Alternating random = new Alternating();
        PcmSequence sequence = sequence(5L * 44100, 0, random);
        short[] output = new short[5 * 44100 * 2];
        sequence.read(output);
        for (int frame = 0; frame < 5 * 44100; frame++) {
            short expected = (short) (frame < 88200 ? 1 : frame < 176400 ? 2 : 1);
            assertEquals("frame " + frame, expected, output[frame * 2]);
            assertEquals(expected, output[frame * 2 + 1]);
        }
    }

    @Test public void oddLeadCutsPreAtExactFrameAndStartsOnceThenLoops() {
        PcmSequence sequence = sequence(44100, 0, new Alternating());
        short[] pre = new short[44100 * 2 - 2];
        sequence.read(pre);
        short[] boundary = new short[24];
        sequence.read(boundary);
        assertArrayEquals(new short[]{1,1, 3,3, 3,3, 3,3, 4,4, 4,4, 4,4, 4,4, 4,4, 4,4, 4,4, 4,4}, boundary);
    }

    @Test public void noLeadStartsImmediatelyAndNeverReplaysIntro() {
        PcmSequence sequence = sequence(0, 0, new Random(1));
        short[] output = new short[30];
        sequence.read(output);
        for (int i = 0; i < output.length; i++) assertEquals(i < 6 ? 3 : 4, output[i]);
    }

    @Test public void delayedLaunchResumesWithinCurrentTwoSecondSlot() {
        PcmSequence sequence = sequence(44100, 44100, new Alternating());
        short[] output = new short[44100 * 2 + 2];
        sequence.read(output);
        assertEquals(1, output[0]);
        assertEquals(1, output[output.length - 3]);
        assertEquals(3, output[output.length - 1]);
    }

    @Test public void arbitraryReadBoundariesDoNotChangeStream() {
        PcmSequence whole = sequence(90001, 0, new Random(42));
        PcmSequence chunks = sequence(90001, 0, new Random(42));
        short[] expected = new short[200000];
        short[] actual = new short[expected.length];
        whole.read(expected);
        for (int offset = 0; offset < actual.length;) {
            int count = Math.min(882, actual.length - offset);
            short[] block = new short[count];
            chunks.read(block);
            System.arraycopy(block, 0, actual, offset, count);
            offset += count;
        }
        assertArrayEquals(expected, actual);
    }
}
