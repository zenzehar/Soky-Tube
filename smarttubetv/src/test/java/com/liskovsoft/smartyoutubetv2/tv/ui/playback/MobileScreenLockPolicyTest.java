package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileScreenLockPolicyTest {
    @Test public void lockRetainsAlreadyPlayingSession() {
        assertTrue(MobileScreenLockPolicy.retainOnStop(true, true, true, true));
    }
    @Test public void momentaryPlayerPauseDuringLockDoesNotDestroySession() {
        assertTrue(MobileScreenLockPolicy.retainOnStop(true, true, true, false));
    }
    @Test public void intentionalPauseDoesNotCreateBackgroundPlayback() {
        assertFalse(MobileScreenLockPolicy.retainOnStop(true, true, false, false));
    }
    @Test public void PiPDismissalWhileScreenInteractiveStillCleansUp() {
        assertFalse(MobileScreenLockPolicy.retainOnStop(false, true, true, true));
    }
    @Test public void finishingAlwaysOverridesScreenLockRetention() {
        assertFalse(MobileScreenLockPolicy.retainOnStop(true, false, true, true));
    }
}
