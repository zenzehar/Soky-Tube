package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/**
 * Fullscreen video recovery waits for the new, MEASURED video rectangle.
 * The previous implementation returned success for the old valid surface
 * before any new frame had been drawn on the resized window.
 *
 * Keep the fallback bounded for OEMs that omit a layout-change callback.
 */
final class MobileRotationSurfacePolicy {
    // Allow slow hardware decoders to deliver their first visible frame.
    // Retry polling without repeatedly resetting the same decoder surface.
    static final int MAX_ATTEMPTS = 22;
    private static final int MEASURE_WAIT_ATTEMPTS = 5;

    private MobileRotationSurfacePolicy() {}

    static boolean waitForMeasuredFrame(boolean measured, int attempt) {
        return !measured && attempt < MEASURE_WAIT_ATTEMPTS;
    }

    static boolean shouldRebind(int attempt) {
        return attempt == 0 || attempt == 5 || attempt == 12 || attempt == 18;
    }

    /** A valid Surface alone is not proof of video. On a TextureView the
     * listener can confirm an actual newly composited frame after resizing.
     */
    static boolean hasNewFrame(long lastCompositedFrameMs, long measuredAtMs) {
        return lastCompositedFrameMs > 0 && measuredAtMs > 0 &&
                lastCompositedFrameMs >= measuredAtMs;
    }

    static boolean hasUsableFrame(int width, int height, boolean attached) {
        return attached && width > 0 && height > 0;
    }
}
