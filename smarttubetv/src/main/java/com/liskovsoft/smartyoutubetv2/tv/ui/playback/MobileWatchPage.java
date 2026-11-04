package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.ObjectAdapter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileVideoFeed;

/**
 * Mobile watch layout. The ORIGINAL PlaybackFragment controls are the only
 * player transport. No second overlay, buttons, progress bar or timer.
 */
final class MobileWatchPage {
    private final PlaybackActivity activity;
    private final PlaybackFragment player;
    private final LinearLayout screen;
    private final FrameLayout videoFrame;
    private final FrameLayout below;
    private final TextView title, metadata, author;
    private final MobileVideoFeed feed;

    MobileWatchPage(PlaybackActivity activity, PlaybackFragment player) {
        this.activity = activity;
        this.player = player;
        screen = activity.findViewById(R.id.mobile_watch_root);
        videoFrame = activity.findViewById(R.id.mobile_player_frame);
        below = activity.findViewById(R.id.mobile_watch_below);
        if (screen == null || videoFrame == null || below == null)
            throw new IllegalStateException("Missing mobile watch containers");

        // Do not add any view on top of the playing video. The original
        // SmartTube playback controls receive native touch gestures directly.
        LinearLayout description = new LinearLayout(activity);
        description.setOrientation(LinearLayout.VERTICAL);
        description.setPadding(dp(14), dp(12), dp(14), dp(8));
        below.addView(description, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        title = new TextView(activity);
        title.setTextColor(0xFF171717);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setText("Loading video...");
        title.setOnClickListener(v -> {
            title.setMaxLines(title.getMaxLines() <= 2 ? 8 : 2);
        });
        description.addView(title);

        metadata = new TextView(activity);
        metadata.setTextColor(0xFF777777);
        metadata.setTextSize(12);
        metadata.setMaxLines(2);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(6);
        description.addView(metadata, metaParams);

        LinearLayout channelRow = new LinearLayout(activity);
        channelRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        channelParams.topMargin = dp(8);
        description.addView(channelRow, channelParams);

        TextView avatar = new TextView(activity);
        avatar.setText("S");
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(18);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(0xFF385C7D, 30));
        channelRow.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));

        author = new TextView(activity);
        author.setTextColor(0xFF151515);
        author.setTextSize(15);
        author.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        author.setMaxLines(1);
        author.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        authorParams.setMargins(dp(10), 0, dp(6), 0);
        channelRow.addView(author, authorParams);
        avatar.setOnClickListener(v -> player.mobileAction(R.id.action_channel));
        author.setOnClickListener(v -> player.mobileAction(R.id.action_channel));

        TextView subscribe = pill("Subscribe", 0xFF171717, Color.WHITE);
        channelRow.addView(subscribe, new LinearLayout.LayoutParams(dp(105), dp(39)));
        subscribe.setOnClickListener(v -> player.mobileAction(R.id.action_subscribe));

        HorizontalScrollView actionsScroll = new HorizontalScrollView(activity);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, 0, dp(8), 0);
        actionsScroll.addView(actions);
        addPill(actions, "Like", () -> player.mobileAction(R.id.action_thumbs_up));
        addPill(actions, "Dislike", () -> player.mobileAction(R.id.action_thumbs_down));
        addPill(actions, "Share", () -> player.mobileAction(R.id.action_share));
        addPill(actions, "Save", () -> player.mobileAction(R.id.action_playlist_add));
        addPill(actions, "Captions", () -> player.mobileAction(R.id.lb_control_closed_captioning));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        actionParams.topMargin = dp(8);
        description.addView(actionsScroll, actionParams);

        TextView nextHeader = new TextView(activity);
        nextHeader.setText("Up next");
        nextHeader.setTextColor(0xFF171717);
        nextHeader.setTextSize(17);
        nextHeader.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nextHeader.setPadding(0, dp(12), 0, dp(9));
        description.addView(nextHeader);

        feed = new MobileVideoFeed(activity, player::openMobileSuggestion, false);
        feed.setOnVideoVisible(player::onMobileSuggestionVisible);
        description.addView(feed.getView(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        videoFrame.post(this::applyLayout);
        onVideoChanged(player.getVideo());
    }

    private int dp(float value) { return MobileUi.dp(activity, value); }
    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private TextView pill(String label, int bg, int ink) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextColor(ink);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(bg, 22));
        view.setContentDescription(label);
        return view;
    }
    private void addPill(LinearLayout row, String text, Runnable callback) {
        TextView pill = pill(text, 0xFFF1F1F1, 0xFF171717);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(92), dp(37));
        params.setMarginEnd(dp(8));
        row.addView(pill, params);
        pill.setOnClickListener(v -> callback.run());
    }
    void onVideoChanged(Video video) {
        if (video == null) return;
        title.setText(video.getTitleFull() != null ? video.getTitleFull() : video.getTitle());
        CharSequence second = video.getSecondTitleFull();
        metadata.setText(second == null ? "" : second);
        String name = video.getAuthor();
        author.setText(name != null ? name : "Channel");
    }
    void onSuggestions(ObjectAdapter rows) {
        feed.scheduleRows(rows);
    }
    void applyLayout() {
        if (videoFrame.getParent() == null) return;
        boolean landscape = activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) videoFrame.getLayoutParams();
        if (landscape) {
            params.height = 0;
            params.weight = 1f;
            below.setVisibility(View.GONE);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            int width = screen.getWidth() > 0 ? screen.getWidth()
                    : activity.getResources().getDisplayMetrics().widthPixels;
            params.height = Math.round(width * 9f / 16f);
            params.weight = 0;
            below.setVisibility(View.VISIBLE);
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            activity.getWindow().setStatusBarColor(Color.BLACK);
            activity.getWindow().setNavigationBarColor(Color.WHITE);
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        videoFrame.setLayoutParams(params);
    }
    private void toggleFullscreen() {
        boolean landscape = activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        activity.setRequestedOrientation(landscape
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    // All player-control lifecycle operations belong to the native fragment.
    void resume() {}
    void stop() {}
    void setControlsShown(boolean visible) { player.showOverlay(visible); }
}
