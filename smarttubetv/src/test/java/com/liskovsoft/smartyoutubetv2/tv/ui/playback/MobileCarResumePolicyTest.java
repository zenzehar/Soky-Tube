package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileCarResumePolicyTest {
    @Test public void carConnectAloneMustNotStartVideo() {
        assertFalse(MobileCarResumePolicy.mayColdResume(false, false, true));
    }
    @Test public void explicitPlayResumesSavedVideoWithoutPhoneActivity() {
        assertTrue(MobileCarResumePolicy.mayColdResume(true, false, true));
    }
    @Test public void onePlayerOwnerOnly() {
        assertFalse(MobileCarResumePolicy.mayColdResume(true, true, true));
    }
    @Test public void neverInventMediaIfNoVideoWasSelected() {
        assertFalse(MobileCarResumePolicy.mayColdResume(true, false, false));
    }
    @Test public void resumeIdentifiersAreOnlyYouTubeVideoIds() {
        assertTrue(MobileCarResumeState.validVideoId("dQw4w9WgXcQ"));
        assertFalse(MobileCarResumeState.validVideoId("https://example.com/x"));
        assertFalse(MobileCarResumeState.validVideoId("../../.secret"));
    }
}
