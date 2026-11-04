package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.widget.VerticalGridView;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;

/**
 * Phone-only overlay for the ORIGINAL Leanback section list.
 *
 * The TV transition moves the main content sideways when the section list is
 * shown. On phones this squeezes the full-width video feed into a thin strip.
 * Here the existing header fragment stays attached to its original dock,
 * but its dock and a dismissible scrim overlay the content instead.
 *
 * No duplicate menu, duplicate section adapter or replacement list is created.
 * Android TV does not instantiate this class.
 */
final class MobileSectionsDrawer {
    private final BrowseFragment fragment;
    private final View dock;
    private final View scrim;
    private boolean open;

    MobileSectionsDrawer(BrowseFragment owner, View root) {
        fragment = owner;
        View frameView = root.findViewById(R.id.browse_frame);
        View existingDock = root.findViewById(R.id.browse_headers_dock);
        if (!(frameView instanceof FrameLayout) || existingDock == null) {
            throw new IllegalStateException("Original Leanback sections view missing");
        }
        FrameLayout frame = (FrameLayout) frameView;
        dock = existingDock;
        scrim = new View(root.getContext());
        scrim.setBackgroundColor(0x80000000);
        scrim.setContentDescription("Close sections menu");
        scrim.setOnClickListener(v -> hide());
        int dockIndex = frame.indexOfChild(dock);
        frame.addView(scrim, Math.max(0, dockIndex),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setVisibility(View.GONE);
        dock.setVisibility(View.GONE);
        // Prevent Leanback's old "tap gutter" handler from triggering a
        // push-aside transition on touchscreen devices.
        dock.setOnClickListener(v -> {});
        dock.setBackgroundColor(Color.WHITE);
        dock.setPadding(0, 0, 0, 0);
        resize();
    }

    boolean isOpen() {
        return open;
    }

    void toggle() {
        if (open) hide();
        else show();
    }

    void show() {
        if (!fragment.isAdded()) return;
        open = true;
        resize();
        scrim.setVisibility(View.VISIBLE);
        scrim.bringToFront();
        dock.setVisibility(View.VISIBLE);
        dock.bringToFront();
        dock.setElevation(MobileUi.dp(fragment.requireContext(), 12));
        HeadersSupportFragment headers = fragment.getHeadersSupportFragment();
        if (headers != null) {
            if (headers.getView() != null) headers.getView().setBackgroundColor(Color.WHITE);
            Helpers.setField(headers, "mHeadersEnabled", true);
            View actualList = headers.getView();
            if (actualList != null) {
                ViewGroup.LayoutParams layout = actualList.getLayoutParams();
                if (layout != null) layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
                // Leanback hid this view by applying a negative start margin.
                // Reset it whenever our drawer opens, without moving the feed.
                if (layout instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams margin = (ViewGroup.MarginLayoutParams) layout;
                    if (margin.getMarginStart() != 0) {
                        margin.setMarginStart(0);
                        actualList.setLayoutParams(margin);
                    }
                }
                if (layout != null) actualList.setLayoutParams(layout);
                actualList.setVisibility(View.VISIBLE);
                View fade = actualList.findViewById(R.id.fade_out_edge);
                if (fade != null) fade.setVisibility(View.GONE);
            }
            VerticalGridView grid = headers.getVerticalGridView();
            if (grid != null) {
                grid.setBackgroundColor(Color.WHITE);
                grid.setChildrenVisibility(View.VISIBLE);
                ViewGroup.LayoutParams gridLayout = grid.getLayoutParams();
                if (gridLayout != null) {
                    gridLayout.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    grid.setLayoutParams(gridLayout);
                }
                int pad = MobileUi.dp(fragment.requireContext(), 8);
                grid.setPadding(pad, pad, pad, pad);
                grid.setClipToPadding(false);
            }
        }
    }

    void hide() {
        open = false;
        scrim.setVisibility(View.GONE);
        dock.setVisibility(View.GONE);
        HeadersSupportFragment headers = fragment.getHeadersSupportFragment();
        if (headers != null) {
            Helpers.setField(headers, "mHeadersEnabled", false);
            VerticalGridView grid = headers.getVerticalGridView();
            if (grid != null) grid.setChildrenVisibility(View.INVISIBLE);
        }
    }

    void resize() {
        if (!fragment.isAdded()) return;
        float windowDp = MobileUi.windowWidthDp(fragment.requireContext());
        // Leave a visible dismissible edge, even in split-screen windows.
        float widthDp = Math.min(320f, Math.max(1f, windowDp * 0.82f));
        widthDp = Math.min(widthDp, Math.max(1f, windowDp - 48f));
        int widthPx = MobileUi.dp(fragment.requireContext(), widthDp);
        ViewGroup.LayoutParams layout = dock.getLayoutParams();
        if (layout != null && layout.width != widthPx) {
            layout.width = widthPx;
            dock.setLayoutParams(layout);
        }
    }
}
