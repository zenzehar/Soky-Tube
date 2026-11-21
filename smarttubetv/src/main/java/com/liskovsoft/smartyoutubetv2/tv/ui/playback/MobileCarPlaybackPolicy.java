package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/**
 * Phone/car lifecycle invariant: a paused ExoPlayer MUST NOT be destroyed
 * simply because the display locks while Android Auto still owns a media
 * browser connection. This is independent of playWhenReady.
 *
 * Explicit user closure of PiP or Activity finish still wins.
 */
final class MobileCarPlaybackPolicy {
    private MobileCarPlaybackPolicy() {}

    static boolean keepPausedSession(boolean carBrowserConnected,
            boolean explicitBackgroundAudio, boolean hasPreparedVideo,
            boolean finishing, boolean pipDismissed) {
        return hasPreparedVideo && !finishing && !pipDismissed &&
                (carBrowserConnected || explicitBackgroundAudio);
    }
}
