package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ObjectAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** A virtualised, vertically scrolling mobile video feed. The source is still
 * the existing Leanback adapters, so search/history/subscriptions keep their
 * original data, click actions and pagination semantics.
 */
public final class MobileVideoFeed {
    public interface OnVideoClick { void onVideo(Video video); }
    public interface OnVideoVisible { void onVideoVisible(Video video); }
    private static final int INK = Color.rgb(20, 20, 20);
    private static final int SUBTEXT = Color.rgb(104, 104, 104);
    private static final int BACKGROUND = Color.WHITE;
    private final Context context;
    private final LinearLayout root;
    private final RecyclerView list;
    private final GridLayoutManager gridLayoutManager;
    private final LinearLayout chipsRow;
    private final HorizontalScrollView chipsScroll;
    private final FeedAdapter adapter = new FeedAdapter();
    private final ArrayList<FeedItem> allVideos = new ArrayList<>();
    private final OnVideoClick onVideoClick;
    private OnVideoVisible onVideoVisible;
    private final boolean showChips;
    private String filter = "All";
    private boolean refreshQueued;

    private static final class FeedItem {
        Video video;
        String category;
        FeedItem(Video item, String category) {
            this.video = item;
            this.category = category;
        }
    }

    public MobileVideoFeed(Context context, OnVideoClick onClick, boolean categories) {
        this.context = context;
        this.onVideoClick = onClick;
        this.showChips = categories;
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setFocusableInTouchMode(true);

        chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setFillViewport(false);
        chipsScroll.setBackgroundColor(BACKGROUND);
        chipsRow = new LinearLayout(context);
        chipsRow.setPadding(dp(12), dp(6), dp(12), dp(8));
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsScroll.addView(chipsRow);
        if (categories) root.addView(chipsScroll,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        list = new RecyclerView(context);
        gridLayoutManager = new GridLayoutManager(context, 1);
        list.setLayoutManager(gridLayoutManager);
        list.setAdapter(adapter);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        list.setItemAnimator(null);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (onVideoVisible == null) return;
                int position = ((LinearLayoutManager)view.getLayoutManager()).findLastVisibleItemPosition();
                if (position >= 0 && position < adapter.displayed.size()) {
                    onVideoVisible.onVideoVisible(adapter.displayed.get(position).video);
                }
            }
        }); // Updating thumbnails shouldn't animate the entire screen.
        list.setClipToPadding(false);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        // One full-width thumbnail on portrait phones, two on wide phones and
        // tablets, three on larger tablets. Reflow when the window is resized,
        // not just when the device physically rotates.
        root.addOnLayoutChangeListener((v, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft)) updateColumns(right - left);
        });
    }

    private void updateColumns(int widthPx) {
        float widthDp = widthPx / Math.max(0.1f,
                context.getResources().getDisplayMetrics().density);
        int span = MobileUi.feedColumnsForWidthDp(widthDp);
        if (gridLayoutManager.getSpanCount() != span) {
            gridLayoutManager.setSpanCount(span);
            adapter.notifyDataSetChanged();
        }
    }

    private int dp(float value) { return MobileUi.dp(context, value); }
    public View getView() { return root; }
    public void setOnVideoVisible(OnVideoVisible callback) { onVideoVisible = callback; }

    public void setBottomInsetDp(int bottomDp) {
        list.setPadding(0, 0, 0, dp(bottomDp));
    }

    /** Debounce multiple pages received in the same UI frame. */
    public void scheduleRows(ObjectAdapter rows) {
        if (refreshQueued) return;
        refreshQueued = true;
        root.postDelayed(() -> {
            refreshQueued = false;
            setRows(rows);
        }, 80);
    }

    public void setRows(ObjectAdapter rows) {
        if (rows == null) return;
        ArrayList<FeedItem> next = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            Object value = rows.get(r);
            if (!(value instanceof ListRow)) continue;
            ListRow row = (ListRow) value;
            String category = row.getHeaderItem() != null ? row.getHeaderItem().getName() : "";
            ObjectAdapter videos = row.getAdapter();
            if (videos == null) continue;
            for (int v = 0; v < videos.size(); v++) {
                Object item = videos.get(v);
                if (item instanceof Video) next.add(new FeedItem((Video) item, category));
            }
        }
        setItems(next);
    }

    public void setVideos(ObjectAdapter videos, String category) {
        ArrayList<FeedItem> next = new ArrayList<>();
        if (videos != null) {
            for (int i = 0; i < videos.size(); i++) {
                Object item = videos.get(i);
                if (item instanceof Video) next.add(new FeedItem((Video)item, category));
            }
        }
        setItems(next);
    }

    private void setItems(List<FeedItem> next) {
        String previousTopId = null;
        int previousTopOffset = 0;
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        int firstPosition = manager.findFirstVisibleItemPosition();
        if (firstPosition >= 0 && firstPosition < adapter.displayed.size()) {
            Video top = adapter.displayed.get(firstPosition).video;
            previousTopId = top.videoId;
            View topView = manager.findViewByPosition(firstPosition);
            previousTopOffset = topView != null ? topView.getTop() : 0;
        }
        allVideos.clear();
        allVideos.addAll(next);
        rebuildChips();
        applyFilter();
        // Keep the user's scroll position during background pagination.
        if (previousTopId != null) {
            for (int i = 0; i < adapter.displayed.size(); i++) {
                if (TextUtils.equals(previousTopId, adapter.displayed.get(i).video.videoId)) {
                    manager.scrollToPositionWithOffset(i, previousTopOffset);
                    break;
                }
            }
        }
    }

    private void rebuildChips() {
        if (!showChips) return;
        Set<String> categories = new LinkedHashSet<>();
        categories.add("All");
        for (FeedItem item : allVideos) {
            if (!TextUtils.isEmpty(item.category)) categories.add(item.category);
            if (categories.size() >= 12) break;
        }
        if (!categories.contains(filter)) filter = "All";
        chipsRow.removeAllViews();
        for (String category : categories) {
            TextView chip = new TextView(context);
            chip.setText(category);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setMaxWidth(dp(168));
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), 0, dp(16), 0);
            boolean selected = category.equals(filter);
            chip.setTextColor(selected ? Color.WHITE : INK);
            chip.setBackground(rounded(selected ? INK : 0xFFF1F1F1, 11));
            chip.setOnClickListener(v -> {
                filter = category;
                rebuildChips();
                applyFilter();
                list.scrollToPosition(0);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            params.setMarginEnd(dp(8));
            chipsRow.addView(chip, params);
        }
    }

    private void applyFilter() {
        adapter.displayed.clear();
        for (FeedItem item : allVideos) {
            if (filter.equals("All") || filter.equals(item.category)) adapter.displayed.add(item);
        }
        adapter.notifyDataSetChanged();
    }

    private GradientDrawable rounded(int fill, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private final class FeedAdapter extends RecyclerView.Adapter<VideoHolder> {
        private final ArrayList<FeedItem> displayed = new ArrayList<>();
        @NonNull @Override
        public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.setBackgroundColor(BACKGROUND);
            card.setPadding(0, 0, 0, dp(12));

            AspectFrame imageFrame = new AspectFrame(context);
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageFrame.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            TextView duration = new TextView(context);
            duration.setTextColor(Color.WHITE);
            duration.setTextSize(11);
            duration.setPadding(dp(5), dp(2), dp(5), dp(2));
            duration.setBackground(rounded(0xDB151515, 4));
            FrameLayout.LayoutParams durationParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            durationParams.setMargins(0, 0, dp(8), dp(8));
            imageFrame.addView(duration, durationParams);
            card.addView(imageFrame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout details = new LinearLayout(context);
            details.setPadding(dp(12), dp(12), dp(8), dp(12));
            details.setGravity(Gravity.TOP | Gravity.START);
            TextView avatar = new TextView(context);
            avatar.setGravity(Gravity.CENTER);
            avatar.setTextColor(Color.WHITE);
            avatar.setTextSize(16);
            avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            avatar.setBackground(rounded(0xFF243C72, 28));
            details.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelsParams.setMargins(dp(11), 0, dp(5), 0);
            details.addView(labels, labelsParams);
            TextView title = new TextView(context);
            title.setTextSize(16);
            title.setTextColor(INK);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            labels.addView(title);
            TextView metadata = new TextView(context);
            metadata.setTextSize(12);
            metadata.setTextColor(SUBTEXT);
            metadata.setMaxLines(2);
            metadata.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subtitleParams.topMargin = dp(5);
            labels.addView(metadata, subtitleParams);

            TextView more = new TextView(context);
            more.setText("\u22EE");
            more.setTextSize(26);
            more.setTextColor(INK);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("More options for video");
            details.addView(more, new LinearLayout.LayoutParams(dp(42), dp(48)));
            card.addView(details);

            return new VideoHolder(card, image, duration, title, metadata, avatar, more);
        }

        @Override public void onBindViewHolder(@NonNull VideoHolder h, int index) {
            Video video = displayed.get(index).video;
            h.title.setText(video.getTitle());
            CharSequence second = video.getSecondTitle();
            h.metadata.setText(second != null ? second : video.getAuthor());
            String author = video.getAuthor();
            String initial = author == null || author.length() == 0 ? "S" :
                    author.substring(0, 1).toUpperCase(Locale.getDefault());
            h.avatar.setText(initial);
            h.duration.setText(video.isLive ? "LIVE" : durationText(video.getDurationMs()));
            h.duration.setVisibility(video.isLive || video.getDurationMs() > 0 ? View.VISIBLE : View.GONE);
            Glide.with(h.image)
                    .load(video.getCardImageUrl())
                    .centerCrop()
                    .into(h.image);
            h.itemView.setOnClickListener(v -> {
                if (onVideoClick != null) onVideoClick.onVideo(video);
            });
            h.more.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(context, h.more);
                menu.getMenu().add("Play").setOnMenuItemClickListener(item -> {
                    if (onVideoClick != null) onVideoClick.onVideo(video);
                    return true;
                });
                if (!TextUtils.isEmpty(video.videoId)) {
                    menu.getMenu().add("Copy video link").setOnMenuItemClickListener(item -> {
                        ClipboardManager clipboard = (ClipboardManager)
                                context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(
                                "Video", "https://www.youtube.com/watch?v=" + video.videoId));
                        return true;
                    });
                }
                menu.show();
            });
        }

        @Override public void onViewRecycled(@NonNull VideoHolder holder) {
            Glide.with(holder.image).clear(holder.image);
            super.onViewRecycled(holder);
        }

        @Override public int getItemCount() { return displayed.size(); }
    }

    private String durationText(long ms) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(Math.max(0, ms));
        if (seconds >= 3600) return String.format(Locale.US, "%d:%02d:%02d",
                seconds / 3600, seconds / 60 % 60, seconds % 60);
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static final class VideoHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView duration, title, metadata, avatar, more;
        VideoHolder(View root, ImageView image, TextView duration, TextView title,
                    TextView metadata, TextView avatar, TextView more) {
            super(root);
            this.image=image;
            this.duration=duration;
            this.title=title;
            this.metadata=metadata;
            this.avatar=avatar;
            this.more=more;
        }
    }

    private static final class AspectFrame extends FrameLayout {
        AspectFrame(Context context) { super(context); }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = View.MeasureSpec.getSize(widthMeasureSpec);
            int height = Math.round(width * 9f / 16f);
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        }
    }
}
