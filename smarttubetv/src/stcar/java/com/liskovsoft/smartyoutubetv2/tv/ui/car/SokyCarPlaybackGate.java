package com.liskovsoft.smartyoutubetv2.tv.ui.car;

import android.os.Build;

/**
 * Fail-closed Android Auto VIDEO policy for this diagnostic-only car flavor.
 *
 * A navigation SurfaceCallback is a map drawing surface, not a supported
 * video streaming activity or a trusted parked-state authorisation.
 * This class intentionally has no user-override, speed-based inference,
 * debug bypass, or API that can claim "parked" from an arbitrary caller.
 *
 * The separate Soky Tube mobile MediaBrowserService can continue to provide
 * normal audio playback. It is NOT controlled by this visual-only gate.
 */
final class SokyCarPlaybackGate {
    private SokyCarPlaybackGate() {}

    /** The current projected Android Auto experiment NEVER attaches video. */
    static boolean canRenderCarVideo() {
        return false;
    }

    static String blockReason() {
        if (Build.VERSION.SDK_INT < 35) {
            return "Android " + Build.VERSION.RELEASE
                    + ": direct parked video activity unavailable";
        }
        return "No supported video category or trusted parked state";
    }

    static String diagnosticState() {
        return "VIDEO BLOCKED - parked state not authorised";
    }
}
