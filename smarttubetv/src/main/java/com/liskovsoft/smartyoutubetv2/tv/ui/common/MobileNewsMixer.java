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

/** On-device ranking for the mobile NEWS section only.
 * No new account data leaves the device: source videos already come from the
 * existing signed-in YouTube content service and watch-history store.
 */
public final class MobileNewsMixer {
    public static final int NEWS = 0, SUBSCRIPTIONS = 1, RECOMMENDATIONS = 2;

    public static final class Item<T> {
        public final String id, title, author, channel;
        public final int source;
        public final boolean subscribed;
        public final T value;

        public Item(String id, String title, String author, String channel,
                int source, boolean subscribed, T value) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.author = author == null ? "" : author;
            this.channel = channel == null ? "" : channel;
            this.source = source;
            this.subscribed = subscribed;
            this.value = value;
        }
    }

    private MobileNewsMixer() {}

    public static <T> List<Item<T>> mix(List<Item<T>> news,
            List<Item<T>> subscriptions, List<Item<T>> recommendations,
            List<Item<T>> watched, Set<String> previousTopIds,
            long seed, int maximum) {
        int max = Math.max(0, maximum);
        ArrayList<Item<T>> result = new ArrayList<>();
        if (max == 0) return result;

        // Each source has unique videos; a News item wins if seen again in Home.
        Map<String, Item<T>> dedup = new LinkedHashMap<>();
        addUnique(dedup, news);
        addUnique(dedup, subscriptions);
        addUnique(dedup, recommendations);
        List<Item<T>> pool = new ArrayList<>(dedup.values());
        Map<String, Integer> interests = new HashMap<>();
        Set<String> favouriteChannels = new HashSet<>();
        if (watched != null) {
            for (int i = Math.max(0, watched.size() - 35); i < watched.size(); i++) {
                Item<T> item = watched.get(i);
                if (!item.channel.isEmpty()) favouriteChannels.add(item.channel);
                for (String token : tokenize(item.title)) {
                    Integer count = interests.get(token);
                    interests.put(token, Math.min(8, count == null ? 1 : count + 1));
                }
            }
        }
        Random random = new Random(seed);
        Map<String, Integer> channelCounts = new HashMap<>();
        Set<String> seen = new HashSet<>();
        Set<String> previous = previousTopIds == null ? new HashSet<>() : previousTopIds;

        for (int position = 0; position < max && seen.size() < pool.size(); position++) {
            // Approximately 70% provider News, 20% subscriptions, 10% Home.
            // When a source is unavailable, fill from the other real sources.
            int slot = position % 10;
            int desired = slot < 7 ? NEWS : slot < 9 ? SUBSCRIPTIONS : RECOMMENDATIONS;
            Item<T> best = choose(pool, desired, true, seen, previous, interests,
                    favouriteChannels, channelCounts, random);
            if (best == null) best = choose(pool, desired, false, seen, previous,
                    interests, favouriteChannels, channelCounts, random);
            if (best == null) break;
            seen.add(identity(best));
            result.add(best);
            if (!best.channel.isEmpty())
                channelCounts.put(best.channel, channelCounts.containsKey(best.channel)
                        ? channelCounts.get(best.channel) + 1 : 1);
        }
        return result;
    }

    private static <T> Item<T> choose(List<Item<T>> pool, int desired, boolean restricted,
            Set<String> seen, Set<String> previous, Map<String, Integer> interests,
            Set<String> favouriteChannels, Map<String, Integer> channelCounts, Random random) {
        Item<T> best = null;
        double high = Double.NEGATIVE_INFINITY;
        for (Item<T> item : pool) {
            if (seen.contains(identity(item))) continue;
            if (restricted && item.source != desired) continue;
            int channelCount = channelCounts.containsKey(item.channel)
                    ? channelCounts.get(item.channel) : 0;
            double score = random.nextDouble() * 2.5;
            if (previous.contains(identity(item))) score -= 4.0;
            if (!item.channel.isEmpty() && favouriteChannels.contains(item.channel)) score += 1.3;
            if (item.subscribed) score += 1.2;
            for (String token : tokenize(item.title))
                if (interests.containsKey(token))
                    score += Math.min(0.5, interests.get(token) * 0.1);
            if (!item.channel.isEmpty()) score -= channelCount * 1.15;
            if (score > high) { high = score; best = item; }
        }
        return best;
    }

    private static <T> void addUnique(Map<String, Item<T>> output, List<Item<T>> input) {
        if (input == null) return;
        for (Item<T> item : input) {
            if (item != null && item.id != null && !item.id.isEmpty()) {
                String id = identity(item);
                if (!output.containsKey(id)) output.put(id, item);
            }
        }
    }

    private static <T> String identity(Item<T> item) { return item.id; }

    private static Set<String> tokenize(String title) {
        Set<String> result = new HashSet<>();
        if (title == null) return result;
        String normalized = title.toLowerCase(Locale.ROOT);
        for (String token : normalized.split("[^\\p{L}\\p{Nd}]+")) {
            if (token.length() >= 4 && token.length() <= 28 &&
                    !token.equals("this") && !token.equals("that") &&
                    !token.equals("with") && !token.equals("from") &&
                    !token.equals("video") && !token.equals("watch") &&
                    !token.equals("breaking") && !token.equals("latest") &&
                    !token.equals("news")) result.add(token);
        }
        return result;
    }
}
