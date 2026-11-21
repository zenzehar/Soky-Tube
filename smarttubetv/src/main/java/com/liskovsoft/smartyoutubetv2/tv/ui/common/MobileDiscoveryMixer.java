package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * General-purpose discovery ranking, never a news-only feed.
 * Invoked ONLY by an initial user open or deliberate pull-to-refresh.
 * Recommendation signals remain on-device.
 */
public final class MobileDiscoveryMixer {
    public static final int FOR_YOU = 0;
    public static final int SUBSCRIPTIONS = 1;
    public static final int EXPLORE = 2;

    public static final class Item<T> {
        public final String id, title, channel;
        public final int source;
        public final boolean subscribed;
        public final T value;
        public Item(String id, String title, String channel,
                int source, boolean subscribed, T value) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.channel = channel == null ? "" : channel;
            this.source = source;
            this.subscribed = subscribed;
            this.value = value;
        }
    }

    private MobileDiscoveryMixer() {}

    /** 60% Home recommendations, 25% subscriptions, 15% broad exploration.
     * Fallbacks fill empty source slots; we never fabricate unavailable videos.
     */
    public static <T> List<Item<T>> mix(List<Item<T>> recommendations,
            List<Item<T>> subscriptions, List<Item<T>> explore,
            List<Item<T>> watchHistory, Set<String> recentTop,
            long seed, int maximum) {
        ArrayList<Item<T>> result = new ArrayList<>();
        if (maximum <= 0) return result;
        LinkedHashMap<String, Item<T>> unique = new LinkedHashMap<>();
        // Prefer the subscription relationship when duplicates appear.
        addUnique(unique, subscriptions);
        addUnique(unique, recommendations);
        addUnique(unique, explore);
        ArrayList<Item<T>> pool = new ArrayList<>(unique.values());

        Map<String, Integer> interests = new HashMap<>();
        Set<String> watchedChannels = new HashSet<>();
        if (watchHistory != null) {
            for (int i = Math.max(0, watchHistory.size() - 45); i < watchHistory.size(); i++) {
                Item<T> watched = watchHistory.get(i);
                if (watched == null) continue;
                if (!watched.channel.isEmpty()) watchedChannels.add(watched.channel);
                for (String word : tokenize(watched.title)) {
                    Integer count = interests.get(word);
                    interests.put(word, Math.min(8, count == null ? 1 : count + 1));
                }
            }
        }

        Random random = new Random(seed);
        Set<String> used = new HashSet<>();
        Set<String> prior = recentTop != null ? recentTop : new HashSet<>();
        Map<String, Integer> byChannel = new HashMap<>();
        int limit = Math.min(maximum, pool.size());
        for (int position = 0; position < limit; position++) {
            int slot = position % 20;
            int preferred = slot < 12 ? FOR_YOU : slot < 17 ? SUBSCRIPTIONS : EXPLORE;
            Item<T> best = choose(pool, used, prior, byChannel, interests,
                    watchedChannels, preferred, random, true);
            if (best == null) best = choose(pool, used, prior, byChannel, interests,
                    watchedChannels, preferred, random, false);
            if (best == null) break;
            result.add(best);
            used.add(best.id);
            if (!best.channel.isEmpty())
                byChannel.put(best.channel, byChannel.containsKey(best.channel)
                        ? byChannel.get(best.channel) + 1 : 1);
        }
        return result;
    }

    private static <T> Item<T> choose(List<Item<T>> pool, Set<String> used,
            Set<String> prior, Map<String, Integer> byChannel,
            Map<String, Integer> interests, Set<String> watchedChannels,
            int preferred, Random random, boolean onlyPreferred) {
        double top = Double.NEGATIVE_INFINITY;
        Item<T> selection = null;
        for (Item<T> candidate : pool) {
            if (used.contains(candidate.id)) continue;
            if (onlyPreferred && candidate.source != preferred) continue;
            double score = random.nextDouble() * 4.5;
            if (prior.contains(candidate.id)) score -= 6.0;
            if (candidate.subscribed) score += 1.6;
            if (watchedChannels.contains(candidate.channel)) score += 1.3;
            if (!candidate.channel.isEmpty() && byChannel.containsKey(candidate.channel))
                score -= byChannel.get(candidate.channel) * 1.7;
            for (String word : tokenize(candidate.title)) {
                Integer frequency = interests.get(word);
                if (frequency != null) score += Math.min(0.35, 0.09 * frequency);
            }
            if (score > top) {
                top = score;
                selection = candidate;
            }
        }
        return selection;
    }

    private static <T> void addUnique(Map<String, Item<T>> unique, List<Item<T>> source) {
        if (source == null) return;
        for (Item<T> item : source) {
            if (item != null && item.id != null && !item.id.isEmpty() &&
                    !unique.containsKey(item.id)) unique.put(item.id, item);
        }
    }

    private static Set<String> tokenize(String title) {
        Set<String> tokens = new HashSet<>();
        if (title == null) return tokens;
        for (String word : title.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+")) {
            if (word.length() >= 4 && word.length() <= 28 &&
                    !word.equals("video") && !word.equals("watch") &&
                    !word.equals("the") && !word.equals("with") &&
                    !word.equals("that") && !word.equals("from") &&
                    !word.equals("latest")) tokens.add(word);
        }
        return tokens;
    }
}
