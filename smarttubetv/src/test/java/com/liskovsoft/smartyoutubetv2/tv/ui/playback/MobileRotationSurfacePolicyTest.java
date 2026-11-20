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

    @Test public void detachedOrZeroSizedVideoCannotCountAsRecovered() {
        assertFalse(MobileRotationSurfacePolicy.hasUsableFrame(1920, 1080, false));
        assertFalse(MobileRotationSurfacePolicy.hasUsableFrame(0, 1080, true));
        assertFalse(MobileRotationSurfacePolicy.hasUsableFrame(1920, 0, true));
        assertTrue(MobileRotationSurfacePolicy.hasUsableFrame(1920, 1080, true));
    }
}
