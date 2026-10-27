package com.usagi.alarm;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AlarmMotionTest {
    @Test public void preMotionAlternatesEverySecond() {
        assertEquals(1f, AlarmMotion.preOffset(0), 0.0001f);
        assertEquals(-1f, AlarmMotion.preOffset(1000), 0.0001f);
        assertEquals(1f, AlarmMotion.preOffset(2000), 0.0001f);
        assertEquals(0f, AlarmMotion.preOffset(500), 0.0001f);
    }

    @Test public void noShakeBeforeMainOrDuringSilence() {
        short[] silent = new short[882];
        assertEquals(0f, AlarmMotion.level(silent, silent, -1), 0);
        assertEquals(0f, AlarmMotion.level(silent, silent, 0), 0);
        assertEquals(0f, AlarmMotion.level(silent, silent, 100000), 0);
    }

    @Test public void shakeFollowsIntroThenLoopsChain() {
        short[] intro = new short[882], chain = new short[882];
        Arrays.fill(intro, (short) 4096);
        Arrays.fill(chain, (short) 8192);
        assertEquals(0.375f, AlarmMotion.level(intro, chain, 0), 0.0001f);
        assertEquals(0.75f, AlarmMotion.level(intro, chain, 441), 0.0001f);
        assertEquals(0.75f, AlarmMotion.level(intro, chain, 441L * 100), 0.0001f);
        assertTrue(AlarmMotion.level(intro, chain, 220) > 0.375f);
    }

    @Test public void fullScaleShakeIsBounded() {
        short[] clip = new short[882];
        Arrays.fill(clip, Short.MIN_VALUE);
        assertEquals(1f, AlarmMotion.level(clip, clip, 0), 0);
    }
}