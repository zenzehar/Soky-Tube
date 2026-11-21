package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileDiscoveryRefreshPolicyTest {
    @Test public void onlyFirstOpenFetchesAutomatically() {
        assertTrue(MobileDiscoveryRefreshPolicy.shouldFetch(false, false, false));
        assertFalse(MobileDiscoveryRefreshPolicy.shouldFetch(true, false, false));
    }
    @Test public void tabReturnScrollAndAppResumeDoNotFetch() {
        assertFalse(MobileDiscoveryRefreshPolicy.shouldFetch(true, false, false));
        assertFalse(MobileDiscoveryRefreshPolicy.swipeStartsRefresh(false, true));
        assertFalse(MobileDiscoveryRefreshPolicy.swipeStartsRefresh(true, false));
    }
    @Test public void topPullRefreshesAndNetworkIsDeduplicated() {
        assertTrue(MobileDiscoveryRefreshPolicy.swipeStartsRefresh(true, true));
        assertTrue(MobileDiscoveryRefreshPolicy.shouldFetch(true, true, false));
        assertFalse(MobileDiscoveryRefreshPolicy.shouldFetch(true, true, true));
    }
}
