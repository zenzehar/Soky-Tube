package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/** Lock-screen lifecycle safety for the handset player (not TV). */
final class MobileScreenLockPolicy {
    private MobileScreenLockPolicy() {}

    /** Retain the current player when locking, but never manufacture a Play. */
    static boolean retainOnStop(boolean deviceLocking, boolean activityAlive,
            boolean playingBeforeLock, boolean currentlyPlaying) {
        return deviceLocking && activityAlive && (playingBeforeLock || currentlyPlaying);
    }
}
