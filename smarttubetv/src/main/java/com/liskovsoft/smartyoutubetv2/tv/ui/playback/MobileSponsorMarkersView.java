package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.SeekBar;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import java.util.ArrayList;
import java.util.List;

/** Paints the exact coloured SponsorBlock segments over the phone seek track.
 * This is non-interactive: all finger dragging stays with the native SeekBar.
 * Normalises invalid ratios and clips segments to the available video duration.
 */
final class MobileSponsorMarkersView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<SeekBarSegment> segments = new ArrayList<>();
    private final ArrayList<Video> chapters = new ArrayList<>();
    private long durationMs;
    private final SeekBar progress;

    MobileSponsorMarkersView(Context context, SeekBar seekBar) {
        super(context);
        progress = seekBar;
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    void updateSegments(List<SeekBarSegment> source) {
        if (source != null && !source.isEmpty()) {
            // The original chapter controller also calls setSeekBarSegments()
            // with ALL-BLACK microsegments. Those must not erase SponsorBlock
            // colours: chapter positions are supplied separately below.
            boolean chapterOnly = true;
            for (SeekBarSegment item : source) {
                if (item != null && (item.color & 0x00FFFFFF) != 0) {
                    chapterOnly = false;
                    break;
                }
            }
            if (chapterOnly) return;
        }
        segments.clear();
        if (source != null) {
            for (SeekBarSegment item : source) {
                if (item == null || Float.isNaN(item.startProgress)
                        || Float.isNaN(item.endProgress) ||
                        Float.isInfinite(item.startProgress) || Float.isInfinite(item.endProgress)) continue;
                segments.add(item);
            }
        }
        invalidate();
    }

    void updateChapters(List<Video> source, long duration) {
        chapters.clear();
        if (source != null) chapters.addAll(source);
        durationMs = duration;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (segments.isEmpty() && chapters.isEmpty()) return;
        float width = getWidth();
        // Use SeekBar track padding rather than full container width, otherwise
        // 0%/100% segments misalign with the red progress/thumb on phones.
        float startX = progress.getPaddingLeft();
        float trackWidth = width - startX - progress.getPaddingRight();
        if (trackWidth <= 0f) return;
        float h = MobileUi.dp(getContext(), 4);
        float y = getHeight() * .5f;
        for (SeekBarSegment segment : segments) {
            float start = Math.max(0f, Math.min(1f, segment.startProgress));
            float end = Math.max(0f, Math.min(1f, segment.endProgress));
            if (end <= start) continue;
            paint.setColor(segment.color);
            canvas.drawRect(startX + start * trackWidth, y - h,
                    startX + end * trackWidth, y + h, paint);
        }
        if (durationMs > 0 && !chapters.isEmpty()) {
            // Native YouTube partitions the progress track at chapter starts.
            paint.setColor(0xFFFFFFFF);
            float stroke = MobileUi.dp(getContext(), 2);
            for (Video chapter : chapters) {
                if (chapter == null || chapter.startTimeMs <= 0
                        || chapter.startTimeMs >= durationMs) continue;
                float x = startX + chapter.startTimeMs * trackWidth / durationMs;
                canvas.drawRect(x - stroke * .5f, y - h * 1.4f,
                        x + stroke * .5f, y + h * 1.4f, paint);
            }
        }
    }
}
