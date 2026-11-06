package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/** Stable playback-time mathematics for edge double-tap seeking on phones. */
final class MobileDoubleTapSeekPolicy {
    static final long STEP_MS = 10_000L;
    static final int BACKWARD = -1;
    static final int FORWARD = 1;

    private MobileDoubleTapSeekPolicy() {}

    static int direction(float x, float playerWidth) {
        return x < Math.max(1f, playerWidth) * .5f ? BACKWARD : FORWARD;
    }

    /** -1 means duration unavailable; never request a misleading seek. */
    static long target(long positionMs, long durationMs, int direction) {
        if (durationMs <= 0) return -1L;
        long position = Math.max(0L, Math.min(positionMs, durationMs));
        return direction == BACKWARD
                ? Math.max(0L, position - STEP_MS)
                : Math.min(durationMs, position + STEP_MS);
    }
}
