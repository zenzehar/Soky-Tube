package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

/** Finger-tracked PiP drag and a separate fast-fling release rule. */
final class MobilePipDragMath {
    private MobilePipDragMath() {}

    /** Returns {translationX, translationY, scale}. No autoplay while finger is held. */
    static float[] follow(float dragY, float dragX, float targetX, float targetY,
            float finalScale, float density) {
        float dy = Math.max(0f, dragY);
        float densitySafe = Math.max(.1f, density);
        float travel = Math.max(1f, targetY);
        float range = Math.max(240f * densitySafe,
                Math.min(travel * .70f, 550f * densitySafe));
        float progress = Math.min(1f, dy / range);
        float attract = progress * progress * progress;
        float horizontal = dragX * (1f - attract) + targetX * attract;
        float vertical = Math.min(dy, travel);
        float scale = 1f - (1f - finalScale) * progress;
        return new float[]{horizontal, vertical, scale};
    }

    static boolean shouldMinimize(float dragY, float velocityY, float density) {
        density = Math.max(.1f, density);
        // Deliberate long pull, or a genuinely fast downward flick.
        return dragY >= 125f * density ||
                (dragY >= 24f * density && velocityY >= 1100f * density);
    }

    static long settleDurationMs(float velocityY, float density) {
        return velocityY >= 1100f * Math.max(.1f, density) ? 135L : 220L;
    }
}
