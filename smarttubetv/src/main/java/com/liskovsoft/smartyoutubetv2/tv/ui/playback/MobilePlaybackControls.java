package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated touch controls above the existing Leanback player.
 * Never created on Android TV. The underlying TV player and remote keys are unchanged.
 */
final class MobilePlaybackControls {
    private static final long HIDE_DELAY_MS = 5_000;
    private final Activity activity;
    private final PlaybackFragment player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final LinearLayout bottom;
    private final Button back;
    private final Button play;
    private final SeekBar seek;
    private final TextView time;
    private boolean seeking;
    private boolean showing;
    private boolean stopped;

    private final Runnable hide = () -> setShown(false);
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            refreshPosition();
            handler.postDelayed(this, 500);
        }
    };

    MobilePlaybackControls(Activity activity, PlaybackFragment player) {
        this.activity = activity;
        this.player = player;
        root = activity.findViewById(android.R.id.content);
        if (root == null) throw new IllegalStateException("No content root");
        back = button("Back", v -> activity.onBackPressed());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                MobileUi.dp(activity, 72), MobileUi.dp(activity, 50),
                Gravity.TOP | Gravity.START);
        root.addView(back, backParams);

        bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(MobileUi.dp(activity, 8), MobileUi.dp(activity, 4),
                MobileUi.dp(activity, 8), MobileUi.dp(activity, 6));
        bottom.setBackgroundColor(0xE613141B);

        time = new TextView(activity);
        time.setTextColor(Color.WHITE);
        time.setTextSize(12);
        time.setText("00:00 / --:--");
        time.setGravity(Gravity.CENTER);
        bottom.addView(time, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, MobileUi.dp(activity, 24)));

        seek = new SeekBar(activity);
        seek.setMax(1000);
        seek.setContentDescription("Video seek position");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long duration = safeDuration();
                    if (duration > 0) {
                        time.setText(format(Math.round(duration * (progress / 1000d))) + " / " + format(duration));
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
                postponeHide();
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                long duration = safeDuration();
                if (duration > 0) player.setPositionMs(Math.round(duration * (bar.getProgress() / 1000d)));
                seeking = false;
                postponeHide();
            }
        });
        bottom.addView(seek, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, MobileUi.dp(activity, 46)));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.CENTER);
        add(buttons, "−10", v -> seekRelative(-10_000));
        play = button("Pause", v -> {
            player.setPlayWhenReady(!player.getPlayWhenReady());
            refreshPosition();
            postponeHide();
        });
        add(buttons, "Play", v -> play.performClick(), play);
        add(buttons, "+10", v -> seekRelative(10_000));
        add(buttons, "Next", v -> { player.skipToNext(); postponeHide(); });
        add(buttons, "More", v -> {
            setShown(false);
            player.showControlsOverlay(true);
        });
        bottom.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, MobileUi.dp(activity, 52)));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, MobileUi.dp(activity, 136), Gravity.BOTTOM);
        root.addView(bottom, bottomParams);
        resume();
        showTemporarily();
    }

    private Button button(String title, View.OnClickListener listener) {
        Button result = new Button(activity);
        result.setText(title);
        result.setAllCaps(false);
        result.setTextSize(13);
        result.setTextColor(Color.WHITE);
        result.setBackgroundColor(0x331A1A24);
        result.setMinWidth(0);
        result.setMinimumWidth(0);
        result.setMinHeight(MobileUi.dp(activity, 48));
        result.setContentDescription(title);
        result.setOnClickListener(listener);
        return result;
    }

    private void add(LinearLayout row, String title, View.OnClickListener listener) {
        add(row, title, listener, button(title, listener));
    }

    private void add(LinearLayout row, String title, View.OnClickListener listener, Button control) {
        row.addView(control, new LinearLayout.LayoutParams(0, MobileUi.dp(activity, 48), 1));
    }

    private void seekRelative(long offsetMs) {
        long position = Math.max(0, player.getPositionMs() + offsetMs);
        long duration = safeDuration();
        player.setPositionMs(duration > 0 ? Math.min(duration, position) : position);
        refreshPosition();
        postponeHide();
    }

    private long safeDuration() {
        long duration = player.getDurationMs();
        return duration > 0 && duration < TimeUnit.DAYS.toMillis(100) ? duration : 0L;
    }

    private void refreshPosition() {
        try {
            long duration = safeDuration();
            long position = Math.max(0, player.getPositionMs());
            play.setText(player.getPlayWhenReady() ? "Pause" : "Play");
            if (!seeking) {
                seek.setEnabled(duration > 0);
                if (duration > 0) {
                    seek.setProgress((int) Math.min(1000, position * 1000d / duration));
                }
                time.setText(format(position) + " / " + (duration > 0 ? format(duration) : "--:--"));
            }
        } catch (RuntimeException ignored) {
            // The player can be in the middle of a media transition.
        }
    }

    private static String format(long timeMs) {
        long totalSeconds = Math.max(0, timeMs / 1000);
        if (totalSeconds >= 3600) {
            return String.format(Locale.US, "%d:%02d:%02d",
                    totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60);
        }
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    void showTemporarily() {
        setShown(true);
        postponeHide();
    }

    private void postponeHide() {
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, HIDE_DELAY_MS);
    }

    private void setShown(boolean show) {
        showing = show;
        back.setVisibility(show ? View.VISIBLE : View.GONE);
        bottom.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    boolean isTouchOnControls(MotionEvent event) {
        if (!showing) return false;
        return event.getY() <= MobileUi.dp(activity, 52) &&
                event.getX() <= MobileUi.dp(activity, 80)
                || event.getY() >= root.getHeight() - MobileUi.dp(activity, 140);
    }

    void hideForPip(boolean inPip) {
        if (inPip) setShown(false);
        else showTemporarily();
    }

    void resume() {
        stopped = false;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    void stop() {
        stopped = true;
        handler.removeCallbacks(refresh);
        handler.removeCallbacks(hide);
    }
}
