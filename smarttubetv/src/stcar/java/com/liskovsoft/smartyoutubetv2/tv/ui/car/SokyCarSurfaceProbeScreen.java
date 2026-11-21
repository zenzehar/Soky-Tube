package com.liskovsoft.smartyoutubetv2.tv.ui.car;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import java.util.Locale;

/**
 * Diagnostic-only drawing test on the surface confirmed to work at 1202x462.
 *
 * This is NOT a video output or a navigation product. NavigationTemplate's
 * surface is reserved for map graphics by Android Auto app quality policy.
 * No media decoder or unverified parked-state inference is connected here.
 */
final class SokyCarSurfaceProbeScreen extends Screen implements SurfaceCallback {
    private static final String TAG = "SokyCarSurfaceProbe";
    private final AppManager appManager;
    private Surface currentSurface;
    private Rect visibleArea;
    private Rect stableArea;
    private String status = "Waiting for Android Auto surface";

    SokyCarSurfaceProbeScreen(@NonNull CarContext context) {
        super(context);
        appManager = context.getCarService(AppManager.class);
        getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_START) {
                // The screen owns the callback only while it is being shown.
                appManager.setSurfaceCallback(this);
            } else if (event == Lifecycle.Event.ON_STOP ||
                    event == Lifecycle.Event.ON_DESTROY) {
                appManager.setSurfaceCallback(null);
                currentSurface = null;  // Host owns and releases its Surface.
                visibleArea = null;
                stableArea = null;
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ActionStrip strip = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("BACK")
                        .setOnClickListener(this::finish)
                        .build())
                .build();
        return new NavigationTemplate.Builder()
                .setActionStrip(strip)
                .build();
    }

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer container) {
        currentSurface = container.getSurface();
        visibleArea = null;
        stableArea = null;
        status = "CAR SURFACE AVAILABLE";
        Log.i(TAG, "Diagnostic Surface available: " + container.getWidth()
                + " x " + container.getHeight() + "; video gate blocked");
        drawDiagnosticFrame();
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect area) {
        visibleArea = new Rect(area);
        drawDiagnosticFrame();
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect area) {
        stableArea = new Rect(area);
        drawDiagnosticFrame();
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer container) {
        Log.i(TAG, "Diagnostic Surface destroyed");
        if (currentSurface == container.getSurface()) {
            currentSurface = null;
            status = "Surface disconnected";
        }
    }

    private void drawDiagnosticFrame() {
        Surface surface = currentSurface;
        if (surface == null || !surface.isValid()) {
            return;
        }
        Canvas canvas = null;
        try {
            canvas = surface.lockCanvas(null);
            canvas.drawColor(Color.rgb(12, 19, 33));

            int width = canvas.getWidth();
            int height = canvas.getHeight();
            Rect bounds = new Rect(0, 0, width, height);
            Rect preferred = visibleArea != null ? visibleArea : stableArea;
            if (preferred != null && !bounds.intersect(preferred)) {
                bounds.set(0, 0, width, height);
            }
            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            float unit = Math.max(1f, Math.min(bounds.width(), bounds.height()));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(18, 199, 157));
            canvas.drawRoundRect(cx - unit * .19f, cy - unit * .32f,
                    cx + unit * .19f, cy - unit * .19f,
                    unit * .025f, unit * .025f, paint);

            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif", android.graphics.Typeface.BOLD));
            paint.setTextSize(Math.max(18f, unit * .046f));
            canvas.drawText(status, cx, cy - unit * .07f, paint);

            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(Math.max(15f, unit * .032f));
            canvas.drawText("Soky Car native surface diagnostics", cx,
                    cy + unit * .015f, paint);
            canvas.drawText(String.format(Locale.US,
                    "Surface: %d x %d | Android API: %d",
                    width, height, Build.VERSION.SDK_INT),
                    cx, cy + unit * .095f, paint);

            // Every frame explicitly exposes fail-closed video state.
            // Even a working Surface is not parked-video authorisation.
            paint.setColor(Color.rgb(255, 189, 124));
            canvas.drawText(SokyCarPlaybackGate.diagnosticState(),
                    cx, cy + unit * .18f, paint);
            paint.setTextSize(Math.max(13f, unit * .027f));
            canvas.drawText(SokyCarPlaybackGate.blockReason(),
                    cx, cy + unit * .25f, paint);
        } catch (RuntimeException error) {
            Log.e(TAG, "Android Auto diagnostic Surface not drawable", error);
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas);
                } catch (RuntimeException error) {
                    Log.e(TAG, "Diagnostic Surface post failed", error);
                }
            }
        }
    }
}
