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
    private static final String CHANNEL_ID = "sokytube_mobile_media";
    private static final int NOTIFICATION_ID = 2037;

    private static MediaSessionCompat sharedSession;
    private static MobilePlaybackService activeService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean foreground;

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
        if (activeService != null) activeService.stopForegroundAndSelf();
        else {
            sharedSession.setActive(false);
            sharedSession.release();
            sharedSession = null;
        }
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
        if (activeService != null) activeService.refreshNotification();
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
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        setSessionToken(session(this).getSessionToken());
        // Promote immediately after a foreground service start to meet the
        // Android 8+ 5-second deadline.
        startForeground(NOTIFICATION_ID, notification());
        foreground = true;
    }

    @Nullable @Override public BrowserRoot onGetRoot(@NonNull String clientPackageName,
            int clientUid, @Nullable Bundle rootHints) {
        // A driver-safe audio-only media root, with no browsing video thumbnails.
        return new BrowserRoot("soky_audio_root", null);
    }

    @Override public void onLoadChildren(@NonNull String parentId,
            @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (!"soky_audio_root".equals(parentId) || sharedSession == null) {
            result.sendResult(Collections.emptyList());
            return;
        }
        MediaMetadataCompat metadata = sharedSession.getController().getMetadata();
        if (metadata == null || metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) == null) {
            result.sendResult(Collections.emptyList());
            return;
        }
        android.support.v4.media.MediaDescriptionCompat item =
                new android.support.v4.media.MediaDescriptionCompat.Builder()
                    .setMediaId("now_playing_audio")
                    .setTitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                    .setSubtitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
                    .build();
        result.sendResult(Collections.singletonList(new MediaBrowserCompat.MediaItem(item,
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foreground) {
            startForeground(NOTIFICATION_ID, notification());
            foreground = true;
        }
        if (intent != null && sharedSession != null) {
            MediaControllerCompat.TransportControls controls =
                    sharedSession.getController().getTransportControls();
            String action = intent.getAction();
            if (ACTION_NEXT.equals(action)) controls.skipToNext();
            else if (ACTION_PREVIOUS.equals(action)) controls.skipToPrevious();
            else if (ACTION_PAUSE.equals(action)) controls.pause();
            else if (ACTION_PLAY.equals(action)) controls.play();
            else if (ACTION_STOP.equals(action)) {
                controls.pause();
                stopForegroundAndSelf();
                return START_NOT_STICKY;
            }
        }
        refreshNotification();
        return START_NOT_STICKY;
    }

    private Notification notification() {
        MediaSessionCompat mediaSession = session(this);
        MediaControllerCompat controller = mediaSession.getController();
        MediaMetadataCompat metadata = controller.getMetadata();
        PlaybackStateCompat state = controller.getPlaybackState();
        boolean playing = state != null && (state.getState() == PlaybackStateCompat.STATE_PLAYING
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
        if (!foreground) return;
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification());
    }

    private void stopForegroundAndSelf() {
        foreground = false;
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        foreground = false;
        activeService = null;
        synchronized (MobilePlaybackService.class) {
            if (sharedSession != null) {
                sharedSession.setActive(false);
                sharedSession.release();
                sharedSession = null;
            }
        }
        super.onDestroy();
    }
}
