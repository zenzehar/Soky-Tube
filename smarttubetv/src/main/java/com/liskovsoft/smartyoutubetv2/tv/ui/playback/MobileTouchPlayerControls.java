package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;

import java.util.Locale;

/**
 * The ONE player control layer shown on phones.
 * Uses PlaybackFragment's existing player and command actions (no second
 * player engine, hidden TV control bar, or multiple competing overlays).
 * Brief gradient bars leave the image visible instead of covering it.
 */
final class MobileTouchPlayerControls {
    private final PlaybackActivity activity;
    private final PlaybackFragment player;
    private final FrameLayout root;
    private final LinearLayout top;
    private final LinearLayout center;
    private final LinearLayout bottom;
    private final SeekBar seek;
    private final TextView time;
    private final ImageButton play;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean visible = true;
    private boolean tracking;
    private boolean released;

    private final Runnable dismiss = () -> {
        if (!tracking && player.getPlayWhenReady()) setVisible(false);
    };
    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (released) return;
            refresh();
            handler.postDelayed(this, 650);
        }
    };

    private int dp(float n) { return MobileUi.dp(activity, n); }

    private GradientDrawable circle() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x55000000);
        bg.setCornerRadius(dp(99));
        return bg;
    }

    private GradientDrawable gradient(boolean isTop) {
        return new GradientDrawable(isTop
                ? GradientDrawable.Orientation.TOP_BOTTOM
                : GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {0x90000000, 0x00000000});
    }

    private ImageButton button(int drawable, String description) {
        ImageButton icon = new ImageButton(activity);
        icon.setImageResource(drawable);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(circle());
        icon.setContentDescription(description);
        return icon;
    }

    private ImageButton addButton(LinearLayout parent, int drawable, String description, Runnable action) {
        ImageButton b = button(drawable, description);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(48));
        p.setMarginStart(dp(8));
        p.setMarginEnd(dp(8));
        parent.addView(b, p);
        b.setOnClickListener(v -> {
            action.run();
            scheduleDismiss();
        });
        return b;
    }

    MobileTouchPlayerControls(PlaybackActivity activity, PlaybackFragment fragment, FrameLayout container) {
        this.activity = activity;
        this.player = fragment;
        root = new FrameLayout(activity);
        root.setClickable(true);
        root.setFocusable(false);
        root.setBackgroundColor(Color.TRANSPARENT);

        GestureDetector gestures = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                setVisible(!visible);
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event) {
                long duration = Math.max(0L, player.getDurationMs());
                if (duration <= 0) return true;
                long position = Math.max(0L, player.getPositionMs());
                long delta = event.getX() < root.getWidth() / 2f ? -10000L : 10000L;
                player.setPositionMs(Math.max(0L, Math.min(duration, position + delta)));
                setVisible(true);
                return true;
            }
        });
        root.setOnTouchListener((v, event) -> gestures.onTouchEvent(event));
        container.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), 0, dp(6), 0);
        top.setBackground(gradient(true));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(49), Gravity.TOP);
        root.addView(top, topParams);
        addButton(top, R.drawable.mobile_ic_back, "Back", activity::onBackPressed);
        View filler = new View(activity);
        top.addView(filler, new LinearLayout.LayoutParams(0, 1, 1f));
        addButton(top, R.drawable.mobile_ic_more, "Playback options", this::showOptions);

        center = new LinearLayout(activity);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams middleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52), Gravity.CENTER);
        root.addView(center, middleParams);
        addButton(center, R.drawable.mobile_ic_prev, "Previous video", player::skipToPrevious);
        play = addButton(center, R.drawable.mobile_ic_pause, "Pause", () -> {
            player.setPlayWhenReady(!player.getPlayWhenReady());
            refresh();
        });
        addButton(center, R.drawable.mobile_ic_next, "Next video", player::skipToNext);

        bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(8), dp(4), dp(8), dp(2));
        bottom.setBackground(gradient(false));
        FrameLayout.LayoutParams botParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(65), Gravity.BOTTOM);
        root.addView(bottom, botParams);

        time = new TextView(activity);
        time.setTextColor(Color.WHITE);
        time.setTextSize(12);
        time.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        time.setText("0:00 / --:--");
        bottom.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(23)));

        seek = new SeekBar(activity);
        seek.setMax(1000);
        seek.setProgressTintList(ColorStateList.valueOf(0xFFFF2020));
        seek.setThumbTintList(ColorStateList.valueOf(0xFFFF2020));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0xCCDDDDDD));
        seek.setContentDescription("Seek video. Drag to scrub");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {
                tracking = true;
                handler.removeCallbacks(dismiss);
            }
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) {
                    long duration = Math.max(0L, player.getDurationMs());
                    time.setText(duration > 0
                            ? format(Math.round(duration * value / 1000d)) + " / " + format(duration)
                            : "LIVE");
                }
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                long duration = player.getDurationMs();
                if (duration > 0) player.setPositionMs(Math.round(duration * bar.getProgress() / 1000d));
                tracking = false;
                scheduleDismiss();
            }
        });
        bottom.addView(seek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(37)));

        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)-> resize(r-l,b-t));
        resize(container.getWidth(), container.getHeight());
        handler.post(progressTick);
        scheduleDismiss();
    }

    private void showOptions() {
        PopupMenu options = new PopupMenu(activity, top.getChildAt(top.getChildCount() - 1));
        options.getMenu().add("Quality").setOnMenuItemClickListener(i -> runAction(R.id.lb_control_high_quality));
        options.getMenu().add("Playback speed").setOnMenuItemClickListener(i -> runAction(R.id.action_video_speed));
        options.getMenu().add("Subtitles").setOnMenuItemClickListener(i -> runAction(R.id.lb_control_closed_captioning));
        options.getMenu().add("Repeat").setOnMenuItemClickListener(i -> runAction(R.id.action_repeat));
        options.getMenu().add("Video information").setOnMenuItemClickListener(i -> runAction(R.id.action_info));
        options.show();
    }

    private boolean runAction(int id) {
        player.mobileAction(id);
        return true;
    }

    private String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds >= 3600) return String.format(Locale.US, "%d:%02d:%02d",
                seconds/3600, seconds/60%60, seconds%60);
        return String.format(Locale.US, "%d:%02d", seconds/60, seconds%60);
    }

    void refresh() {
        if (released) return;
        boolean playing = player.getPlayWhenReady();
        play.setImageResource(playing ? R.drawable.mobile_ic_pause : R.drawable.mobile_ic_play);
        play.setContentDescription(playing ? "Pause video" : "Resume video");
        if (tracking) return;
        long duration = Math.max(0L, player.getDurationMs());
        long position = Math.max(0L, player.getPositionMs());
        if (duration > 0) {
            seek.setEnabled(true);
            seek.setProgress((int) Math.min(1000d, position * 1000d / duration));
            time.setText(format(position) + " / " + format(duration));
        } else {
            seek.setEnabled(false);
            seek.setProgress(0);
            time.setText("LIVE");
        }
    }

    private void resize(int widthPx, int heightPx) {
        if (heightPx <= 0) return;
        boolean compact = heightPx < dp(190);
        top.setVisibility(visible && !compact ? View.VISIBLE : View.GONE);
        center.setVisibility(visible ? View.VISIBLE : View.GONE);
        bottom.setVisibility(visible ? View.VISIBLE : View.GONE);
        // Hide nonessential previous/next on very short embedded video frames.
        if (center.getChildCount() >= 3) {
            center.getChildAt(0).setVisibility(compact ? View.GONE : View.VISIBLE);
            center.getChildAt(2).setVisibility(compact ? View.GONE : View.VISIBLE);
        }
        ViewGroup.LayoutParams params = bottom.getLayoutParams();
        int h = dp(compact ? 46 : 65);
        if (params.height != h) {
            params.height = h;
            bottom.setLayoutParams(params);
        }
        time.setVisibility(compact ? View.GONE : View.VISIBLE);
    }

    void setVisible(boolean visible) {
        this.visible = visible;
        top.setVisibility(visible ? View.VISIBLE : View.GONE);
        center.setVisibility(visible ? View.VISIBLE : View.GONE);
        bottom.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            resize(root.getWidth(), root.getHeight());
            refresh();
            scheduleDismiss();
        } else handler.removeCallbacks(dismiss);
    }

    private void scheduleDismiss() {
        handler.removeCallbacks(dismiss);
        if (visible && !tracking && !released) handler.postDelayed(dismiss, 3200);
    }

    void release() {
        released = true;
        handler.removeCallbacks(dismiss);
        handler.removeCallbacks(progressTick);
    }
}
