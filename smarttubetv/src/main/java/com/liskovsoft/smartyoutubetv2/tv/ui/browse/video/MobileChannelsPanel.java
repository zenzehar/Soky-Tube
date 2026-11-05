package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.leanback.widget.ObjectAdapter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileVideoFeed;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Handheld channel picker: never inherits the TV two-column grid widths.
 * The original Leanback adapters remain the authoritative channel/video data.
 */
final class MobileChannelsPanel extends LinearLayout {
    private final ArrayList<Video> channels = new ArrayList<>();
    private final LinearLayout chips;
    private final TextView selected;
    private final MobileVideoFeed feed;
    private final Consumer<Video> onChannel;
    private String selectedId;

    MobileChannelsPanel(Context context, Consumer<Video> onChannel,
            Consumer<Video> onVideo, Runnable onRefresh) {
        super(context);
        this.onChannel = onChannel;
        setOrientation(VERTICAL);
        setBackgroundColor(Color.WHITE);
        setPadding(0, 0, 0, 0);
        LinearLayout heading = new LinearLayout(context);
        heading.setPadding(dp(16), dp(12), dp(12), dp(8));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        selected = text("Channels", 17, Color.BLACK);
        selected.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        selected.setSingleLine(true);
        selected.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(selected, new LayoutParams(0, dp(46), 1f));
        selected.setGravity(Gravity.CENTER_VERTICAL);
        TextView all = text("Browse all  ›", 14, 0xFF333333);
        all.setPadding(dp(10), 0, dp(10), 0);
        all.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(all);
        all.setOnClickListener(v -> showAllChannels());
        addView(heading);
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(context);
        chips.setPadding(dp(12), 0, dp(12), dp(8));
        chips.setOrientation(HORIZONTAL);
        scroller.addView(chips);
        addView(scroller, new LayoutParams(LayoutParams.MATCH_PARENT, dp(53)));
        feed = new MobileVideoFeed(context, onVideo::accept, false);
        feed.setOnRefresh(onRefresh);
        addView(feed.getView(), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private int dp(float v) { return MobileUi.dp(getContext(), v); }
    private TextView text(String text, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }
    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(23));
        return d;
    }
    void showChannels(ObjectAdapter adapter) {
        channels.clear();
        if (adapter != null) for (int i = 0; i < adapter.size(); i++) {
            Object item = adapter.get(i);
            if (item instanceof Video) channels.add((Video) item);
        }
        renderChips();
    }
    void showVideos(ObjectAdapter adapter) { feed.setVideos(adapter, ""); }

    private String channelName(Video video) {
        CharSequence title = video.getTitleFull();
        return title != null ? title.toString() :
                video.getTitle() != null ? video.getTitle() : "Channel";
    }
    private void choose(Video channel) {
        if (channel == null) return;
        selectedId = channel.channelId != null ? channel.channelId : channel.videoId;
        selected.setText(channelName(channel));
        renderChips();
        onChannel.accept(channel);
    }
    private void renderChips() {
        chips.removeAllViews();
        for (int i = 0; i < Math.min(18, channels.size()); i++) {
            Video channel = channels.get(i);
            boolean active = TextUtils.equals(selectedId,
                    channel.channelId != null ? channel.channelId : channel.videoId);
            TextView pill = text(channelName(channel), 13, active ? Color.WHITE : 0xFF222222);
            pill.setMaxWidth(dp(170));
            pill.setSingleLine(true);
            pill.setEllipsize(TextUtils.TruncateAt.END);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(15), 0, dp(15), 0);
            pill.setBackground(rounded(active ? 0xFF161616 : 0xFFF1F1F1));
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(42));
            lp.setMarginEnd(dp(8));
            chips.addView(pill, lp);
            pill.setOnClickListener(v -> choose(channel));
        }
        if (channels.size() > 18) {
            TextView more = text("All channels…", 13, 0xFF161616);
            more.setGravity(Gravity.CENTER);
            more.setPadding(dp(14), 0, dp(14), 0);
            more.setBackground(rounded(0xFFE6E6E6));
            chips.addView(more, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(42)));
            more.setOnClickListener(v -> showAllChannels());
        }
    }
    private void showAllChannels() {
        if (channels.isEmpty()) return;
        final ArrayList<Video> matches = new ArrayList<>(channels);
        final ArrayList<String> names = new ArrayList<>();
        for (Video channel : matches) names.add(channelName(channel));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_list_item_1, new ArrayList<>(names));
        EditText filter = new EditText(getContext());
        filter.setSingleLine(true);
        filter.setHint("Search channels");
        filter.setPadding(dp(18), dp(10), dp(18), dp(10));
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                matches.clear();
                names.clear();
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                for (Video channel : channels) {
                    String name = channelName(channel);
                    if (name.toLowerCase(Locale.ROOT).contains(query)) {
                        matches.add(channel);
                        names.add(name);
                    }
                }
                adapter.clear();
                adapter.addAll(names);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        new AlertDialog.Builder(getContext()).setTitle("Select a channel")
                .setView(filter)
                .setAdapter(adapter, (dialog, which) -> {
                    if (which >= 0 && which < matches.size()) choose(matches.get(which));
                }).setNegativeButton("Close", null).show();
    }
}
