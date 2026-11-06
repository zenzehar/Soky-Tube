package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/** Pure state checks for phone Home-to-PiP versus power-button screen lock. */
final class MobileScreenTransitionPolicy {
    private MobileScreenTransitionPolicy() {}

    static boolean shouldAutoEnterPip(boolean playing, boolean audioOnly,
            boolean interactive, boolean keyguardLocked, boolean finishing,
            boolean inPip, boolean transitionPending, boolean dialogOpen) {
        return playing && !audioOnly && interactive && !keyguardLocked &&
                !finishing && !inPip && !transitionPending && !dialogOpen;
    }

    static boolean shouldRestoreVideoSurface(boolean screenOffSinceLastVisible,
            boolean interactive, boolean keyguardLocked, boolean inPip,
            boolean playerReady) {
        // Never redraw over Android's keyguard. Only repair a real existing
        // player surface after the user has unlocked and returned to fullscreen.
        return screenOffSinceLastVisible && interactive && !keyguardLocked &&
                !inPip && playerReady;
    }
}
