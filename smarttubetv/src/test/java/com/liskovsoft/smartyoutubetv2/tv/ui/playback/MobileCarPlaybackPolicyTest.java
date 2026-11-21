package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileCarPlaybackPolicyTest {
    @Test public void pausedCarBrowserSurvivesPhoneLockAndActivityStop() {
        assertTrue(MobileCarPlaybackPolicy.keepPausedSession(
                true, false, true, false, false));
    }

    @Test public void explicitBackgroundAudioRetainedWithoutCar() {
        assertTrue(MobileCarPlaybackPolicy.keepPausedSession(
                false, true, true, false, false));
    }

    @Test public void ordinaryPausedVideoMayReleaseWhenBackgrounded() {
        assertFalse(MobileCarPlaybackPolicy.keepPausedSession(
                false, false, true, false, false));
    }

    @Test public void closingPipOrFinishingOverridesConnectedCar() {
        assertFalse(MobileCarPlaybackPolicy.keepPausedSession(
                true, false, true, false, true));
        assertFalse(MobileCarPlaybackPolicy.keepPausedSession(
                true, false, true, true, false));
    }

    @Test public void noDecoderCannotResurrectAudioOnLockScreen() {
        assertFalse(MobileCarPlaybackPolicy.keepPausedSession(
                true, false, false, false, false));
    }
}
