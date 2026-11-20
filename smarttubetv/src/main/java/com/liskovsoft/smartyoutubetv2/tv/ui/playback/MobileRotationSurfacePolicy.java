package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/**
 * Fullscreen video recovery waits for the new, MEASURED video rectangle.
 * The previous implementation returned success for the old valid surface
 * before any new frame had been drawn on the resized window.
 *
 * Keep the fallback bounded for OEMs that omit a layout-change callback.
 */
final class MobileRotationSurfacePolicy {
    static final int MAX_ATTEMPTS = 9;
    private static final int MEASURE_WAIT_ATTEMPTS = 5;

    private MobileRotationSurfacePolicy() {}

    static boolean waitForMeasuredFrame(boolean measured, int attempt) {
        return !measured && attempt < MEASURE_WAIT_ATTEMPTS;
    }

    static boolean hasUsableFrame(int width, int height, boolean attached) {
        return attached && width > 0 && height > 0;
    }
}
