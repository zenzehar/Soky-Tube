package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.app.PendingIntent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MobileMediaButtonIntentPolicyTest {
    @Test public void android12ThroughAndroid14UseImmutableFlag() {
        int expected = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        assertEquals(expected, MobileMediaButtonIntentPolicy.flags(31));
        assertEquals(expected, MobileMediaButtonIntentPolicy.flags(33));
        assertEquals(expected, MobileMediaButtonIntentPolicy.flags(34));
        assertEquals(expected, MobileMediaButtonIntentPolicy.flags(35));
    }

    @Test public void earlierAndroidRetainsCompatibleFlagBehavior() {
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, MobileMediaButtonIntentPolicy.flags(17));
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, MobileMediaButtonIntentPolicy.flags(22));
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                MobileMediaButtonIntentPolicy.flags(23));
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                MobileMediaButtonIntentPolicy.flags(30));
    }

    @Test public void mediaButtonPendingIntentIsNeverMutable() {
        for (int sdk = 17; sdk <= 36; sdk++) {
            assertEquals(0, MobileMediaButtonIntentPolicy.flags(sdk) & PendingIntent.FLAG_MUTABLE);
        }
    }
}
