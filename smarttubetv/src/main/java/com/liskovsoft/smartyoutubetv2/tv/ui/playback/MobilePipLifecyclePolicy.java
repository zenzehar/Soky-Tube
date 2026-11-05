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
}
