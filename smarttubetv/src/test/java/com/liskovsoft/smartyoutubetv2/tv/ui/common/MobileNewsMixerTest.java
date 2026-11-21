package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import org.junit.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class MobileNewsMixerTest {
    private static MobileNewsMixer.Item<String> item(String id, int source) {
        return new MobileNewsMixer.Item<>(id, "Engineering climate market " + id,
                "Channel " + (Integer.parseInt(id.substring(1)) % 12),
                "ch" + (Integer.parseInt(id.substring(1)) % 12),
                source, source == MobileNewsMixer.SUBSCRIPTIONS, id);
    }
    private static List<MobileNewsMixer.Item<String>> pool(int start, int count, int source) {
        List<MobileNewsMixer.Item<String>> list = new ArrayList<>();
        for (int i = start; i < start + count; i++) list.add(item("v" + i, source));
        return list;
    }
    @Test public void mixedFeedIsDiverseAndContainsNoDuplicateVideoIds() {
        List<MobileNewsMixer.Item<String>> result = MobileNewsMixer.mix(
                pool(0, 100, MobileNewsMixer.NEWS),
                pool(80, 50, MobileNewsMixer.SUBSCRIPTIONS),
                pool(120, 70, MobileNewsMixer.RECOMMENDATIONS),
                pool(150, 5, MobileNewsMixer.NEWS), new HashSet<>(), 77L, 50);
        assertEquals(50, result.size());
        Set<String> ids = new HashSet<>();
        int news = 0, subs = 0, home = 0;
        for (MobileNewsMixer.Item<String> value : result) {
            assertTrue(ids.add(value.id));
            if (value.source == MobileNewsMixer.NEWS) news++;
            if (value.source == MobileNewsMixer.SUBSCRIPTIONS) subs++;
            if (value.source == MobileNewsMixer.RECOMMENDATIONS) home++;
        }
        assertTrue("News must be majority", news >= 30);
        assertTrue("Subscription items included", subs >= 5);
        assertTrue("Home recommendations included", home >= 2);
    }
    @Test public void refreshChangesCombinationAndAvoidsRecentlyShownTopVideos() {
        List<MobileNewsMixer.Item<String>> news = pool(0, 120, MobileNewsMixer.NEWS);
        List<MobileNewsMixer.Item<String>> oldMix = MobileNewsMixer.mix(
                news, null, null, null, null, 21, 35);
        Set<String> previous = new HashSet<>();
        for (int i = 0; i < 15; i++) previous.add(oldMix.get(i).id);
        List<MobileNewsMixer.Item<String>> next = MobileNewsMixer.mix(
                news, null, null, null, previous, 22, 35);
        int sameTop = 0;
        for (int i = 0; i < 15; i++)
            if (previous.contains(next.get(i).id)) sameTop++;
        assertTrue("Refresh should not show the same top page", sameTop < 5);
    }
    @Test public void signedOutOrUnavailableOptionalSourcesStillShowNews() {
        List<MobileNewsMixer.Item<String>> result = MobileNewsMixer.mix(
                pool(0, 20, MobileNewsMixer.NEWS),
                null, null, null, null, 4, 60);
        assertEquals(20, result.size());
    }
}
