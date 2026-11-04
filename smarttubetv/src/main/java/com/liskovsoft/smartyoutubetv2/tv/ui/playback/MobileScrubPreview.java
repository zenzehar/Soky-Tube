package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.previewtimebar.StoryboardManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Phone-only seek previews built from the original YouTube storyboard cache.
 * A time-and-thumbnail bubble follows finger scrubbing; an upward swipe
 * reveals a horizontally scrolling precise-seek filmstrip. Select any frame
 * to seek. Missing storyboards fall back to timestamps without fake thumbnails.
 */
final class MobileScrubPreview {
    interface SeekHandler { void seek(long milliseconds); }

    private final PlaybackActivity activity;
    private final PlaybackFragment player;
    private final FrameLayout parent;
    private final StoryboardManager storyboards;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout bubble;
    private final ImageView hero;
    private final TextView caption;
    private final LinearLayout filmstripBox;
    private final LinearLayout filmstripCells;
    private final HorizontalScrollView filmstrip;
    private final ArrayList<Video> chapters = new ArrayList<>();
    private Video video;
    private String lastVideoId;
    private long initializedDuration;
    private int previewGeneration;
    private Runnable pendingFrameRequest;
    private long previousRequestMs = -1;
    private boolean released;
    private boolean expanded;
    private int frameWidth;
    private int frameHeight;

    private int dp(float n) { return MobileUi.dp(activity,n); }

