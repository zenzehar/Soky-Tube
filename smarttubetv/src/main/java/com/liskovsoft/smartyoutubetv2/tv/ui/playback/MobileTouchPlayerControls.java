package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.widget.ScrollView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
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
import android.view.animation.OvershootInterpolator;
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
    private final FrameLayout videoContainer;
    private final LinearLayout top;
    private final LinearLayout center;
    private final LinearLayout bottom;
    private final SeekBar seek;
    private final MobileSponsorMarkersView sponsorMarkers;
    private MobileScrubPreview scrubPreviews;
    private final ArrayList<Video> chapterVideos = new ArrayList<>();
    private long chapterMarkerDuration = -1;
    private long sponsorMarkerSignature = Long.MIN_VALUE;

    private final TextView time;
    private final ImageButton play;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean visible = true;
    private boolean tracking;
    private boolean released;
    private boolean running = true;
    private Boolean lastMediaPlaying;
    private boolean draggingToPip;

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
        this.videoContainer = container;
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
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    downY = event.getY();
                    downX = event.getX();
                    draggingToPip = false;
                    videoContainer.animate().cancel();
                    return gestures.onTouchEvent(event);
                }
                float dy = Math.max(0f, event.getY() - downY);
                float dx = Math.abs(event.getX() - downX);
                if (action == MotionEvent.ACTION_MOVE) {
                    if (!draggingToPip && dy > dp(14) && dy > dx * 1.4f)
                        draggingToPip = true;
                    if (draggingToPip) {
                        // Follow the finger, reducing the video toward its PiP size.
                        float progress = Math.min(1f, dy / Math.max(dp(130), root.getHeight()));
                        videoContainer.setPivotX(videoContainer.getWidth() / 2f);
                        videoContainer.setPivotY(0f);
                        videoContainer.setTranslationY(dp(42) * progress);
                        videoContainer.setScaleX(1f - .22f * progress);
                        videoContainer.setScaleY(1f - .22f * progress);
                        return true;
                    }
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (draggingToPip) {
                        boolean complete = action == MotionEvent.ACTION_UP &&
                                dy > dp(72) && dy > dx * 1.4f;
                        draggingToPip = false;
                        if (complete) {
                            // Physical shrinking animation bridges into platform PiP,
                            // which then takes over the system-level floating window.
                            videoContainer.animate().translationY(dp(65)).scaleX(.76f).scaleY(.76f)
                                    .setDuration(165)
                                    .withEndAction(() -> {
                                        activity.enterMobilePip(true);
                                        root.postDelayed(this::resetPipTransform, 220);
                                    }).start();
                        } else {
                            resetPipTransform();
                        }
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
                if (scrubPreviews != null)
                    scrubPreviews.showAt(Math.round(player.getDurationMs()
                            * bar.getProgress()/1000d));
            }
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) {
                    long duration = Math.max(0L, player.getDurationMs());
                    long at = Math.round(duration * value / 1000d);
                    String chapter = chapterTitle(at);
                    time.setText(duration > 0 ? format(at) + " / " + format(duration) +
                            (chapter == null ? "" : "   ·   " + chapter) : "LIVE");
                    if (scrubPreviews != null) scrubPreviews.showAt(at);
                }
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                long duration = player.getDurationMs();
                if (duration > 0) player.setPositionMs(Math.round(duration * bar.getProgress() / 1000d));
                tracking = false;
                if (scrubPreviews != null) scrubPreviews.endDrag();
                scheduleDismiss();
            }
        });
        FrameLayout timeline = new FrameLayout(activity);
        timeline.addView(seek, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sponsorMarkers = new MobileSponsorMarkersView(activity, seek);
        timeline.addView(sponsorMarkers, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Markings do not intercept touch, and are drawn over the native seekbar.
        sponsorMarkers.setClickable(false);
        bottom.addView(timeline, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(37)));
        scrubPreviews = new MobileScrubPreview(activity, player, root);
        seek.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            boolean expandedThisGesture;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downY = event.getY();
                    expandedThisGesture = false;
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                        && !expandedThisGesture && downY - event.getY() > dp(32)) {
                    expandedThisGesture = true;
                    long duration = Math.max(0L,player.getDurationMs());
                    if (duration > 0) scrubPreviews.showFilmstrip(
                            Math.round(duration*seek.getProgress()/1000d),player::setPositionMs);
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    expandedThisGesture = false;
                }
                // Return false to retain genuine Android SeekBar thumb handling.
                return false;
            }
        });

        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)-> resize(r-l,b-t));
        resize(container.getWidth(), container.getHeight());
        handler.post(progressTick);
        scheduleDismiss();
    }

    void resetPipTransform() {
        videoContainer.animate().cancel();
        videoContainer.animate().translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(260).setInterpolator(new OvershootInterpolator(.65f)).start();
    }

    // YouTube-style navigation hierarchy with live options from the actual
    // ExoPlayer tracks, not hard-coded '4K/1080p' choices that may not exist.
    void openSettings() { showOptions(); }

    private void showOptions() {
        showSheet("Settings", null,
                new String[]{"Quality", "Playback speed", "Audio language", "Captions",
                        "Chapters", "Comments", "Advanced options",
                        "Picture-in-picture", "Background audio"},
                new Runnable[]{this::showQuality, this::showSpeeds, this::showAudioLanguage,
                        this::showCaptions, this::showChapters, player::openMobileComments,
                        this::showAdvancedOptions,
                        () -> activity.enterMobilePip(false),
                        activity::startMobileBackgroundAudio});
    }

    private void showAudioLanguage() {
        List<FormatItem> tracks = player.getAudioFormats();
        if (tracks == null || tracks.isEmpty()) {
            android.widget.Toast.makeText(activity,
                    "Alternative audio tracks are unavailable for this video",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        for (FormatItem track : tracks) {
            if (track == null) continue;
            String language = track.getLanguage();
            CharSequence title = track.getTitle();
            String label = !TextUtils.isEmpty(language) ? language :
                    title != null ? title.toString() : "Original audio";
            if (track.isSelected()) label += "   ✓";
            labels.add(label);
            actions.add(() -> player.setFormat(track));
        }
        showSheet("Audio language", "Available audio tracks from this video",
                labels.toArray(new String[0]), actions.toArray(new Runnable[0]));
    }

    private void showAdvancedOptions() {
        showSheet("Advanced options", null,
                new String[]{"Video information", "Loop video",
                        "SponsorBlock settings", "Audio language",
                        "Exact resolution", "Playback speed"},
                new Runnable[]{() -> runAction(R.id.action_info),
                        () -> runAction(R.id.action_repeat),
                        () -> com.liskovsoft.smartyoutubetv2.common.app.presenters.settings
                                .SponsorBlockSettingsPresenter.instance(activity).show(),
                        this::showAudioLanguage,
                        this::showAdvancedQuality, this::showSpeeds});
    }

    private void showChapters() {
        if (chapterVideos.isEmpty()) {
            android.widget.Toast.makeText(activity,
                    "This video has no chapters",android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[chapterVideos.size()];
        Runnable[] actions = new Runnable[chapterVideos.size()];
        for (int i=0;i<chapterVideos.size();i++) {
            Video chapter = chapterVideos.get(i);
            labels[i] = format(chapter.startTimeMs) + "  " + chapter.getTitleFull();
            actions[i] = () -> player.setPositionMs(chapter.startTimeMs);
        }
        showSheet("Chapters", "Jump to a chapter", labels, actions);
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
        LinearLayout sheetHeader = new LinearLayout(activity);
        sheetHeader.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(sheetHeader,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        if (!"Settings".equals(title)) {
            TextView back = new TextView(activity);
            back.setText("‹");
            back.setTextSize(32);
            back.setTextColor(Color.WHITE);
            back.setGravity(Gravity.CENTER);
            back.setContentDescription("Back to Settings");
            sheetHeader.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
            back.setOnClickListener(v -> {
                sheet.dismiss();
                if (title.startsWith("Advanced · Quality")) showQuality();
                else if ("Audio language".equals(title) || "Advanced options".equals(title)) showOptions();
                else showOptions();
            });
        }
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(19);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                0,ViewGroup.LayoutParams.MATCH_PARENT,1f);
        headingParams.setMarginStart(dp(6));
        sheetHeader.addView(heading,headingParams);
        TextView close = new TextView(activity);
        close.setText("×");
        close.setTextSize(28);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(Color.WHITE);
        close.setContentDescription("Close player Settings");
        sheetHeader.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        close.setOnClickListener(v -> sheet.dismiss());
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
            float availableHeightDp = activity.getResources().getConfiguration().screenHeightDp;
            int max = Math.max(dp(96),Math.round(availableHeightDp *
                    activity.getResources().getDisplayMetrics().density * .72f)
                    - dp(subtitle == null ? 60 : 92));
            scroller.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(dp(52),Math.min(dp(labels.length*54),max))));
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

    void setSponsorSegments(List<SeekBarSegment> segments) {
        sponsorMarkerSignature = Long.MIN_VALUE;
        sponsorMarkers.updateSegments(segments);
    }

    /** Sync asynchronously loaded SponsorBlock categories after duration resolves.
     * Prevents stale segments and avoids invalidating the seekbar every tick.
     */
    private void refreshSponsorMarkers(long duration) {
        if (duration <= 0) return;
        List<SeekBarSegment> source = player.getMobileSponsorSegmentsForDuration(duration);
        long signature = 1125899906842597L;
        for (SeekBarSegment segment : source) {
            if (segment == null) continue;
            signature = signature * 31 + Float.floatToIntBits(segment.startProgress);
            signature = signature * 31 + Float.floatToIntBits(segment.endProgress);
            signature = signature * 31 + segment.color;
        }
        if (signature != sponsorMarkerSignature) {
            sponsorMarkerSignature = signature;
            // A temporary empty response must not erase original colour markers
            // while the SponsorBlock request is still completing.
            if (!source.isEmpty())
                sponsorMarkers.updateTrustedSponsorSegments(source);
        }
    }
    void setChapters(List<Video> chapters) {
        chapterVideos.clear();
        if (chapters != null) chapterVideos.addAll(chapters);
        chapterMarkerDuration = -1;
        // Chapters may arrive before ExoPlayerController.setVideo(). Defer
        // timeline measurements until refresh() obtains a valid duration.
        sponsorMarkers.updateChapters(chapterVideos, 0L);
        if (scrubPreviews != null) scrubPreviews.setChapters(chapterVideos);
    }

    void onVideoChanged(Video video) {
        if (scrubPreviews != null) scrubPreviews.setVideo(video);
        chapterMarkerDuration = -1;
        sponsorMarkerSignature = Long.MIN_VALUE;
        sponsorMarkers.updateSegments(java.util.Collections.emptyList());
    }

    private String chapterTitle(long position) {
        String title = null;
        for (Video chapter : chapterVideos) {
            if (chapter != null && chapter.startTimeMs <= position)
                title = chapter.getTitleFull();
        }
        return title;
    }


    void refresh() {
        if (released) return;
        boolean playing = player.getPlayWhenReady();
        if (lastMediaPlaying == null || lastMediaPlaying != playing) {
            lastMediaPlaying = playing;
            MobilePlaybackService.refresh();
        }
        play.setImageResource(playing ? R.drawable.mobile_ic_pause : R.drawable.mobile_ic_play);
        play.setContentDescription(playing ? "Pause video" : "Resume video");
        if (tracking) return;
        long duration = Math.max(0L, player.getDurationMs());
        long position = Math.max(0L, player.getPositionMs());
        refreshSponsorMarkers(duration);
        if (scrubPreviews != null) scrubPreviews.refresh();
        if (duration != chapterMarkerDuration) {
            chapterMarkerDuration = duration;
            sponsorMarkers.updateChapters(chapterVideos,duration);
        }
        if (duration > 0) {
            seek.setEnabled(true);
            seek.setProgress((int) Math.min(1000d, position * 1000d / duration));
            String chapter = chapterTitle(position);
            time.setText(format(position) + " / " + format(duration)
                    + (chapter == null ? "" : "   ·   " + chapter));
            time.setSingleLine(true);
            time.setEllipsize(TextUtils.TruncateAt.END);
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
        if (scrubPreviews != null) scrubPreviews.resize();
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
        } else {
            handler.removeCallbacks(dismiss);
            if (scrubPreviews != null) scrubPreviews.hide();
        }
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
        if (scrubPreviews != null) scrubPreviews.release();
    }
}
