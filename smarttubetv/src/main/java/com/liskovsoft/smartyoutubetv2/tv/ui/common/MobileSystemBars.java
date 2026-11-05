package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Enforces status/cutout/nav bar safe areas on touch devices only. Android TV
 * retains its original immersive full-screen presentation.
 */
public final class MobileSystemBars {
    private MobileSystemBars() {}

    public static void install(Activity activity, boolean lightStatusIcons) {
        if (!MobileUi.isHandheld(activity)) return;
        ensure(activity, lightStatusIcons);
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        final int left = content.getPaddingLeft();
        final int top = content.getPaddingTop();
        final int right = content.getPaddingRight();
        final int bottom = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            applyPadding(activity, view, insets, left, top, right, bottom);
            return insets;
        });
        content.post(() -> applyPadding(activity, content,
                activity.getWindow().getDecorView().getRootWindowInsets(),
                left, top, right, bottom));
        content.requestApplyInsets();
    }

    public static void ensure(Activity activity, boolean lightStatusIcons) {
        if (!MobileUi.isHandheld(activity)) return;
        // Explicit landscape-video fullscreen remains a separate user mode.
        if (!lightStatusIcons && activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) return;
        if (Build.VERSION.SDK_INT >= 26 && activity.isInPictureInPictureMode()) return;
        Window window = activity.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS |
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(lightStatusIcons ? Color.WHITE : Color.BLACK);
        window.setNavigationBarColor(Color.WHITE);
        View decor = window.getDecorView();
        int immersiveMask = View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        int visibleAppearance = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR |
                (lightStatusIcons ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
        decor.setSystemUiVisibility((decor.getSystemUiVisibility() & ~immersiveMask)
                | visibleAppearance);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);
            WindowInsetsController bars = window.getInsetsController();
            if (bars != null) {
                bars.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                bars.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS |
                        (lightStatusIcons ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0),
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        }
        View content = activity.findViewById(android.R.id.content);
        if (content != null) content.requestApplyInsets();
    }

    /** Only add the gap not already provided by the operating system. */
    static int[] correction(int contentScreenTop, int contentHeight,
            int safeScreenTop, int safeScreenBottom) {
        return new int[]{
                Math.max(0, safeScreenTop - contentScreenTop),
                Math.max(0, contentScreenTop + Math.max(0, contentHeight) - safeScreenBottom)
        };
    }

    private static void applyPadding(Activity activity, View content, WindowInsets insets,
            int originalLeft, int originalTop, int originalRight, int originalBottom) {
        if (insets == null || content.getHeight() <= 0) return;
        int safeTop, safeBottom;
        if (Build.VERSION.SDK_INT >= 30) {
            safeTop = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
            safeBottom = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars()).bottom;
        } else {
            safeTop = insets.getStableInsetTop();
            safeBottom = insets.getStableInsetBottom();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null)
                safeTop = Math.max(safeTop, insets.getDisplayCutout().getSafeInsetTop());
        }
        Point display = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(display);
        int[] position = new int[2];
        content.getLocationOnScreen(position);
        int[] extra = correction(position[1], content.getHeight(),
                safeTop, display.y - safeBottom);
        int top = originalTop + extra[0];
        int bottom = originalBottom + extra[1];
        if (content.getPaddingLeft() != originalLeft ||
                content.getPaddingTop() != top ||
                content.getPaddingRight() != originalRight ||
                content.getPaddingBottom() != bottom) {
            content.setPadding(originalLeft, top, originalRight, bottom);
        }
    }
}
