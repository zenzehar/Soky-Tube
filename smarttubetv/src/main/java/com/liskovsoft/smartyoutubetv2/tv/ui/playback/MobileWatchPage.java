package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.leanback.widget.ObjectAdapter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileVideoFeed;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Single native touch transport over the same ExoPlayer engine.
 * The original Leanback transport is hidden ONLY on mobile. Video playback,
 * channel actions, playback settings and queue remain backed by the original presenter.
 */
final class MobileWatchPage {
    private final PlaybackActivity activity;
    private final PlaybackFragment player;
    private final LinearLayout screen;
    private final FrameLayout videoFrame;
    private final FrameLayout below;
    private final FrameLayout shade;
    private final LinearLayout controls;
    private final TextView title, metadata, author, time;
    private final ImageButton toggle, expand;
    private final SeekBar seek;
    private final MobileVideoFeed feed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean tracking;
    private boolean controlsShown = true;
    private boolean stopped;

    private final Runnable autoHide = () -> setControlsShown(false);
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            updatePlayback();
            handler.postDelayed(this, 450);
        }
    };

    MobileWatchPage(PlaybackActivity activity, PlaybackFragment player) {
        this.activity = activity;
        this.player = player;
        screen = activity.findViewById(R.id.mobile_watch_root);
        videoFrame = activity.findViewById(R.id.mobile_player_frame);
        below = activity.findViewById(R.id.mobile_watch_below);
        if (screen == null || videoFrame == null || below == null)
            throw new IllegalStateException("Mobile watch layout not found");

        shade = new FrameLayout(activity);
        shade.setBackgroundColor(0x55000000);
        shade.setClickable(true);
        shade.setContentDescription("Tap to show or hide playback controls");
        shade.setOnClickListener(v -> setControlsShown(!controlsShown));
        // Keep the legacy mobile overlay detached. Only the original
        // PlaybackFragment/Leanback control menu is visible and touch-active.
        // The portrait watch page below remains intact.

        controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        shade.addView(controls, overlayParams);

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), dp(1), dp(4), 0);
        controls.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        addIcon(top, R.drawable.mobile_ic_back, "Back", () -> activity.onBackPressed());
        View spacer = new View(activity);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        addIcon(top, R.drawable.mobile_ic_more, "Playback settings", this::openSettings);

        LinearLayout center = new LinearLayout(activity);
        center.setGravity(Gravity.CENTER);
        addIcon(center, R.drawable.mobile_ic_prev, "Previous video", player::skipToPrevious);
        toggle = icon(R.drawable.mobile_ic_pause, "Pause");
        toggle.setOnClickListener(v -> {
            player.setPlayWhenReady(!player.getPlayWhenReady());
            updatePlayback();
            postponeHide();
        });
        center.addView(toggle, new LinearLayout.LayoutParams(dp(64), dp(64)));
        addIcon(center, R.drawable.mobile_ic_next, "Next video", player::skipToNext);
        controls.addView(center, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout lower = new LinearLayout(activity);
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setPadding(dp(10), 0, dp(10), dp(2));
        controls.addView(lower, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        time = new TextView(activity);
        time.setTextSize(12);
        time.setTextColor(Color.WHITE);
        time.setText("0:00 / --:--");
        labels.addView(time, new LinearLayout.LayoutParams(0, dp(25), 1f));
        expand = icon(R.drawable.mobile_ic_fullscreen, "Toggle fullscreen");
        expand.setOnClickListener(v -> toggleFullscreen());
        labels.addView(expand, new LinearLayout.LayoutParams(dp(48), dp(36)));
        lower.addView(labels);

        seek = new SeekBar(activity);
        seek.setMax(1000);
        seek.setContentDescription("Video progress and seeking");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {
                tracking = true;
                handler.removeCallbacks(autoHide);
            }
            @Override public void onProgressChanged(SeekBar bar, int position, boolean user) {
                if (user) {
                    long duration = duration();
                    time.setText(duration > 0
                            ? format(Math.round(duration * position / 1000d)) + " / " + format(duration)
                            : "LIVE");
                }
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                long duration = duration();
                if (duration > 0) player.setPositionMs(Math.round(duration * bar.getProgress() / 1000d));
                tracking = false;
                postponeHide();
            }
        });
        lower.addView(seek, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));

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
        resume();
        postponeHide();
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
    private ImageButton icon(int drawable, String description) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(drawable);
        button.setColorFilter(Color.WHITE);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(description);
        return button;
    }
    private void addIcon(LinearLayout row, int image, String name, Runnable callback) {
        ImageButton button = icon(image, name);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(56), dp(52));
        row.addView(button, params);
        button.setOnClickListener(v -> { callback.run(); postponeHide(); });
    }

    private void openSettings() {
        ImageButton button = (ImageButton) ((LinearLayout)controls.getChildAt(0)).getChildAt(2);
        PopupMenu menu = new PopupMenu(activity, button);
        menu.getMenu().add("Quality").setOnMenuItemClickListener(i -> action(R.id.lb_control_high_quality));
        menu.getMenu().add("Playback speed").setOnMenuItemClickListener(i -> action(R.id.action_video_speed));
        menu.getMenu().add("Subtitles").setOnMenuItemClickListener(i -> action(R.id.lb_control_closed_captioning));
        menu.getMenu().add("Repeat mode").setOnMenuItemClickListener(i -> action(R.id.action_repeat));
        menu.getMenu().add("Video information").setOnMenuItemClickListener(i -> action(R.id.action_info));
        menu.show();
        postponeHide();
    }
    private boolean action(int id) { player.mobileAction(id); return true; }

    void onVideoChanged(Video video) {
        if (video == null) return;
        title.setText(video.getTitleFull() != null ? video.getTitleFull() : video.getTitle());
        CharSequence second = video.getSecondTitleFull();
        metadata.setText(second == null ? "" : second);
        String name = video.getAuthor();
        author.setText(name != null ? name : "Channel");
        setControlsShown(true);
        postponeHide();
    }
    void onSuggestions(ObjectAdapter rows) { feed.scheduleRows(rows); }

    private long duration() {
        long ms = player.getDurationMs();
        return ms > 0 && ms < TimeUnit.DAYS.toMillis(10) ? ms : 0;
    }
    private String format(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return String.format(Locale.US, "%d:%02d:%02d",
                s / 3600, (s / 60) % 60, s % 60);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
    private void updatePlayback() {
        if (player == null || seek == null) return;
        try {
            boolean playing = player.getPlayWhenReady();
            toggle.setImageResource(playing ? R.drawable.mobile_ic_pause : R.drawable.mobile_ic_play);
            toggle.setContentDescription(playing ? "Pause" : "Play");
            if (tracking) return;
            long duration = duration();
            long position = Math.max(0, player.getPositionMs());
            seek.setEnabled(duration > 0);
            if (duration > 0) {
                seek.setProgress((int)Math.min(1000, 1000d * position / duration));
                time.setText(format(position) + " / " + format(duration));
            } else {
                time.setText("LIVE");
                seek.setProgress(0);
            }
        } catch (RuntimeException ignored) {
            // Media source may be replacing the player during an autoplay transition.
        }
    }

    void setControlsShown(boolean visible) {
        controlsShown = visible;
        controls.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        shade.setBackgroundColor(visible ? 0x66000000 : Color.TRANSPARENT);
        if (visible) postponeHide();
    }
    private void postponeHide() {
        handler.removeCallbacks(autoHide);
        if (!tracking && !stopped) handler.postDelayed(autoHide, 5000);
    }

    void applyLayout() {
        if (videoFrame.getParent() == null) return;
        boolean landscape = activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout.LayoutParams videoParams = (LinearLayout.LayoutParams) videoFrame.getLayoutParams();
        if (landscape) {
            videoParams.height = 0;
            videoParams.weight = 1f;
            below.setVisibility(View.GONE);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            int width = screen.getWidth() > 0 ? screen.getWidth()
                    : activity.getResources().getDisplayMetrics().widthPixels;
            videoParams.height = Math.round(width * 9f / 16f);
            videoParams.weight = 0;
            below.setVisibility(View.VISIBLE);
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            activity.getWindow().setStatusBarColor(Color.BLACK);
            activity.getWindow().setNavigationBarColor(Color.WHITE);
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        videoFrame.setLayoutParams(videoParams);
    }

    private void toggleFullscreen() {
        boolean landscape = activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        activity.setRequestedOrientation(landscape
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        postponeHide();
    }

    void resume() {
        stopped = false;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }
    void stop() {
        stopped = true;
        handler.removeCallbacks(refresh);
        handler.removeCallbacks(autoHide);
    }
}
