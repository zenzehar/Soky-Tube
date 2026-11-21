package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/** Only stores the last selected (non-incognito) video for explicit in-car resume. */
final class MobileCarResumeState {
    private static final String PREFS = "soky_car_last_session";
    final String videoId;
    final String title;
    final String artist;
    final long positionMs;

    private MobileCarResumeState(String id, String title, String artist, long position) {
        this.videoId = id;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.positionMs = Math.max(0, position);
    }

    static boolean validVideoId(String id) {
        // Only YouTube video IDs; never accept arbitrary network URLs, content
        // URIs, user input or filesystem references in the car resume path.
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }

    static MobileCarResumeState get(Context context) {
        SharedPreferences data = context.getApplicationContext().getSharedPreferences(PREFS,
                Context.MODE_PRIVATE);
        String id = data.getString("video", "");
        if (!validVideoId(id)) return null;
        return new MobileCarResumeState(id, data.getString("title", ""),
                data.getString("artist", ""), data.getLong("position", 0));
    }

    static void save(Context context, String videoId, String title,
            String artist, long positionMs, boolean incognito) {
        if (incognito || !validVideoId(videoId)) return;
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("video", videoId)
                .putString("title", title == null ? "" : title)
                .putString("artist", artist == null ? "" : artist)
                .putLong("position", Math.max(0, positionMs)).apply();
    }

    static void updatePosition(Context context, String videoId, long positionMs) {
        if (!validVideoId(videoId) || positionMs < 0) return;
        SharedPreferences data = context.getApplicationContext().getSharedPreferences(PREFS,
                Context.MODE_PRIVATE);
        if (!TextUtils.equals(data.getString("video", ""), videoId)) return;
        data.edit().putLong("position", positionMs).apply();
    }

    static void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS,
                Context.MODE_PRIVATE).edit().clear().apply();
    }
}
