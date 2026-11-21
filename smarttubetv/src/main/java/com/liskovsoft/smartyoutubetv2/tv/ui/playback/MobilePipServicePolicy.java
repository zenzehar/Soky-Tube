package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/** Whether task removal is permission to stop the *existing* PiP player. */
final class MobilePipServicePolicy {
    private MobilePipServicePolicy() {}

    /** Home/Recents can deliver onTaskRemoved while PiP is animating. */
    static boolean shouldPauseOnTaskRemoved(boolean explicitBackgroundAudio,
            boolean pipWindowVisibleOrEntering) {
        return shouldPauseOnTaskRemoved(explicitBackgroundAudio,
                pipWindowVisibleOrEntering, false);
    }

    /** Car-driven audio is independent of the phone task and remains active
     * after Recents removes the UI. The media session owns its teardown. */
    static boolean shouldPauseOnTaskRemoved(boolean explicitBackgroundAudio,
            boolean pipWindowVisibleOrEntering, boolean carAudioInUse) {
        return !explicitBackgroundAudio && !pipWindowVisibleOrEntering &&
                !carAudioInUse;
    }
}
