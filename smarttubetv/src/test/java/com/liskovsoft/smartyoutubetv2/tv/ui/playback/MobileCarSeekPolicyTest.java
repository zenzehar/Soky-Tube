package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileCarSeekPolicyTest {
    @Test public void forwardTenSeconds() {
        assertEquals(20_000L, MobileCarSeekPolicy.targetMs(10_000, 100_000, true, true));
    }
    @Test public void backwardTenSeconds() {
        assertEquals(500L, MobileCarSeekPolicy.targetMs(10_500, 100_000, true, false));
    }
    @Test public void boundToStart() {
        assertEquals(0L, MobileCarSeekPolicy.targetMs(5_000, 100_000, true, false));
    }
    @Test public void boundToEnd() {
        assertEquals(45_000L, MobileCarSeekPolicy.targetMs(40_000, 45_000, true, true));
    }
    @Test public void unknownDurationAllowsForward() {
        assertEquals(22_345L, MobileCarSeekPolicy.targetMs(12_345, -1, true, true));
    }
    @Test public void unseekableLiveStreamIsDisabled() {
        assertEquals(-1L, MobileCarSeekPolicy.targetMs(12_345, 90_000, false, true));
    }
    @Test public void noPreparedPositionIsDisabled() {
        assertEquals(-1L, MobileCarSeekPolicy.targetMs(-1, -1, true, false));
    }
}
