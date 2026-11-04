package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.widget.ScrollView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
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
    private PlaybackFragment player;
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
    private boolean running = true;

    private final Runnable dismiss = () -> {
        if (!tracking && player.getPlayWhenReady()) setVisible(false);
    };
    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (released || !running) return;
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
            @Override public boolean onSingleTapUp(MotionEvent event) {
                // React on finger-up immediately instead of waiting for the
                // double-tap timeout (which made phone controls feel sluggish).
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
        root.setOnTouchListener(new View.OnTouchListener() {
            float downY, downX;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downY = event.getY();
                    downX = event.getX();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    float dy = event.getY() - downY;
                    float dx = Math.abs(event.getX() - downX);
                    if (dy > dp(72) && Math.abs(dy) > 1.5f * dx) {
                        activity.enterMobilePip(true);
                        return true;
                    }
                }
                return gestures.onTouchEvent(event);
            }
        });
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
        addButton(top, R.drawable.mobile_ic_pip, "Picture-in-picture",
                () -> activity.enterMobilePip(false));
        addButton(top, R.drawable.mobile_ic_settings, "Settings", this::showOptions);

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

    // YouTube-style navigation hierarchy with live options from the actual
    // ExoPlayer tracks, not hard-coded '4K/1080p' choices that may not exist.
    private void showOptions() {
        showSheet("Settings", null,
                new String[]{"Quality", "Playback speed", "Captions", "Loop video",
                        "Picture-in-picture", "Background audio", "Video information"},
                new Runnable[]{this::showQuality, this::showSpeeds, this::showCaptions,
                        () -> runAction(R.id.action_repeat),
                        () -> activity.enterMobilePip(false),
                        activity::startMobileBackgroundAudio,
                        () -> runAction(R.id.action_info)});
    }

    private void showQuality() {
        FormatItem selected = player.getVideoFormat();
        String current = selected != null && selected.getHeight() > 0
                && selected.getHeight() < 10000 ? selected.getHeight() + "p" : "Auto";
        showSheet("Quality", "Current: " + current,
                new String[]{"Auto (recommended)", "Higher picture quality",
                        "Data saver", "Advanced"},
                new Runnable[]{
                        () -> player.setFormat(FormatItem.VIDEO_AUTO),
                        () -> chooseQuality(true),
                        () -> chooseQuality(false),
                        this::showAdvancedQuality
                });
    }

    private List<FormatItem> availableVideoFormats() {
        List<FormatItem> all = player.getVideoFormats();
        ArrayList<FormatItem> result = new ArrayList<>();
        if (all != null) for (FormatItem track : all) {
            if (track != null && track.getHeight() > 0
                    && track.getWidth() > 0 && track.getHeight() < 10000) result.add(track);
        }
        Collections.sort(result, (left, right) -> {
            int byHeight = Integer.compare(right.getHeight(), left.getHeight());
            return byHeight != 0 ? byHeight :
                    Float.compare(right.getFrameRate(), left.getFrameRate());
        });
        return result;
    }

    private void chooseQuality(boolean high) {
        List<FormatItem> tracks = availableVideoFormats();
        if (tracks.isEmpty()) {
            android.widget.Toast.makeText(activity, "Quality options unavailable for this video",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        player.setFormat(tracks.get(high ? 0 : tracks.size() - 1));
    }

    private void showAdvancedQuality() {
        List<FormatItem> tracks = availableVideoFormats();
        LinkedHashMap<String,FormatItem> distinct = new LinkedHashMap<>();
        for (FormatItem track : tracks) {
            int height = track.getHeight();
            int frameRate = Math.round(track.getFrameRate());
            String label = height + "p" + (frameRate > 30 ? frameRate : "");
            if (!distinct.containsKey(label)) distinct.put(label,track);
        }
        if (distinct.isEmpty()) {
            android.widget.Toast.makeText(activity, "No video resolutions reported",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = distinct.keySet().toArray(new String[0]);
        Runnable[] actions = new Runnable[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final FormatItem chosen = distinct.get(labels[i]);
            actions[i] = () -> player.setFormat(chosen);
        }
        showSheet("Advanced · Quality", "Choose available video resolution", labels, actions);
    }

    private void showSpeeds() {
        final float[] speeds = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] labels = new String[speeds.length];
        Runnable[] actions = new Runnable[speeds.length];
        for (int i=0;i<speeds.length;i++) {
            final float value = speeds[i];
            labels[i] = (value == 1f ? "Normal" : value + "×")
                    + (Math.abs(player.getSpeed()-value)<0.01f ? "  ✓" : "");
            actions[i] = () -> player.setSpeed(value);
        }
        showSheet("Playback speed", null, labels, actions);
    }

    private void showCaptions() {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        labels.add("Off");
        actions.add(() -> player.setFormat(FormatItem.SUBTITLE_NONE));
        List<FormatItem> tracks = player.getSubtitleFormats();
        if (tracks != null) for (FormatItem track : tracks) {
            if (track == null || track.isDefault() || track.getLanguage() == null) continue;
            String language = track.getLanguage();
            labels.add(!TextUtils.isEmpty(language) ? language : "Captions");
            actions.add(() -> player.setFormat(track));
        }
        showSheet("Captions", null,
                labels.toArray(new String[0]), actions.toArray(new Runnable[0]));
    }

    private void showSheet(String title, String subtitle, String[] labels, Runnable[] actions) {
        if (released || activity.isFinishing()) return;
        Dialog sheet = new Dialog(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16),dp(10),dp(16),dp(14));
        layout.setBackgroundColor(0xFF202124);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(19);
        heading.setPadding(dp(8),dp(10),dp(8),dp(14));
        layout.addView(heading);
        if (subtitle != null) {
            TextView detail = new TextView(activity);
            detail.setText(subtitle);
            detail.setTextColor(0xFFCCCCCC);
            detail.setTextSize(13);
            detail.setPadding(dp(8),0,dp(8),dp(10));
            layout.addView(detail);
        }
        ScrollView scroller = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(rows);
        for (int i=0;i<labels.length;i++) {
            final Runnable action = actions[i];
            TextView row = new TextView(activity);
            row.setText(labels[i]);
            row.setTextColor(Color.WHITE);
            row.setTextSize(16);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14),dp(8),dp(14),dp(8));
            row.setMinHeight(dp(52));
            rows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOnClickListener(v -> {
                sheet.dismiss();
                action.run();
                scheduleDismiss();
            });
        }
        layout.addView(scroller);
        sheet.setContentView(layout);
        if (sheet.getWindow() != null) {
            sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            sheet.getWindow().setGravity(Gravity.BOTTOM);
            sheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            int max = Math.round(activity.getResources().getDisplayMetrics().heightPixels * .76f);
            scroller.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.min(dp(labels.length*54), max)));
        }
        sheet.show();
        if (sheet.getWindow() != null) {
            sheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            sheet.getWindow().setGravity(Gravity.BOTTOM);
        }
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
        boolean compact = MobileUi.compactTouchPlayer(heightPx /
                Math.max(0.1f, activity.getResources().getDisplayMetrics().density));
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

    void resume() {
        if (released) return;
        running = true;
        handler.removeCallbacks(progressTick);
        handler.post(progressTick);
        scheduleDismiss();
    }

    void pause() {
        running = false;
        handler.removeCallbacks(progressTick);
        handler.removeCallbacks(dismiss);
    }

    void release() {
        released = true;
        handler.removeCallbacks(dismiss);
        handler.removeCallbacks(progressTick);
    }
}
