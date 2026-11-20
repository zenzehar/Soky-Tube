package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/** Pure Android lifecycle decision, isolated from the TV player behaviour. */
final class MobilePipLifecyclePolicy {
    private MobilePipLifecyclePolicy() {}

    /**
     * PiP dismissal calls onStop on many Android devices without first setting
     * Activity.isFinishing(). Closing PiP is not a request for audio-only play.
     * Keep transitional/configuration and explicit audio-only sessions alive.
     */
    static boolean shouldStopOnStop(boolean pipSessionActive, boolean audioOnly,
            boolean enteringPip, boolean changingConfiguration, boolean finishing) {
        if (audioOnly || changingConfiguration || enteringPip) return false;
        return pipSessionActive || finishing;
    }

    /** PiP exit is distinct from fullscreen expansion. The screen must NOT
     * require isFinishing() or PowerManager.isInteractive() to release audio.
     */
    static boolean shouldStopAfterPipExit(boolean pipSessionActive,
            boolean playbackResumed, boolean explicitAudioOnly,
            boolean inSystemPip, boolean changingConfiguration) {
        return pipSessionActive && !playbackResumed && !explicitAudioOnly &&
                !inSystemPip && !changingConfiguration;
    }
}
