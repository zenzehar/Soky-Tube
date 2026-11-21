package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * A service-owned, AUDIO-ONLY fallback used when Android Auto sends Play after
 * the phone Activity/fragment has already been destroyed. Never constructs a
 * playback Activity, never displays video, and starts ONLY on explicit Play.
 *
 * When the phone player attaches, this engine is released before the Activity
 * connects its own MediaSessionConnector. At most one decoder owns the session.
 */
final class MobileCarAudioEngine {
    private static final String TAG = "MobileCarAudioEngine";
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaSessionCompat session;
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            if (player == null || current == null) return;
            MobileCarResumeState.updatePosition(context, current.videoId,
                    player.getCurrentPosition());
            main.postDelayed(this, 8_000);
        }
    };
    private MediaSessionConnector connector;
    private ExoPlayerInitializer initializer;
    private SimpleExoPlayer player;
    private Disposable formatLookup;
    private MobileCarResumeState current;
    private int generation;
    private boolean released;
    private boolean loading;

    MobileCarAudioEngine(Context context, MediaSessionCompat session) {
        this.context = context.getApplicationContext();
        this.session = session;
    }

    boolean hasPreparedPlayer() { return player != null; }

    /** Called on main thread following a real Play action from the car. */
    boolean resume(MobileCarResumeState resume) {
        if (released || resume == null) return false;
        if (player != null) {
            player.setPlayWhenReady(true);
            return true;
        }
        if (loading) return true;
        current = resume;
        loading = true;
        final int request = ++generation;
        // Resolve a NEW authorized stream URL. YouTube stream URLs expire;
        // persisting one would break after the phone has been idle for hours.
        formatLookup = YouTubeServiceManager.instance().getMediaItemService()
                .getFormatInfoObserve(resume.videoId)
                .take(1)
                .subscribeOn(Schedulers.io())
                .subscribe(info -> main.post(() -> {
                    if (!released && generation == request) prepare(info, request);
                }), error -> main.post(() -> fail(request, error.getMessage())));
        return true;
    }

    private void prepare(MediaItemFormatInfo info, int request) {
        if (released || request != generation || current == null) return;
        if (info == null || info.isUnplayable()) {
            fail(request, "The selected video is unavailable");
            return;
        }
        try {
            ExoMediaSourceFactory factory = new ExoMediaSourceFactory(context);
            MediaSource source;
            if (info.containsDashFormats()) source = factory.fromDashFormatInfo(info);
            else if (info.containsSabrFormats()) source = factory.fromSabrFormatInfo(info);
            else if (info.isLive() && info.containsDashUrl())
                source = factory.fromDashManifestUrl(info.getDashManifestUrl());
            else if (info.containsHlsUrl())
                source = factory.fromHlsPlaylist(info.getHlsManifestUrl());
            else if (info.containsUrlFormats() && !info.createUrlList().isEmpty())
                source = factory.fromUrlList(info.createUrlList());
            else {
                fail(request, "No supported audio stream was returned");
                return;
            }
            DefaultTrackSelector tracks = new RestoreTrackSelector(
                    new AdaptiveTrackSelection.Factory());
            // Video renderer 0 must never decode or display pictures in the car.
            tracks.setParameters(tracks.buildUponParameters().setRendererDisabled(0, true));
            initializer = new ExoPlayerInitializer(context);
            player = initializer.createPlayer(context, new DefaultRenderersFactory(context), tracks);
            player.setPlayWhenReady(false);
            player.addListener(new Player.EventListener() {
                @Override public void onPlayerError(ExoPlaybackException exception) {
                    fail(generation, exception.getMessage());
                }
            });
            connector = new MediaSessionConnector(session);
            connector.setMediaMetadataProvider(p -> metadata(current));
            connector.setPlayer(player);
            connector.setCustomActionProviders(
                    customSeek(MobilePlaybackService.ACTION_SEEK_BACK_10,
                            "Back 10 seconds", R.drawable.mobile_ic_back_10, false),
                    customSeek(MobilePlaybackService.ACTION_SEEK_FORWARD_10,
                            "Forward 10 seconds", R.drawable.mobile_ic_forward_10, true));
            if (current.positionMs > 0) player.seekTo(current.positionMs);
            player.prepare(source);
            player.setPlayWhenReady(true);
            loading = false;
            main.removeCallbacks(checkpoint);
            main.postDelayed(checkpoint, 8_000);
        } catch (RuntimeException e) {
            fail(request, e.getMessage());
        }
    }

    private MediaSessionConnector.CustomActionProvider customSeek(
            String actionName, String title, int icon, boolean forward) {
        return new MediaSessionConnector.CustomActionProvider() {
            @Override public PlaybackStateCompat.CustomAction getCustomAction(Player exo) {
                if (exo == null || !exo.isCurrentWindowSeekable()) return null;
                return new PlaybackStateCompat.CustomAction.Builder(
                        actionName, title, icon).build();
            }
            @Override public void onCustomAction(Player exo, ControlDispatcher dispatcher,
                    String action, Bundle extras) {
                if (!actionName.equals(action) || exo == null) return;
                long target = MobileCarSeekPolicy.targetMs(
                        exo.getCurrentPosition(), exo.getDuration(),
                        exo.isCurrentWindowSeekable(), forward);
                if (target >= 0) dispatcher.dispatchSeekTo(
                        exo, exo.getCurrentWindowIndex(), target);
            }
        };
    }

    private MediaMetadataCompat metadata(MobileCarResumeState state) {
        return new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, state.videoId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.artist)
                .build();
    }

    private void fail(int request, String why) {
        if (released || generation != request) return;
        Log.e(TAG, "Audio-only car resume failed: " + why);
        releasePlayer();
        loading = false;
        session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_ERROR, 0, 0)
                .setErrorMessage("Unable to resume audio; choose another video on your phone")
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
                .build());
    }

    void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
            if (current != null)
                MobileCarResumeState.updatePosition(context, current.videoId,
                        player.getCurrentPosition());
        }
    }

    /** Release on foreground phone-player takeover or service destruction. */
    void release() {
        released = true;
        generation++;
        main.removeCallbacks(checkpoint);
        if (formatLookup != null) formatLookup.dispose();
        releasePlayer();
        loading = false;
    }

    private void releasePlayer() {
        if (connector != null) {
            connector.setPlayer(null);
            connector = null;
        }
        if (player != null) {
            if (current != null) MobileCarResumeState.updatePosition(context,
                    current.videoId, player.getCurrentPosition());
            player.release();
            player = null;
        }
        if (initializer != null) {
            initializer.release();
            initializer = null;
        }
    }
}
