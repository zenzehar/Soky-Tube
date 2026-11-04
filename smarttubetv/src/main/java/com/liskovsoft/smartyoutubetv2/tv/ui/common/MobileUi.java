package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** Detects a real touch handset/tablet without altering Android TV or D-pad behaviour. */
public final class MobileUi {
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

    /** Width based on the CURRENT orientation (never the longer display dimension). */
    public static int columns(Context context, int cardWidthResId) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float widthDp = metrics.widthPixels / metrics.density;
        // Account for the TV row's own insets so two tiles fit fully inside a phone.
        // Wider tablets use more columns without stretching thumbnails to TV sizes.
        float minimum = widthDp >= 600f ? 158f
                : (cardWidthResId == R.dimen.shorts_card_width ? 114f : 124f);
        int columns = (int) ((widthDp - 60f + 12f) / (minimum + 12f));
        return Math.max(1, Math.min(6, columns));
    }

    public static android.util.Pair<Integer, Integer> cardSize(Context context, int widthResId, int heightResId) {
        int columns = columns(context, widthResId);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int gap = dp(context, 12);
        int horizontalInsets = dp(context, 60);
        int width = Math.max(dp(context, 104),
                (screenWidth - horizontalInsets - gap * (columns - 1)) / columns);
        boolean shorts = widthResId == R.dimen.shorts_card_width;
        float ratio = shorts ? (16f / 9f) :
                ((float) context.getResources().getDimensionPixelSize(heightResId)
                 / Math.max(1, context.getResources().getDimensionPixelSize(widthResId)));
        return new android.util.Pair<>(width, Math.round(width * ratio));
    }
}
