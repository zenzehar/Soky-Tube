package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import org.junit.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class MobileDiscoveryMixerTest {
    private static MobileDiscoveryMixer.Item<String> item(int index, int source) {
        return new MobileDiscoveryMixer.Item<>("v" + index,
                "Outdoor technology reviews " + index, "channel" + (index % 17),
                source, source == MobileDiscoveryMixer.SUBSCRIPTIONS, "v" + index);
    }
    private static List<MobileDiscoveryMixer.Item<String>> pool(int start, int count, int source) {
        List<MobileDiscoveryMixer.Item<String>> result = new ArrayList<>();
        for (int i = start; i < start + count; i++) result.add(item(i, source));
        return result;
    }
    @Test public void mixesAllCategoriesWithoutDuplicates() {
        List<MobileDiscoveryMixer.Item<String>> mix = MobileDiscoveryMixer.mix(
                pool(0, 120, MobileDiscoveryMixer.FOR_YOU),
                pool(95, 65, MobileDiscoveryMixer.SUBSCRIPTIONS),
                pool(150, 90, MobileDiscoveryMixer.EXPLORE),
                pool(0, 5, MobileDiscoveryMixer.FOR_YOU),
                null, 42, 60);
        assertEquals(60, mix.size());
        int recommendations = 0, subscriptions = 0, explore = 0;
        Set<String> seen = new HashSet<>();
        for (MobileDiscoveryMixer.Item<String> item : mix) {
            assertTrue(seen.add(item.id));
            if (item.source == MobileDiscoveryMixer.FOR_YOU) recommendations++;
            if (item.source == MobileDiscoveryMixer.SUBSCRIPTIONS) subscriptions++;
            if (item.source == MobileDiscoveryMixer.EXPLORE) explore++;
        }
        assertTrue(recommendations >= 20);
        assertTrue(subscriptions >= 5);
        assertTrue(explore >= 5);
    }
    @Test public void pullProducesNewCombinationEvenWhenSourceRepeats() {
        List<MobileDiscoveryMixer.Item<String>> source =
                pool(0, 100, MobileDiscoveryMixer.FOR_YOU);
        List<MobileDiscoveryMixer.Item<String>> first =
                MobileDiscoveryMixer.mix(source, null, null, null, null, 10L, 40);
        Set<String> previous = new HashSet<>();
        for (int i = 0; i < 15; i++) previous.add(first.get(i).id);
        List<MobileDiscoveryMixer.Item<String>> second =
                MobileDiscoveryMixer.mix(source, null, null, null, previous, 11L, 40);
        int overlap = 0;
        for (int i = 0; i < 15; i++)
            if (previous.contains(second.get(i).id)) overlap++;
        assertTrue("Repeated top cards are discouraged", overlap < 5);
    }
    @Test public void signedOutFallbackUsesExistingRecommendations() {
        assertEquals(18, MobileDiscoveryMixer.mix(
                pool(0, 18, MobileDiscoveryMixer.FOR_YOU),
                null, null, null, null, 7, 60).size());
    }
    @Test public void emptyPoolDoesNotCreateFakeVideos() {
        assertTrue(MobileDiscoveryMixer.mix(null, null, null,
                null, null, 5, 40).isEmpty());
    }
}
