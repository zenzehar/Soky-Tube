package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

/**
 * YouTube-style tap-seek effect drawn OVER the actual movie but UNDER no
 * additional touch handlers. A soft, curved translucent edge shade, three
 * staggered animated chevrons and the number of seconds skipped.
 */
final class MobileDoubleTapSeekFeedback extends View {
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevrons = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final RectF edge = new RectF();
    private final float density;
    private final float scaledDensity;
    private ValueAnimator animator;
    private int direction = MobileDoubleTapSeekPolicy.FORWARD;
    private int count = 1;
    private long lastFeedbackAt;
    private float phase;

    MobileDoubleTapSeekFeedback(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(INVISIBLE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        chevrons.setColor(Color.WHITE);
        chevrons.setStyle(Paint.Style.STROKE);
        chevrons.setStrokeCap(Paint.Cap.ROUND);
        chevrons.setStrokeJoin(Paint.Join.ROUND);
        chevrons.setStrokeWidth(dp(3.5f));
        label.setColor(Color.WHITE);
        label.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        label.setTextSize(14f * scaledDensity);
        label.setTextAlign(Paint.Align.CENTER);
        label.setShadowLayer(dp(3f), 0, dp(1f), 0xC0000000);
    }

    private float dp(float number) { return number * density; }

    void show(int side) {
        long now = SystemClock.uptimeMillis();
        if (direction == side && now - lastFeedbackAt < 900L) count++;
        else count = 1;
        direction = side;
        lastFeedbackAt = now;
        if (animator != null) animator.cancel();
        phase = 0f;
        setVisibility(VISIBLE);
        invalidate();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(680L);
        animator.setInterpolator(new DecelerateInterpolator(.8f));
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator == animation) {
                    setVisibility(INVISIBLE);
                    animator = null;
                }
            }
        });
        animator.start();
    }

    void release() {
        if (animator != null) animator.cancel();
        animator = null;
        setVisibility(INVISIBLE);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0f || h <= 0f) return;
        final float opacity = Math.max(0f, Math.min(1f, 1.0f - phase));
        final boolean backward = direction == MobileDoubleTapSeekPolicy.BACKWARD;

        // Soft oval vignette clipped to the tapped half of the whole VIDEO,
        // not a square overlay that obscures the centre controls.
        canvas.save();
        if (backward) canvas.clipRect(0, 0, w * .50f, h);
        else canvas.clipRect(w * .50f, 0, w, h);
        if (backward) edge.set(-w * .68f, -h * .52f, w * .72f, h * 1.52f);
        else edge.set(w * .28f, -h * .52f, w * 1.68f, h * 1.52f);
        shade.setColor(0xFF000000);
        shade.setAlpha((int) (100f * opacity));
        canvas.drawOval(edge, shade);
        canvas.restore();

        float mid = backward ? w * .235f : w * .765f;
        float centerY = h * .48f;
        float arrowSize = Math.max(dp(13f), Math.min(dp(20f), h * .105f));
        float spacing = arrowSize * .81f;
        for (int i = 0; i < 3; i++) {
            float x = mid + (i - 1) * spacing;
            float stagger = (backward ? 2 - i : i) * .10f;
            float appear = Math.min(1f, Math.max(0f, (phase - stagger) * 7f));
            chevrons.setAlpha((int) (230f * opacity * (.45f + .55f * appear)));
            arrow.reset();
            if (backward) {
                arrow.moveTo(x + arrowSize * .35f, centerY - arrowSize * .55f);
                arrow.lineTo(x - arrowSize * .35f, centerY);
                arrow.lineTo(x + arrowSize * .35f, centerY + arrowSize * .55f);
            } else {
                arrow.moveTo(x - arrowSize * .35f, centerY - arrowSize * .55f);
                arrow.lineTo(x + arrowSize * .35f, centerY);
                arrow.lineTo(x - arrowSize * .35f, centerY + arrowSize * .55f);
            }
            canvas.drawPath(arrow, chevrons);
        }
        label.setAlpha((int) (255f * opacity));
        canvas.drawText(String.format(Locale.US, "%d seconds",
                        count * (int) (MobileDoubleTapSeekPolicy.STEP_MS / 1000L)),
                mid, centerY + arrowSize * 1.5f + dp(13f), label);
    }
}