    private GradientDrawable rounded(int color,int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private String clock(long timeMs) {
        long secs = Math.max(0L,timeMs/1000L);
        if (secs >= 3600L) return String.format(Locale.US,
                "%d:%02d:%02d",secs/3600L,secs/60L%60L,secs%60L);
        return String.format(Locale.US,"%d:%02d",secs/60L,secs%60L);
    }

    MobileScrubPreview(PlaybackActivity activity, PlaybackFragment player, FrameLayout parent) {
        this.activity = activity;
        this.player = player;
        this.parent = parent;
        this.storyboards = new StoryboardManager(activity);
        frameWidth = dp(136);
        frameHeight = dp(76);

        bubble = new LinearLayout(activity);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setGravity(Gravity.CENTER_HORIZONTAL);
        bubble.setPadding(dp(5),dp(5),dp(5),dp(6));
        bubble.setBackground(rounded(0xE6222222,10));
        hero = new ImageView(activity);
        hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.setBackgroundColor(0xFF191919);
        hero.setContentDescription("Scrub thumbnail");
        bubble.addView(hero,new LinearLayout.LayoutParams(frameWidth,frameHeight));
        caption = new TextView(activity);
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(12);
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(2);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setPadding(dp(5),dp(4),dp(5),0);
        bubble.addView(caption,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams bubbleLp = new FrameLayout.LayoutParams(
                dp(155),ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        bubbleLp.bottomMargin = dp(79);
        parent.addView(bubble,bubbleLp);
        bubble.setVisibility(View.GONE);

        filmstripBox = new LinearLayout(activity);
        filmstripBox.setOrientation(LinearLayout.VERTICAL);
        filmstripBox.setBackground(rounded(0xEE191919,12));
        TextView hint = new TextView(activity);
        hint.setText("Precise seeking  ·  Swipe thumbnails or tap to seek");
        hint.setTextColor(0xFFEEEEEE);
        hint.setTextSize(11);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        hint.setPadding(dp(12),dp(6),dp(8),dp(4));
        filmstripBox.addView(hint);
        filmstrip = new HorizontalScrollView(activity);
        filmstrip.setHorizontalScrollBarEnabled(false);
        filmstrip.setFillViewport(false);
        filmstripCells = new LinearLayout(activity);
        filmstripCells.setOrientation(LinearLayout.HORIZONTAL);
        filmstripCells.setGravity(Gravity.CENTER_VERTICAL);
        filmstripCells.setPadding(dp(9),0,dp(9),dp(4));
        filmstrip.addView(filmstripCells);
        filmstripBox.addView(filmstrip,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(91)));
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(121),Gravity.BOTTOM);
        stripParams.bottomMargin = dp(72);
        parent.addView(filmstripBox,stripParams);
        filmstripBox.setVisibility(View.GONE);
    }

    void setChapters(List<Video> items) {
        chapters.clear();
        if (items != null) for (Video video : items) {
            if (video != null && video.isChapter) chapters.add(video);
        }
    }

    private String chapterAt(long ms) {
        String current = null;
        for (Video chapter : chapters) {
            if (chapter.startTimeMs <= ms) {
                String title = chapter.getTitleFull();
                if (!TextUtils.isEmpty(title)) current = title;
            }
        }
        return current;
    }

    void setVideo(Video source) {
        previewGeneration++;
        storyboards.release();
        video = source;
        lastVideoId = source != null ? source.videoId : null;
        initializedDuration = -1;
        previousRequestMs = -1;
        if (pendingFrameRequest != null) handler.removeCallbacks(pendingFrameRequest);
        setChapters(null);
        hero.setImageDrawable(null);
        hide();
        ensureStoryboard();
    }

    private void ensureStoryboard() {
        if (released || video == null || video.isUpcoming || video.isLive) return;
        long duration = Math.max(0L,player.getDurationMs());
        if (duration <= 0 || duration == initializedDuration) return;
        // Video duration can be unknown at activity creation. Initialize when
        // actual media duration appears in the player, not with bogus zero.
        initializedDuration = duration;
        storyboards.init(video,duration);
    }

    void refresh() {
        ensureStoryboard();
    }

    void showAt(long positionMs) {
        if (released || player.getDurationMs() <= 0) return;
        ensureStoryboard();
        bubble.setVisibility(View.VISIBLE);
        if (!expanded) requestBitmap(positionMs);
        setCaption(positionMs);
        resize();
    }

    private void setCaption(long positionMs) {
        String chapter = chapterAt(positionMs);
        String line = clock(positionMs);
        caption.setText(chapter == null ? line : line + "  ·  " + chapter);
    }

    private void requestBitmap(long positionMs) {
        if (released || Math.abs(positionMs - previousRequestMs) < 650) return;
        previousRequestMs = positionMs;
        if (pendingFrameRequest != null) handler.removeCallbacks(pendingFrameRequest);
        final int token = ++previewGeneration;
        pendingFrameRequest = () -> {
            if (released || token != previewGeneration) return;
            storyboards.getBitmapAtMs(positionMs,bitmap -> {
                if (released || token != previewGeneration || activity.isFinishing()) return;
                handler.post(() -> {
                    if (!released && token == previewGeneration &&
                            bubble.getVisibility() == View.VISIBLE)
                        hero.setImageBitmap(bitmap);
                });
            });
        };
        // Debounce scrubbing so fast finger motion does not issue dozens of
        // Glide requests per second or freeze modest Android phones.
        handler.postDelayed(pendingFrameRequest,85);
    }

    void showFilmstrip(long positionMs,SeekHandler onSeek) {
        if (released || player.getDurationMs() <= 0) return;
        ensureStoryboard();
        expanded = true;
        bubble.setVisibility(View.GONE);
        filmstripBox.setVisibility(View.VISIBLE);
        filmstripCells.removeAllViews();
        long duration = player.getDurationMs();
        long[] available = storyboards.getSeekPositions();
        final ArrayList<Long> times = new ArrayList<>();
        if (available != null && available.length > 0) {
            int at = 0;
            while (at < available.length - 1 && available[at] < positionMs) at++;
            int first = Math.max(0,at-5);
            int last = Math.min(available.length-1,at+5);
            for (int i=first;i<=last;i++) times.add(available[i]);
        } else {
            // Storyboard metadata may still be downloading. Precise seeking is
            // available with timestamps even when thumbnails are unavailable.
            long step = Math.max(1000L,duration / 40L);
            for (int i=-5;i<=5;i++) times.add(
                    Math.max(0L,Math.min(duration,positionMs+i*step)));
        }
        final int token = ++previewGeneration;
        for (Long timeMs : times) {
            final long time = timeMs;
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(3),dp(2),dp(3),dp(2));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    dp(100),ViewGroup.LayoutParams.MATCH_PARENT);
            itemLp.setMarginEnd(dp(4));
            filmstripCells.addView(item,itemLp);
            ImageView thumb = new ImageView(activity);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(0xFF343434);
            item.addView(thumb,new LinearLayout.LayoutParams(dp(92),dp(52)));
            TextView label = new TextView(activity);
            label.setText(clock(time));
            label.setTextColor(Color.WHITE);
            label.setTextSize(11);
            label.setGravity(Gravity.CENTER);
            item.addView(label,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,dp(22)));
            item.setOnClickListener(v -> {
                onSeek.seek(time);
                hide();
            });
            storyboards.getBitmapAtMs(time,bitmap -> {
                if (released || token != previewGeneration || activity.isFinishing()) return;
                handler.post(() -> {
                    if (token == previewGeneration && !released &&
                            filmstripBox.getVisibility()==View.VISIBLE)
                        thumb.setImageBitmap(bitmap);
                });
            });
        }
        resize();
        int center = Math.max(0,(times.size()-1)/2);
        filmstrip.post(() -> filmstrip.scrollTo(Math.max(0,
                center * dp(104) - filmstrip.getWidth()/2 + dp(50)),0));
    }

    boolean isExpanded() { return expanded; }

    void endDrag() {
        if (!expanded) {
            bubble.setVisibility(View.GONE);
            previewGeneration++;
        }
    }

    void hide() {
        expanded = false;
        bubble.setVisibility(View.GONE);
        filmstripBox.setVisibility(View.GONE);
        ++previewGeneration;
        if (pendingFrameRequest != null) handler.removeCallbacks(pendingFrameRequest);
        previousRequestMs = -1;
    }

    void resize() {
        int playerHeight = parent.getHeight();
        if (playerHeight <= 0) return;
        boolean tiny = playerHeight < dp(220);
        hero.setVisibility(tiny ? View.GONE : View.VISIBLE);
        if (tiny && expanded) {
            ViewGroup.LayoutParams strip = filmstripBox.getLayoutParams();
            if (strip instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)strip;
                lp.bottomMargin=dp(52);
                if (lp.height != dp(105)) lp.height=dp(105);
                filmstripBox.setLayoutParams(lp);
            }
        }
    }

    void release() {
        if (released) return;
        released = true;
        previewGeneration++;
        storyboards.release();
        handler.removeCallbacksAndMessages(null);
        hero.setImageDrawable(null);
        filmstripCells.removeAllViews();
    }
}
