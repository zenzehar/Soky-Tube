package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/** Exact ten-second Android Auto jumps, never seek a live/unseekable stream. */
final class MobileCarSeekPolicy {
    static final long JUMP_MS = 10_000L;
    private MobileCarSeekPolicy() {}

    /** -1 is an unavailable action; duration <= 0 means unknown duration. */
    static long targetMs(long currentMs, long durationMs, boolean seekable,
            boolean skipForward) {
        if (!seekable || currentMs < 0) return -1;
        long current = Math.max(0, currentMs);
        if (skipForward) {
            long end = durationMs > 0 ? durationMs : Long.MAX_VALUE;
            return current >= end - JUMP_MS ? end : current + JUMP_MS;
        }
        return Math.max(0, current - JUMP_MS);
    }
}
