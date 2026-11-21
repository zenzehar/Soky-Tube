package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.app.PendingIntent;
import android.os.Build;

/** Mutability policy for the phone's MediaSessionCompat media-button receiver.
 * This is a broadcast-only system media control; the caller must never change
 * the target intent, so FLAG_IMMUTABLE is correct on Android 6.0 and newer.
 */
final class MobileMediaButtonIntentPolicy {
    private MobileMediaButtonIntentPolicy() {}

    static int flags(int sdkInt) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (sdkInt >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
