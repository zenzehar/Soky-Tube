package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/** Explicit user intent is mandatory for a headless Android Auto cold resume. */
final class MobileCarResumePolicy {
    private MobileCarResumePolicy() {}

    static boolean mayColdResume(boolean userPressedPlay,
            boolean phonePlayerOwnsSession, boolean savedPlayableVideo) {
        return userPressedPlay && !phonePlayerOwnsSession && savedPlayableVideo;
    }
}
