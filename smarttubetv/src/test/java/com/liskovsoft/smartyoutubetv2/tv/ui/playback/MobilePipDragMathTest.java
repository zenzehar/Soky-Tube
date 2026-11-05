package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobilePipDragMathTest {
    @Test public void videoTracksTheFingerVerticallyOneForOne() {
        float[] a = MobilePipDragMath.follow(20f, 0f, 400f, 1750f, .35f, 3f);
        float[] b = MobilePipDragMath.follow(90f, 0f, 400f, 1750f, .35f, 3f);
        float[] c = MobilePipDragMath.follow(260f, 0f, 400f, 1750f, .35f, 3f);
        assertEquals(20f, a[1], .01f);
        assertEquals(90f, b[1], .01f);
        assertEquals(260f, c[1], .01f);
        assertTrue(c[2] < b[2] && b[2] < a[2]);
    }
    @Test public void earlyHorizontalTrackingIsByFinger() {
        float[] result = MobilePipDragMath.follow(28f, 37f, 450f, 1800f, .36f, 3f);
        assertEquals(37f, result[0], 1f);
    }
    @Test public void gravityTowardBottomRightIncreasesWithPull() {
        float[] a = MobilePipDragMath.follow(20f, 0f, 450f, 1800f, .36f, 3f);
        float[] b = MobilePipDragMath.follow(850f, 0f, 450f, 1800f, .36f, 3f);
        assertTrue(b[0] > a[0] + 50f);
        assertTrue(b[2] < a[2]);
    }
    @Test public void lightPullReturnsAndDeliberatePullCommits() {
        assertFalse(MobilePipDragMath.shouldMinimize(90f, 400f, 3f));
        assertTrue(MobilePipDragMath.shouldMinimize(400f, 400f, 3f));
    }
    @Test public void fastDownFlickCommitsButUpFlickAndTapDoNot() {
        assertTrue(MobilePipDragMath.shouldMinimize(80f, 4000f, 3f));
        assertFalse(MobilePipDragMath.shouldMinimize(80f, -4000f, 3f));
        assertFalse(MobilePipDragMath.shouldMinimize(25f, 4000f, 3f));
    }
    @Test public void quickFlickSettlesFaster() {
        assertTrue(MobilePipDragMath.settleDurationMs(4000f, 3f) <
                MobilePipDragMath.settleDurationMs(200f, 3f));
    }
}
