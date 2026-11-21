package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobilePipServicePolicyTest {
    @Test public void homeToPipDoesNotPauseJustBecauseTaskChanges() {
        assertFalse(MobilePipServicePolicy.shouldPauseOnTaskRemoved(false, true));
    }
    @Test public void floatingPlayerRemainsRunningWhilePipVisible() {
        assertFalse(MobilePipServicePolicy.shouldPauseOnTaskRemoved(false, true));
    }
    @Test public void trueTaskDismissalStopsUnprotectedPlayback() {
        assertTrue(MobilePipServicePolicy.shouldPauseOnTaskRemoved(false, false));
    }
    @Test public void explicitAudioBackgroundAlwaysRetained() {
        assertFalse(MobilePipServicePolicy.shouldPauseOnTaskRemoved(true, false));
    }
}
