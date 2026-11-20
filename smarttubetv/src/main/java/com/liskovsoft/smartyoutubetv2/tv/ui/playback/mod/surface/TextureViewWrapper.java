package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.ViewGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class TextureViewWrapper implements SurfaceWrapper {
    private int mState = SURFACE_NOT_CREATED;
    private final TextureView mVideoSurface;
    private SurfaceHolder.Callback mMediaPlaybackCallback;
    /** A single Surface lives for the entire SurfaceTexture lifetime. */
    private Surface mTextureSurface;

    @SuppressLint("WrongConstant")
    public TextureViewWrapper(Context context, ViewGroup root) {
        mVideoSurface = (TextureView) LayoutInflater.from(context).inflate(
                R.layout.lb_video_texture, root, false);
        mVideoSurface.setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (mTextureSurface != null) mTextureSurface.release();
                mTextureSurface = new Surface(surface);
                mState = SURFACE_CREATED;
                if (mMediaPlaybackCallback != null) {
                    mMediaPlaybackCallback.surfaceCreated(
                            new TextureViewSurfaceHolder(mTextureSurface));
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                if (mMediaPlaybackCallback != null && mTextureSurface != null) {
                    mMediaPlaybackCallback.surfaceChanged(
                            new TextureViewSurfaceHolder(mTextureSurface), 4, width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                // Clear the decoder output BEFORE releasing the native Surface.
                // Otherwise the next fullscreen resize can decode into a
                // surface Android has already destroyed.
                mState = SURFACE_NOT_CREATED;
                if (mMediaPlaybackCallback != null && mTextureSurface != null) {
                    mMediaPlaybackCallback.surfaceDestroyed(
                            new TextureViewSurfaceHolder(mTextureSurface));
                }
                if (mTextureSurface != null) {
                    mTextureSurface.release();
                    mTextureSurface = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * Adds {@link SurfaceHolder.Callback} to {@link SurfaceView}.
     */
    public void setSurfaceHolderCallback(SurfaceHolder.Callback callback) {
        mMediaPlaybackCallback = callback;

        refreshSurfaceBinding();
    }

    @Override
    public boolean refreshSurfaceBinding() {
        if (mState != SURFACE_CREATED || mTextureSurface == null ||
                !mTextureSurface.isValid() || !mVideoSurface.isAvailable() ||
                !mVideoSurface.isAttachedToWindow() || mMediaPlaybackCallback == null)
            return false;
        // Replay the original Leanback surfaceCreated event: it is already
        // wired to the ExoPlayer decoder and keeps the TextureView's one
        // SurfaceTextureListener intact. Do not use setVideoTextureView here.
        mMediaPlaybackCallback.surfaceCreated(
                new TextureViewSurfaceHolder(mTextureSurface));
        return true;
    }

    @Override
    public View getSurfaceView() {
        return mVideoSurface;
    }
}
