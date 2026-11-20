package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.view.SurfaceHolder;
import android.view.View;

public interface SurfaceWrapper {
    int SURFACE_NOT_CREATED = 0;
    int SURFACE_CREATED = 1;
    void setSurfaceHolderCallback(SurfaceHolder.Callback callback);
    /** Re-notify the original Leanback/ExoPlayer surface callback if the
     * backing Android surface is actually available. The wrapper remains the
     * sole lifecycle owner; callers must not replace TextureView listeners.
     */
    boolean refreshSurfaceBinding();
    /** Uptime of an actual newly composited TextureView frame (0 if none).
     * SurfaceView does not expose this signal through the wrapper.
     */
    long lastVideoFrameUptimeMs();
    View getSurfaceView();
}
