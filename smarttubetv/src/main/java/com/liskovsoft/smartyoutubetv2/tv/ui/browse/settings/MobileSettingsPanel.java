package com.liskovsoft.smartyoutubetv2.tv.ui.browse.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import java.util.ArrayList;
import java.util.List;

/** Touch-first settings categories. Reuses every original SettingsItem.onClick
 * instead of inventing a second settings or preferences backend.
 *
 * One column for phone-sized windows, two for tablets, three for large windows.
 * Calculates actual window width on layout, including foldables, multi-window
 * and portrait/landscape rotations. Android TV never instantiates this view.
 */
final class MobileSettingsPanel extends RecyclerView {
    interface OnOpen { void open(SettingsItem item); }
    private final GridLayoutManager grid;
    private final CategoryAdapter adapter = new CategoryAdapter();
    private final OnOpen onOpen;
    private int lastWidth = -1;

    MobileSettingsPanel(Context context, OnOpen onOpen) {
        super(context);
        this.onOpen = onOpen;
        grid = new GridLayoutManager(context, 1);
        setLayoutManager(grid);
        setAdapter(adapter);
        setItemAnimator(null);
        setBackgroundColor(Color.WHITE);
        setClipToPadding(false);
        setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        setPadding(dp(12), dp(12), dp(12), dp(16));
        addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            if (width != lastWidth) {
                lastWidth = width;
                int columns = MobileUi.settingsColumnsForWidthDp(
                        width / Math.max(0.1f, getResources().getDisplayMetrics().density));
                if (grid.getSpanCount() != columns) grid.setSpanCount(columns);
            }
        });
    }

    int dp(float size) { return MobileUi.dp(getContext(), size); }

    void setItems(List<SettingsItem> items) {
        adapter.items.clear();
        if (items != null) adapter.items.addAll(items);
        adapter.notifyDataSetChanged();
    }

    private GradientDrawable background() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFFF5F5F5);
        shape.setCornerRadius(dp(14));
        return shape;
    }

    private final class CategoryAdapter extends RecyclerView.Adapter<CategoryHolder> {
        final ArrayList<SettingsItem> items = new ArrayList<>();

        @NonNull @Override
        public CategoryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(getContext());
            row.setBackground(background());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(14), dp(9), dp(13), dp(9));
            row.setMinimumHeight(dp(68));
            GridLayoutManager.LayoutParams lp = new GridLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            row.setLayoutParams(lp);
            row.setClickable(true);
            row.setFocusable(true);

            ImageView icon = new ImageView(getContext());
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setColorFilter(0xFF272727);
            row.addView(icon, new LinearLayout.LayoutParams(dp(33), dp(33)));

            TextView title = new TextView(getContext());
            title.setTextColor(0xFF171717);
            title.setTextSize(15);
            title.setTypeface(Typeface.DEFAULT, Typeface.MEDIUM);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleParams.setMargins(dp(14), 0, dp(6), 0);
            row.addView(title, titleParams);
            TextView chevron = new TextView(getContext());
            chevron.setText("\u203A");
            chevron.setTextColor(0xFF666666);
            chevron.setTextSize(26);
            chevron.setGravity(Gravity.CENTER);
            row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(44)));
            return new CategoryHolder(row, icon, title);
        }

        @Override public void onBindViewHolder(@NonNull CategoryHolder holder, int position) {
            SettingsItem item = items.get(position);
            holder.title.setText(item.title);
            if (item.imageResId > 0) {
                holder.icon.setImageDrawable(ContextCompat.getDrawable(getContext(), item.imageResId));
                holder.icon.setVisibility(View.VISIBLE);
            } else holder.icon.setVisibility(View.INVISIBLE);
            holder.itemView.setOnClickListener(v -> onOpen.open(item));
        }

        @Override public int getItemCount() { return items.size(); }
    }

    private static final class CategoryHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        CategoryHolder(View root, ImageView icon, TextView title) {
            super(root);
            this.icon = icon;
            this.title = title;
        }
    }
}
