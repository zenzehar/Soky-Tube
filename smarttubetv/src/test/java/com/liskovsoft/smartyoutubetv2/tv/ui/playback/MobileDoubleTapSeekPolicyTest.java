package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileDoubleTapSeekPolicyTest {
    @Test public void leftDoubleTapRewindsExactlyTenSeconds() {
        int side = MobileDoubleTapSeekPolicy.direction(20f, 360f);
        assertEquals(MobileDoubleTapSeekPolicy.BACKWARD, side);
        assertEquals(50_000L, MobileDoubleTapSeekPolicy.target(60_000L, 150_000L, side));
    }
    @Test public void rightDoubleTapAdvancesExactlyTenSeconds() {
        int side = MobileDoubleTapSeekPolicy.direction(330f, 360f);
        assertEquals(MobileDoubleTapSeekPolicy.FORWARD, side);
        assertEquals(70_000L, MobileDoubleTapSeekPolicy.target(60_000L, 150_000L, side));
    }
    @Test public void clampsAtBeginningAndEnd() {
        assertEquals(0L, MobileDoubleTapSeekPolicy.target(4_000L, 90_000L,
                MobileDoubleTapSeekPolicy.BACKWARD));
        assertEquals(90_000L, MobileDoubleTapSeekPolicy.target(86_000L, 90_000L,
                MobileDoubleTapSeekPolicy.FORWARD));
    }
    @Test public void unknownDurationDoesNotTriggerSeek() {
        assertEquals(-1L, MobileDoubleTapSeekPolicy.target(1_000L, -1L,
                MobileDoubleTapSeekPolicy.FORWARD));
    }
    @Test public void negativePositionNeverSeeksBelowZero() {
        assertEquals(0L, MobileDoubleTapSeekPolicy.target(-3_000L, 90_000L,
                MobileDoubleTapSeekPolicy.BACKWARD));
    }
}
