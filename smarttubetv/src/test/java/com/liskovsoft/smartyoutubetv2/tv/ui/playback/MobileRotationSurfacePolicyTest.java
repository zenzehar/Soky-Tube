package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileRotationSurfacePolicyTest {
    @Test public void oldSurfaceMustNotBeConsideredRecoveredBeforeNewLayout() {
        for (int retry = 0; retry < 5; retry++) {
            assertTrue(MobileRotationSurfacePolicy.waitForMeasuredFrame(false, retry));
        }
    }

    @Test public void rebindAsSoonAsNewMeasuredFullscreenFrameExists() {
        assertFalse(MobileRotationSurfacePolicy.waitForMeasuredFrame(true, 0));
        assertFalse(MobileRotationSurfacePolicy.waitForMeasuredFrame(true, 3));
    }

    @Test public void boundedFallbackAllowsRecoveryWhenOemMissesLayoutCallback() {
        assertFalse(MobileRotationSurfacePolicy.waitForMeasuredFrame(false, 5));
        assertTrue(MobileRotationSurfacePolicy.MAX_ATTEMPTS > 5);
    }

    @Test public void validSurfaceWithoutNewFrameIsNotRecovery() {
        assertFalse(MobileRotationSurfacePolicy.hasNewFrame(0, 1000));
        assertFalse(MobileRotationSurfacePolicy.hasNewFrame(-1, 1000));
        assertFalse(MobileRotationSurfacePolicy.hasNewFrame(999, 1000));
        assertTrue(MobileRotationSurfacePolicy.hasNewFrame(1001, 1000));
    }

    @Test public void rebindsAreSpacedNotTriggeredEveryFramePoll() {
        assertTrue(MobileRotationSurfacePolicy.shouldRebind(0));
        assertFalse(MobileRotationSurfacePolicy.shouldRebind(1));
        assertTrue(MobileRotationSurfacePolicy.shouldRebind(5));
        assertFalse(MobileRotationSurfacePolicy.shouldRebind(6));
        assertTrue(MobileRotationSurfacePolicy.shouldRebind(12));
        assertFalse(MobileRotationSurfacePolicy.shouldRebind(13));
        assertTrue(MobileRotationSurfacePolicy.shouldRebind(18));
    }

    @Test public void detachedOrZeroSizedVideoCannotCountAsRecovered() {
        assertFalse(MobileRotationSurfacePolicy.hasUsableFrame(1920, 1080, false));
        assertFalse(MobileRotationSurfacePolicy.hasUsableFrame(0, 1080, true));
        assertFalse(MobileRotationSurfacePolicy.hasUsableFrame(1920, 0, true));
        assertTrue(MobileRotationSurfacePolicy.hasUsableFrame(1920, 1080, true));
    }
}
