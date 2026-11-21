package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/** Whether task removal is permission to stop the *existing* PiP player. */
final class MobilePipServicePolicy {
    private MobilePipServicePolicy() {}

    /** Home/Recents can deliver onTaskRemoved while PiP is animating. */
    static boolean shouldPauseOnTaskRemoved(boolean explicitBackgroundAudio,
            boolean pipWindowVisibleOrEntering) {
        return !explicitBackgroundAudio && !pipWindowVisibleOrEntering;
    }
}
