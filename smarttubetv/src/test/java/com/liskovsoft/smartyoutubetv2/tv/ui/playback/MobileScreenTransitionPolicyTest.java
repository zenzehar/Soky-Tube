package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileScreenTransitionPolicyTest {
    @Test public void homeGestureWhilePlayingAllowsAutoPip() {
        assertTrue(MobileScreenTransitionPolicy.shouldAutoEnterPip(
                true, false, true, false, false, false, false, false));
    }
    @Test public void powerButtonAndKeyguardNeverRequestPip() {
        assertFalse(MobileScreenTransitionPolicy.shouldAutoEnterPip(
                true, false, false, false, false, false, false, false));
        assertFalse(MobileScreenTransitionPolicy.shouldAutoEnterPip(
                true, false, true, true, false, false, false, false));
    }
    @Test public void pausedAndAudioOnlyCannotAutoPip() {
        assertFalse(MobileScreenTransitionPolicy.shouldAutoEnterPip(
                false, false, true, false, false, false, false, false));
        assertFalse(MobileScreenTransitionPolicy.shouldAutoEnterPip(
                true, true, true, false, false, false, false, false));
    }
    @Test public void existingPipOrDialogWillNotLaunchAnotherPip() {
        assertFalse(MobileScreenTransitionPolicy.shouldAutoEnterPip(
                true, false, true, false, false, true, false, false));
        assertFalse(MobileScreenTransitionPolicy.shouldAutoEnterPip(
                true, false, true, false, false, false, false, true));
        assertFalse(MobileScreenTransitionPolicy.shouldAutoEnterPip(
                true, false, true, false, false, false, true, false));
    }
    @Test public void frozenVideoSurfaceRepairsOnlyAfterUnlock() {
        assertFalse(MobileScreenTransitionPolicy.shouldRestoreVideoSurface(
                true, false, true, false, true));
        assertFalse(MobileScreenTransitionPolicy.shouldRestoreVideoSurface(
                true, true, true, false, true));
        assertTrue(MobileScreenTransitionPolicy.shouldRestoreVideoSurface(
                true, true, false, false, true));
    }
    @Test public void normalResumeAndNativePipDoNotRebindSurface() {
        assertFalse(MobileScreenTransitionPolicy.shouldRestoreVideoSurface(
                false, true, false, false, true));
        assertFalse(MobileScreenTransitionPolicy.shouldRestoreVideoSurface(
                true, true, false, true, true));
        assertFalse(MobileScreenTransitionPolicy.shouldRestoreVideoSurface(
                true, true, false, false, false));
    }
}
