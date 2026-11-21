package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.Collections;
import java.util.List;

/** Foreground media service for PHONE playback only.
 * Uses the SAME MediaSessionCompat and ExoPlayer connector as PlaybackFragment.
 * Never creates a second decoder/player. Android Auto can browse one audio-only
 * Now Playing entry; video itself is not presented on the car display.
 */
public final class MobilePlaybackService extends MediaBrowserServiceCompat {
    public static final String ACTION_START = "com.sokytube.mobile.MEDIA_START";
    public static final String ACTION_PAUSE = "com.sokytube.mobile.MEDIA_PAUSE";
    public static final String ACTION_PLAY = "com.sokytube.mobile.MEDIA_PLAY";
    public static final String ACTION_NEXT = "com.sokytube.mobile.MEDIA_NEXT";
    public static final String ACTION_PREVIOUS = "com.sokytube.mobile.MEDIA_PREVIOUS";
    public static final String ACTION_STOP = "com.sokytube.mobile.MEDIA_STOP";
    private static final String CHANNEL_ID = "sokytube_mobile_media_controls_v2";
    private static final int NOTIFICATION_ID = 2037;

    private static MediaSessionCompat sharedSession;
    private static MobilePlaybackService activeService;
    private static volatile boolean explicitAudioOnly;
    private static volatile boolean carMediaBrowserConnected;

    /**
     * A media browser from Android Auto may remain connected while the car
     * pauses playback and the phone is locked. Keep the *existing* decoder
     * and MediaSessionConnector attached across Activity.onStop even if
     * ExoPlayer.playWhenReady is false.
     */
    static boolean hasCarMediaBrowserClient() {
        return carMediaBrowserConnected && activeService != null;
    }

    private static boolean isCarMediaBrowserClient(String packageName) {
        if (packageName == null) return false;
        String normalized = packageName.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("com.google.android.projection.gearhead") ||
                normalized.contains("carassistant") ||
                normalized.startsWith("com.android.car.") ||
                normalized.contains("automotive");
    }
    private static final String AUTO_ROOT = "soky_audio_root";
    private static final String AUTO_HELP = "soky_audio_phone_help";
    private static final String AUTO_NOW = "now_playing_audio";

