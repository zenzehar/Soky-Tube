package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileAudioTrackPolicyTest {
    @Test public void originalAudioExplicitlyLabelledNotGuessedFromUserLocale() {
        assertTrue(MobileAudioTrackPolicy.original("ar-original", "Original", "140"));
        assertFalse(MobileAudioTrackPolicy.original("en", "English", "140"));
        assertFalse(MobileAudioTrackPolicy.original("en-dubbed", "Dubbed", "141"));
    }

    @Test public void qualityVariantsOfOneLanguageShareTheSameGroup() {
        assertEquals(MobileAudioTrackPolicy.groupKey("en", "English", "mp4a-96"),
                MobileAudioTrackPolicy.groupKey("en", "English", "opus-160"));
    }

    @Test public void dubbedAndOriginalStaySeparateWithinSameLanguage() {
        assertNotEquals(MobileAudioTrackPolicy.groupKey("en-original", "", "one"),
                MobileAudioTrackPolicy.groupKey("en-dubbed", "", "two"));
    }

    @Test public void descriptiveNeverSelectedAsOriginal() {
        assertFalse(MobileAudioTrackPolicy.original("en", "Original audio description", "135"));
        assertTrue(MobileAudioTrackPolicy.descriptive("en", "Audio description", "135"));
    }

    @Test public void unknownLanguageDoesNotBecomeEnglishAutomatically() {
        assertEquals("Unspecified language",
                MobileAudioTrackPolicy.displayLanguage(null, null, "test"));
    }
}
