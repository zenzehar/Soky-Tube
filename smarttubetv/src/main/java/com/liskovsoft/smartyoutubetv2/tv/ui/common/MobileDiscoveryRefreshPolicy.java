package com.liskovsoft.smartyoutubetv2.tv.ui.common;

/** The Discover mix changes only on the initial open or deliberate top swipe. */
public final class MobileDiscoveryRefreshPolicy {
    private MobileDiscoveryRefreshPolicy() {}

    public static boolean shouldFetch(boolean initialLoadCompleted,
            boolean manuallyPulled, boolean requestInFlight) {
        return !requestInFlight && (!initialLoadCompleted || manuallyPulled);
    }

    public static boolean swipeStartsRefresh(boolean atTop, boolean manuallyPulled) {
        return atTop && manuallyPulled;
    }
}
