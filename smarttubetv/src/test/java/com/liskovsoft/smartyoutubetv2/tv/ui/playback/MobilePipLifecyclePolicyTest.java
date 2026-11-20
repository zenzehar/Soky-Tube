package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobilePipLifecyclePolicyTest {
    @Test public void closingFloatingPlayerStopsEvenWhenNotFinishing() {
        assertTrue(MobilePipLifecyclePolicy.shouldStopOnStop(true, false, false, false, false));
    }
    @Test public void explicitBackgroundAudioRemainsPlaying() {
        assertFalse(MobilePipLifecyclePolicy.shouldStopOnStop(true, true, false, false, false));
    }
    @Test public void openingPipDoesNotStopTheDecoder() {
        assertFalse(MobilePipLifecyclePolicy.shouldStopOnStop(true, false, true, false, false));
    }
    @Test public void rotationDoesNotStopPlayingPip() {
        assertFalse(MobilePipLifecyclePolicy.shouldStopOnStop(true, false, false, true, false));
    }
    @Test public void exitingFullScreenPlaybackStopsMedia() {
        assertTrue(MobilePipLifecyclePolicy.shouldStopOnStop(false, false, false, false, true));
    }
    @Test public void ordinaryAppBackgroundDoesNotCancelIntentionalPlayback() {
        assertFalse(MobilePipLifecyclePolicy.shouldStopOnStop(false, false, false, false, false));
    }
    @org.junit.Test public void dismissFloatingVideoMustStopEvenWithoutFinishing() {
        org.junit.Assert.assertTrue(MobilePipLifecyclePolicy.shouldStopAfterPipExit(
                true, false, false, false, false));
    }

    @org.junit.Test public void expandingNativePipDoesNotEndVideo() {
        org.junit.Assert.assertFalse(MobilePipLifecyclePolicy.shouldStopAfterPipExit(
                true, true, false, false, false));
    }

    @org.junit.Test public void explicitAudioOnlySessionNotStoppedByPipExit() {
        org.junit.Assert.assertFalse(MobilePipLifecyclePolicy.shouldStopAfterPipExit(
                true, false, true, false, false));
    }

    @org.junit.Test public void visiblePipAndOrientationTransitionDoNotStop() {
        org.junit.Assert.assertFalse(MobilePipLifecyclePolicy.shouldStopAfterPipExit(
                true, false, false, true, false));
        org.junit.Assert.assertFalse(MobilePipLifecyclePolicy.shouldStopAfterPipExit(
                true, false, false, false, true));
    }

}
