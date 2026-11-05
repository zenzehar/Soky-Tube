package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/**
 * Screen-space morph calculations for the handheld PiP gesture. No Android
 * dependencies, so real phone sizes, source positions and system nav bars can
 * be verified in ordinary local unit tests.
 */
final class MobilePipMorphGeometry {
    private MobilePipMorphGeometry() {}

    /**
     * Returns {translationX, translationY, scale}; screen dimensions refer
     * to the ENTIRE DISPLAY, not the original video or Activity content row.
     */
    static float[] target(float screenWidth, float screenHeight, float navigationBarBottom,
            float videoX, float videoY, float videoWidth, float videoHeight, float density) {
        screenWidth = Math.max(1f, screenWidth);
        screenHeight = Math.max(1f, screenHeight);
        videoWidth = Math.max(1f, videoWidth);
        videoHeight = Math.max(1f, videoHeight);
        density = Math.max(.1f, density);

        float miniWidth = Math.min(206f * density, screenWidth * .46f);
        float scale = Math.max(.18f, Math.min(.60f, miniWidth / videoWidth));
        float marginRight = 16f * density;
        float marginBottom = 20f * density;
        float targetCenterX = screenWidth - marginRight - videoWidth * scale / 2f;
        float targetCenterY = screenHeight - Math.max(0f, navigationBarBottom)
                - marginBottom - videoHeight * scale / 2f;
        float originalCenterX = videoX + videoWidth / 2f;
        float originalCenterY = videoY + videoHeight / 2f;
        return new float[]{targetCenterX - originalCenterX,
                targetCenterY - originalCenterY, scale};
    }
}
