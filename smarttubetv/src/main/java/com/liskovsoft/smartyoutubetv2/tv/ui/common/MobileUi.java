package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.View;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Touch device responsive sizing, independent of model, screen pixel density or rotation.
 *
 * The TV UI remains unchanged. Phone sizing is recalculated on every request; there
 * are no landscape-based widths or stale process-wide orientation caches.
 */
public final class MobileUi {
    private static final float SIDE_INSET_DP = 16f;
    private static final float ITEM_GAP_DP = 10f;
    private static final float MAX_CARD_WIDTH_DP = 220f;
    private MobileUi() {}

    public static boolean isHandheld(Context context) {
        if (context == null) return false;
        Configuration config = context.getResources().getConfiguration();
        if ((config.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) {
            return false;
        }
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }

    public static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** Use the measured Activity content width, not the display's longest axis.
     * Config.screenWidthDp provides the correct window width before the first layout pass,
     * including Android split-screen and multi-window resizing.
     */
    public static float windowWidthDp(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = Math.max(0.1f, metrics.density);
        if (context instanceof Activity) {
            View content = ((Activity) context).findViewById(android.R.id.content);
            if (content != null && content.getWidth() > 0) {
                return content.getWidth() / density;
            }
        }
        int widthDp = context.getResources().getConfiguration().screenWidthDp;
        return widthDp > 0 ? widthDp : metrics.widthPixels / density;
    }

    /** Pure sizing calculation, exposed for small-phone/tablet/rotation unit tests. */
    public static int columnsForWidthDp(float widthDp, boolean shorts) {
        float minimumCardDp = shorts ? (widthDp >= 600f ? 150f : 136f)
                : (widthDp >= 600f ? 170f : 140f);
        float usable = Math.max(0f, widthDp - SIDE_INSET_DP * 2f);
        int cols = (int) Math.floor((usable + ITEM_GAP_DP) /
                (minimumCardDp + ITEM_GAP_DP));
        return Math.max(1, Math.min(6, cols));
    }

    public static float cardWidthForWidthDp(float widthDp, boolean shorts) {
        int cols = columnsForWidthDp(widthDp, shorts);
        float usable = Math.max(0f, widthDp - SIDE_INSET_DP * 2f
                - ITEM_GAP_DP * (cols - 1));
        // Never force a minimum wider than the window.
        return Math.max(1f, cols == 1 ? usable : Math.min(MAX_CARD_WIDTH_DP, usable / cols));
    }

    /** Full-width YouTube-style feed in portrait; responsive grids on large windows. */
    public static int feedColumnsForWidthDp(float windowWidthDp) {
        return windowWidthDp >= 1080f ? 3 : windowWidthDp >= 600f ? 2 : 1;
    }

    public static int columns(Context context, int cardWidthResId) {
        return columnsForWidthDp(windowWidthDp(context),
                cardWidthResId == R.dimen.shorts_card_width);
    }

    public static Pair<Integer, Integer> cardSize(Context context, int widthResId, int heightResId) {
        boolean shorts = widthResId == R.dimen.shorts_card_width;
        int width = dp(context, cardWidthForWidthDp(windowWidthDp(context), shorts));
        float heightRatio = shorts ? (16f / 9f) :
                ((float) context.getResources().getDimensionPixelSize(heightResId)
                / Math.max(1, context.getResources().getDimensionPixelSize(widthResId)));
        return new Pair<>(width, Math.round(width * heightRatio));
    }

    public static int toolbarHeightPx(Context context) {
        // Increase header height for large accessibility fonts, keeping tap areas >=48dp.
        float fontScale = context.getResources().getConfiguration().fontScale;
        return dp(context, Math.max(54f, Math.min(72f, 48f + 8f * fontScale)));
    }
}
