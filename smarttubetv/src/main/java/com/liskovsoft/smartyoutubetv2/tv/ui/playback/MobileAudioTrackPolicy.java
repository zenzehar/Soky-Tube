package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import java.util.Locale;

/**
 * Mobile-only metadata classification. Language/role and codec/bitrate are
 * independent dimensions. Never guess that English is the original language:
 * the video publisher/provider must explicitly mark an original track.
 */
final class MobileAudioTrackPolicy {
    private MobileAudioTrackPolicy() {}

    private static String normal(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    static boolean original(String language, String label, String formatId) {
        return contains(originalFields(language, label, formatId), "original") &&
                !descriptive(language, label, formatId) &&
                !dubbed(language, label, formatId);
    }

    static boolean dubbed(String language, String label, String formatId) {
        return contains(originalFields(language, label, formatId), "dubbed") ||
                contains(originalFields(language, label, formatId), "translation");
    }

    static boolean descriptive(String language, String label, String formatId) {
        String s = originalFields(language, label, formatId);
        return s.contains("descriptive") || s.contains("description") ||
                s.contains("commentary");
    }

    private static boolean contains(String value, String keyword) {
        return value.contains(keyword);
    }

    private static String originalFields(String language, String label, String formatId) {
        return normal(language) + " " + normal(label) + " " + normal(formatId);
    }

    static String languageCode(String language) {
        String code = normal(language);
        if (code.isEmpty() || code.equals("und") || code.equals("null"))
            return "und";
        // YouTube can encode variants as e.g. en-original / en-dubbed.
        int variant = code.indexOf('-');
        if (variant > 0) {
            String tail = code.substring(variant + 1);
            if (tail.startsWith("original") || tail.startsWith("dubbed") ||
                    tail.startsWith("descriptive") || tail.startsWith("description"))
                code = code.substring(0, variant);
        }
        return code;
    }

    static String groupKey(String language, String label, String formatId) {
        String variant = original(language, label, formatId) ? "original" :
                descriptive(language, label, formatId) ? "descriptive" :
                        dubbed(language, label, formatId) ? "dubbed" : "regular";
        return languageCode(language) + "|" + variant;
    }

    static String displayLanguage(String language, String label, String formatId) {
        String code = languageCode(language);
        String display;
        if ("und".equals(code)) {
            display = "Unspecified language";
        } else {
            Locale locale = Locale.forLanguageTag(code);
            display = locale.getDisplayLanguage(Locale.getDefault());
            if (display == null || display.trim().isEmpty() ||
                    "und".equalsIgnoreCase(display)) display = code;
        }
        if (original(language, label, formatId))
            return "Original audio" + ("und".equals(code) ? "" : " · " + display);
        if (descriptive(language, label, formatId))
            return display + " · Audio description";
        if (dubbed(language, label, formatId))
            return display + " · Dubbed";
        return display;
    }
}
