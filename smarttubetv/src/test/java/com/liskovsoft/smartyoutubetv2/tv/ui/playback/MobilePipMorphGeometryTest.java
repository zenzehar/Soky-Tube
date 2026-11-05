package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regressions: never morph to the bottom-right of the 16:9 player row. */
public class MobilePipMorphGeometryTest {
    @Test public void portraitPhoneMiniPlayerEndsAtFullDisplayBottomRight() {
        float[] morph = MobilePipMorphGeometry.target(
                1080f, 2400f, 96f, 0f, 90f, 1080f, 608f, 3f);
        float miniWidth = 1080f * morph[2];
        float miniHeight = 608f * morph[2];
        float finalRight = 540f + morph[0] + miniWidth / 2f;
        float finalBottom = 90f + 304f + morph[1] + miniHeight / 2f;
        assertEquals(1080f - 48f, finalRight, .1f);
        assertEquals(2400f - 96f - 60f, finalBottom, .1f);
        // If it were tied to the video window, the thumbnail would end near
        // ~700 px, not below 2200 px near the device navigation bar.
        assertTrue(finalBottom > 2100f);
    }

    @Test public void tabletTargetUsesScreenWidthNotPlayerWidth() {
        float[] morph = MobilePipMorphGeometry.target(
                1600f, 2560f, 0f, 0f, 120f, 1600f, 900f, 2f);
        float finalRight = 800f + morph[0] + 800f * morph[2];
        float finalBottom = 570f + morph[1] + 450f * morph[2];
        assertEquals(1600f - 32f, finalRight, .1f);
        assertEquals(2560f - 40f, finalBottom, .1f);
        assertTrue(morph[1] > 1000f);
    }

    @Test public void landscapePhoneHasFiniteScaleAndTarget() {
        float[] morph = MobilePipMorphGeometry.target(
                2400f, 1080f, 0f, 0f, 0f, 2400f, 1080f, 3f);
        assertTrue(morph[2] > 0 && morph[2] <= .60f);
        assertTrue(Float.isFinite(morph[0]));
        assertTrue(Float.isFinite(morph[1]));
    }

    @Test public void navigationBarReducesDestinationByItsOwnInset() {
        float[] without = MobilePipMorphGeometry.target(
                1080f, 2400f, 0f, 0f, 90f, 1080f, 608f, 3f);
        float[] with = MobilePipMorphGeometry.target(
                1080f, 2400f, 100f, 0f, 90f, 1080f, 608f, 3f);
        assertEquals(without[1] - 100f, with[1], .1f);
    }
}
