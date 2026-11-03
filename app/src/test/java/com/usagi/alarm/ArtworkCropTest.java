package com.usagi.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ArtworkCropTest {
    // 그림 좌표: {left, top, right, bottom}. UsagiClockView가 쓰는 값과 같다.
    private static final float[] SCENE = {1692, 930};
    private static final float[] SCENE_KEEP = ArtworkCrop.SCENE_KEEP;
    private static final float[] SCENE_PANEL = ArtworkCrop.SCENE_ESSENTIAL;
    private static final float[] ALARM = {1703, 924};
    private static final float[] ALARM_KEEP = ArtworkCrop.ALARM_KEEP;
    private static final float[] ALARM_PANEL = ArtworkCrop.ALARM_ESSENTIAL;
    private static final float[] RATIOS = {0.5f, 0.6f, 0.8f, 0.85f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.6f, 16 / 9f, 2.0f, 2.4f};

    private static ArtworkCrop crop(float[] art, float[] keep, float[] essential, float ratio) {
        float height = 800;
        return ArtworkCrop.of(height * ratio, height, art[0], art[1], keep, essential);
    }

    private static void assertCoversView(ArtworkCrop c, float[] art, float ratio) {
        float width = 800 * ratio, height = 800;
        assertTrue("left edge blank at " + ratio, c.left <= 0.001f);
        assertTrue("top edge blank at " + ratio, c.top <= 0.001f);
        assertTrue("right edge blank at " + ratio, c.left + art[0] * c.scale >= width - 0.01f);
        assertTrue("bottom edge blank at " + ratio, c.top + art[1] * c.scale >= height - 0.01f);
    }

    private static void assertInside(ArtworkCrop c, float[] region, float ratio, String name) {
        float width = 800 * ratio, height = 800;
        assertTrue(name + " left cut at " + ratio, region[0] * c.scale + c.left >= -0.01f);
        assertTrue(name + " top cut at " + ratio, region[1] * c.scale + c.top >= -0.01f);
        assertTrue(name + " right cut at " + ratio, region[2] * c.scale + c.left <= width + 0.01f);
        assertTrue(name + " bottom cut at " + ratio, region[3] * c.scale + c.top <= height + 0.01f);
    }

    @Test public void artworkAlwaysFillsTheWindow() {
        for (float ratio : RATIOS) {
            assertCoversView(crop(SCENE, SCENE_KEEP, SCENE_PANEL, ratio), SCENE, ratio);
            assertCoversView(crop(ALARM, ALARM_KEEP, ALARM_PANEL, ratio), ALARM, ratio);
        }
    }

    @Test public void characterAndClockStayVisibleFromTallToNearSquareToWide() {
        for (float ratio : RATIOS) {
            if (ratio < 0.8f) continue; // 아주 좁은 창은 아래 essential 테스트에서 다룬다.
            assertInside(crop(SCENE, SCENE_KEEP, SCENE_PANEL, ratio), SCENE_KEEP, ratio, "scene keep");
            assertInside(crop(ALARM, ALARM_KEEP, ALARM_PANEL, ratio), ALARM_KEEP, ratio, "alarm keep");
        }
    }

    @Test public void clockPanelStaysVisibleEvenInVeryNarrowWindows() {
        for (float ratio : RATIOS) {
            assertInside(crop(SCENE, SCENE_KEEP, SCENE_PANEL, ratio), SCENE_PANEL, ratio, "scene panel");
            assertInside(crop(ALARM, ALARM_KEEP, ALARM_PANEL, ratio), ALARM_PANEL, ratio, "alarm panel");
        }
    }

    @Test public void phoneLandscapeKeepsTheOriginalCenterCrop() {
        for (float ratio : new float[] {16 / 9f, 2.0f, 2.4f}) {
            for (float[] art : new float[][] {SCENE, ALARM}) {
                float[] keep = art == SCENE ? SCENE_KEEP : ALARM_KEEP;
                float[] essential = art == SCENE ? SCENE_PANEL : ALARM_PANEL;
                ArtworkCrop c = crop(art, keep, essential, ratio);
                float scale = Math.max(800 * ratio / art[0], 800 / art[1]);
                assertEquals(scale, c.scale, 1e-4f);
                assertEquals((800 * ratio - art[0] * scale) / 2, c.left, 0.01f);
                assertEquals((800 - art[1] * scale) / 2, c.top, 0.01f);
            }
        }
    }

    @Test public void exactArtworkRatioHasNoOffset() {
        ArtworkCrop c = ArtworkCrop.of(1692, 930, 1692, 930, SCENE_KEEP, SCENE_PANEL);
        assertEquals(1f, c.scale, 1e-4f);
        assertEquals(0f, c.left, 0.01f);
        assertEquals(0f, c.top, 0.01f);
    }
}
