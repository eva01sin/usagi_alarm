package com.usagi.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.usagi.alarm.WindowLayoutPolicy.Mode;
import org.junit.Test;

public final class WindowLayoutPolicyTest {
    @Test public void phonePortraitStaysPhone() {
        assertEquals(Mode.PHONE_PORTRAIT, WindowLayoutPolicy.modeOf(360, 800));
        assertEquals(Mode.PHONE_PORTRAIT, WindowLayoutPolicy.modeOf(411, 731));
    }

    @Test public void phoneLandscapeStaysPhone() {
        assertEquals(Mode.PHONE_LANDSCAPE, WindowLayoutPolicy.modeOf(800, 360));
        assertEquals(Mode.PHONE_LANDSCAPE, WindowLayoutPolicy.modeOf(731, 411));
        assertEquals(Mode.PHONE_LANDSCAPE, WindowLayoutPolicy.modeOf(915, 412));
    }

    @Test public void foldedNarrowWindowIsPhonePortrait() {
        assertEquals(Mode.PHONE_PORTRAIT, WindowLayoutPolicy.modeOf(360, 820));
        assertEquals(Mode.PHONE_LANDSCAPE, WindowLayoutPolicy.modeOf(820, 360));
    }

    @Test public void unfoldedNearSquareWindowIsLargeInEitherShape() {
        assertEquals(Mode.LARGE, WindowLayoutPolicy.modeOf(673, 586));
        assertEquals(Mode.LARGE, WindowLayoutPolicy.modeOf(586, 673));
        assertEquals(Mode.LARGE, WindowLayoutPolicy.modeOf(931, 775));
        assertEquals(Mode.LARGE, WindowLayoutPolicy.modeOf(600, 600));
    }

    @Test public void tabletIsLargeInEitherOrientation() {
        assertEquals(Mode.LARGE, WindowLayoutPolicy.modeOf(1280, 800));
        assertEquals(Mode.LARGE, WindowLayoutPolicy.modeOf(800, 1280));
    }

    @Test public void lowLargeWindowsNeedCompactLayoutButPhonesDoNot() {
        assertTrue(WindowLayoutPolicy.needsCompactLayout(931, 558));
        assertTrue(WindowLayoutPolicy.needsCompactLayout(673, 586));
        assertTrue(WindowLayoutPolicy.needsCompactLayout(1280, 639));
        assertFalse(WindowLayoutPolicy.needsCompactLayout(931, 775));
        assertFalse(WindowLayoutPolicy.needsCompactLayout(775, 931));
        assertFalse(WindowLayoutPolicy.needsCompactLayout(800, 360));
        assertFalse(WindowLayoutPolicy.needsCompactLayout(360, 800));
    }

    @Test public void ordinaryPhonesNeverMoveAlarmControls() {
        assertFalse(WindowLayoutPolicy.mayMoveAlarmControls(360, 800));
        assertFalse(WindowLayoutPolicy.mayMoveAlarmControls(411, 731));
        assertFalse(WindowLayoutPolicy.mayMoveAlarmControls(731, 411));
        assertFalse(WindowLayoutPolicy.mayMoveAlarmControls(800, 400));
        assertFalse(WindowLayoutPolicy.mayMoveAlarmControls(800, 360));
        assertFalse(WindowLayoutPolicy.mayMoveAlarmControls(720, 360));
    }

    @Test public void exactlyTwentyOneByNineStillKeepsTheCenteredControls() {
        assertFalse(WindowLayoutPolicy.mayMoveAlarmControls(840, 360));
        assertFalse(WindowLayoutPolicy.mayMoveAlarmControls(360, 840));
        assertTrue(WindowLayoutPolicy.mayMoveAlarmControls(850, 360));
    }

    @Test public void longerFoldedWindowAndLargeWindowsMayMoveControls() {
        assertTrue(WindowLayoutPolicy.mayMoveAlarmControls(884, 344));
        assertTrue(WindowLayoutPolicy.mayMoveAlarmControls(344, 884));
        assertTrue(WindowLayoutPolicy.mayMoveAlarmControls(931, 775));
        assertTrue(WindowLayoutPolicy.mayMoveAlarmControls(775, 931));
        assertTrue(WindowLayoutPolicy.mayMoveAlarmControls(1280, 800));
    }

    @Test public void thresholdIsOnShortSide() {
        int edge = WindowLayoutPolicy.LARGE_SHORT_SIDE_DP;
        assertEquals(Mode.LARGE, WindowLayoutPolicy.modeOf(edge, 900));
        assertEquals(Mode.PHONE_PORTRAIT, WindowLayoutPolicy.modeOf(edge - 1, 900));
        assertEquals(Mode.PHONE_LANDSCAPE, WindowLayoutPolicy.modeOf(900, edge - 1));
    }
}
