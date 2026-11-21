package com.liskovsoft.smartyoutubetv2.tv.ui.car;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Surface;
import android.util.Log;

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
 * Static SurfaceCallback experiment. No video, audio, internet, stream URL,
 * media decoder, mirroring, or car-state spoofing is used.
 *
 * A working PaneTemplate proves CarAppService discovery but NOT that this
 * head unit exposes a drawing Surface. The full-screen test pattern below
 * distinguishes the two capabilities.
 *
 * CAUTION: NavigationTemplate Surface is officially meant for map rendering.
 * It is not approval for a YouTube video app. Do NOT use the surface for
 * video or publish this probe as a navigation app.
 */
final class SokyCarSurfaceProbeScreen extends Screen implements SurfaceCallback {
    private static final String TAG = "SokyCarSurfaceProbe";
    private Surface currentSurface;
    private Rect visibleArea;
    private String status = "Waiting for Android Auto surface";

    SokyCarSurfaceProbeScreen(@NonNull CarContext context) {
        super(context);
        AppManager manager = context.getCarService(AppManager.class);
        manager.setSurfaceCallback(this);
        getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) {
                manager.setSurfaceCallback(null);
                currentSurface = null;
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
        Log.i(TAG, "Car Surface available: " + container.getWidth()
                + " x " + container.getHeight());
        status = "CAR SURFACE AVAILABLE";
        drawDiagnosticFrame();
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect visibleArea) {
        this.visibleArea = new Rect(visibleArea);
        drawDiagnosticFrame();
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect stableArea) {
        if (visibleArea == null) visibleArea = new Rect(stableArea);
        drawDiagnosticFrame();
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer container) {
        Log.i(TAG, "Car Surface destroyed");
        currentSurface = null;
    }

    private void drawDiagnosticFrame() {
        Surface surface = currentSurface;
        if (surface == null || !surface.isValid()) return;
        Canvas canvas = null;
        try {
            canvas = surface.lockCanvas(null);
            canvas.drawColor(Color.rgb(12, 19, 33));

            int width = canvas.getWidth();
            int height = canvas.getHeight();
            Rect area = visibleArea;
            float cx = area == null ? width / 2f : area.centerX();
            float cy = area == null ? height / 2f : area.centerY();
            float minDimension = Math.max(1f, Math.min(width, height));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(18, 199, 157));
            canvas.drawRoundRect(cx - minDimension * .20f, cy - minDimension * .29f,
                    cx + minDimension * .20f, cy - minDimension * .12f,
                    minDimension * .03f, minDimension * .03f, paint);

            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif", android.graphics.Typeface.BOLD));
            paint.setTextSize(Math.max(18f, minDimension * .050f));
            canvas.drawText(status, cx, cy - minDimension * .01f, paint);

            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(Math.max(15f, minDimension * .033f));
            canvas.drawText("Soky Tube native rendering test", cx,
                    cy + minDimension * .08f, paint);
            canvas.drawText("No video enabled", cx,
                    cy + minDimension * .15f, paint);
            canvas.drawText(String.format(Locale.US,
                    "Surface: %d x %d", width, height),
                    cx, cy + minDimension * .22f, paint);
        } catch (RuntimeException error) {
            Log.e(TAG, "Android Auto Surface canvas was not drawable", error);
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas);
                } catch (RuntimeException error) {
                    Log.e(TAG, "Surface post failed", error);
                }
            }
        }
    }
}