    static void setExplicitAudioOnly(boolean allowed) {
        explicitAudioOnly = allowed;
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean foreground;
    private Boolean requestedPlaying;

    /** The session is created before the service and attached to the SAME
     * MediaSessionConnector instance owned by the existing player. */
    public static synchronized MediaSessionCompat session(Context context) {
        if (sharedSession == null) {
            sharedSession = new MediaSessionCompat(context.getApplicationContext(),
                    "SokyTubeMobile");
            sharedSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        }
        sharedSession.setActive(true);
        return sharedSession;
    }

    static synchronized void detachMediaSession(MediaSessionCompat original) {
        if (original == null || sharedSession != original) return;
        // The playback fragment owns the session. Stop any notification
        // service first, then release this exact session only on detach.
        if (activeService != null) activeService.stopForegroundAndSelf();
        sharedSession.setActive(false);
        sharedSession.release();
        sharedSession = null;
    }

    public static void start(Context context) {
        if (!com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi.isHandheld(context)) return;
        Intent intent = new Intent(context, MobilePlaybackService.class).setAction(ACTION_START);
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (RuntimeException e) {
            // Android 12+ disallows FGS launches from arbitrary background states.
            // Playback is still controlled by the existing media session.
            android.util.Log.w("MobileMedia", "Foreground service rejected", e);
        }
    }

    public static void refresh() {
        if (activeService != null) {
            activeService.refreshNotification();
            // A new video may have been selected after Android Auto opened
            // its browse hierarchy. Refresh the driver-safe audio item.
            activeService.notifyChildrenChanged(AUTO_ROOT);
        }
    }

    /** Explicitly end the floating player's notification and media service
     * after Android removes its PiP window. This is NOT used for background audio.
     */
    public static void stop(Context context) {
        explicitAudioOnly = false;
        if (activeService != null) activeService.stopForegroundAndSelf();
        else context.stopService(new Intent(context, MobilePlaybackService.class));
    }

    private static PendingIntent actionIntent(Context context, String action, int request) {
        return PendingIntent.getService(context, request,
                new Intent(context, MobilePlaybackService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onCreate() {
        super.onCreate();
        activeService = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Soky Tube playback",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Audio and background player controls");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        setSessionToken(session(this).getSessionToken());
        // Binding from Android Auto alone does not imply playback; only enter
        // foreground state when an actual phone player starts the service.
    }

    @Nullable @Override public BrowserRoot onGetRoot(@NonNull String clientPackageName,
            int clientUid, @Nullable Bundle rootHints) {
        // A driver-safe audio-only media root, with no browsing video thumbnails.
        // Detect the connected car client; it must be able to resume a
        // previously paused ExoPlayer without first waking/unlocking phone UI.
        if (isCarMediaBrowserClient(clientPackageName))
            carMediaBrowserConnected = true;
        return new BrowserRoot(AUTO_ROOT, null);
    }

    @Override public void onLoadChildren(@NonNull String parentId,
            @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (!AUTO_ROOT.equals(parentId)) {
            result.sendResult(Collections.emptyList());
            return;
        }
        MediaMetadataCompat metadata = sharedSession != null
                ? sharedSession.getController().getMetadata() : null;
        String title = metadata != null
                ? metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) : null;
        if (title == null || title.length() == 0) {
            // The old service exposed an EMPTY root unless a phone video was
            // already active. Some Android Auto hosts reject that outright.
            // Inform the driver how to initiate playback, without inventing
            // a playable title that cannot be played.
            android.support.v4.media.MediaDescriptionCompat info =
                    new android.support.v4.media.MediaDescriptionCompat.Builder()
                            .setMediaId(AUTO_HELP)
                            .setTitle("Start audio from your phone")
                            .setSubtitle("Open Soky Tube on your phone, then control audio here")
                            .build();
            result.sendResult(Collections.singletonList(new MediaBrowserCompat.MediaItem(
                    info, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)));
            return;
        }
        android.support.v4.media.MediaDescriptionCompat item =
                new android.support.v4.media.MediaDescriptionCompat.Builder()
                    .setMediaId(AUTO_NOW)
                    .setTitle(title)
                    .setSubtitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
                    .build();
        result.sendResult(Collections.singletonList(new MediaBrowserCompat.MediaItem(item,
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Bluetooth and steering-wheel media keys may arrive here from the
        // AndroidX MediaButtonReceiver rather than as notification actions.
        // Let the ONE ExoPlayer-backed session consume them directly.
        if (intent != null && Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            if (sharedSession != null) MediaButtonReceiver.handleIntent(sharedSession, intent);
            return START_NOT_STICKY;
        }
        if (!foreground) {
            try {
                startForeground(NOTIFICATION_ID, notification());
                foreground = true;
            } catch (RuntimeException failure) {
                android.util.Log.e("MobileMedia", "Foreground playback unavailable", failure);
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        if (intent != null && sharedSession != null) {
            MediaControllerCompat.TransportControls controls =
                    sharedSession.getController().getTransportControls();
            String action = intent.getAction();
            if (ACTION_NEXT.equals(action)) controls.skipToNext();
            else if (ACTION_PREVIOUS.equals(action)) controls.skipToPrevious();
            else if (ACTION_PAUSE.equals(action)) {
                requestedPlaying = false;
                controls.pause();
            } else if (ACTION_PLAY.equals(action)) {
                requestedPlaying = true;
                controls.play();
            } else if (ACTION_STOP.equals(action)) {
                controls.pause();
                stopForegroundAndSelf();
                return START_NOT_STICKY;
            }
        }
        refreshNotification();
        if (requestedPlaying != null) {
            // MediaSessionConnector publishes its new state asynchronously.
            // Update the icon on the SAME tap, then reconcile with the engine.
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(() -> {
                requestedPlaying = null;
                refreshNotification();
            }, 450);
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        MediaSessionCompat mediaSession = session(this);
        MediaControllerCompat controller = mediaSession.getController();
        MediaMetadataCompat metadata = controller.getMetadata();
        PlaybackStateCompat state = controller.getPlaybackState();
        boolean playing = requestedPlaying != null ? requestedPlaying :
                state != null && (state.getState() == PlaybackStateCompat.STATE_PLAYING
                || state.getState() == PlaybackStateCompat.STATE_BUFFERING);
        String title = metadata != null ? metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) : null;
        String artist = metadata != null ? metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) : null;
        Intent launch = new Intent(this, PlaybackActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPlayer = PendingIntent.getActivity(this, 14, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_notification)
                .setContentTitle(title == null ? "Soky Tube" : title)
                .setContentText(artist == null ? "Playing audio" : artist)
                .setContentIntent(openPlayer)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOngoing(playing)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_media_previous, "Previous",
                        actionIntent(this, ACTION_PREVIOUS, 1))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play", actionIntent(this,
                        playing ? ACTION_PAUSE : ACTION_PLAY, 2))
                .addAction(android.R.drawable.ic_media_next, "Next",
                        actionIntent(this, ACTION_NEXT, 3))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        return builder.build();
    }

    private void refreshNotification() {
        if (!foreground || sharedSession == null) return;
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            if (manager != null) manager.notify(NOTIFICATION_ID, notification());
        } catch (RuntimeException failure) {
            android.util.Log.w("MobileMedia", "Media notification unavailable", failure);
        }
    }

    private void stopForegroundAndSelf() {
        foreground = false;
        stopForeground(true);
        stopSelf();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Dismissing a floating window or removing the task must not orphan a
        // playing notification. Explicit user-requested background AUDIO is
        // the one exception, and remains available for car controls.
        if (!explicitAudioOnly) {
            if (sharedSession != null)
                sharedSession.getController().getTransportControls().pause();
            stopForegroundAndSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        foreground = false;
        carMediaBrowserConnected = false;
        activeService = null;
        // Do not release the media session here: Android may stop a service
        // while the fragment's player is still alive. Session ownership and
        // cleanup belong to detachMediaSession().
        super.onDestroy();
    }
}
