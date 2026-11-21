package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileDiscoveryMixer;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileDiscoveryRefreshPolicy;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileVideoFeed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * A standalone mobile Discover feed. It is NEVER tied to a Home/News row
 * adapter, an Activity onResume, an automatic page timer or scroll pagination.
 * Only opening an empty feed initially or pulling down at the very top fetches.
 */
final class MobileDiscoveryFeed {
    private static final String TAG = "MobileDiscoveryFeed";
    private final BrowseActivity activity;
    private final LinearLayout root;
    private final MobileVideoFeed feed;
    private final TextView hint;

    private final ArrayList<Video> displayed = new ArrayList<>();
    private List<Video> lastRecommended = Collections.emptyList();
    private List<Video> lastSubscriptions = Collections.emptyList();
    private List<Video> lastExplore = Collections.emptyList();
    private final ArrayList<Disposable> requests = new ArrayList<>();
    private boolean initialLoadCompleted;
    private boolean inFlight;
    private boolean visible;
    private boolean destroyed;
    private int requestId;
    private long seed = System.nanoTime();
    private int remaining;
    private List<Video> pendingRecommended;
    private List<Video> pendingSubscriptions;
    private List<Video> pendingExplore;
    private Set<String> previousTop = Collections.emptySet();

    MobileDiscoveryFeed(BrowseActivity activity) {
        this.activity = activity;
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        hint = new TextView(activity);
        hint.setText("Your interests, subscriptions and more. Pull down from the top for a new mix.");
        hint.setTextColor(0xFF616161);
        hint.setTextSize(12);
        hint.setPadding(dp(16), dp(10), dp(16), dp(9));
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        feed = new MobileVideoFeed(activity, video ->
                BrowsePresenter.instance(activity).onVideoItemClicked(video), false);
        feed.setBottomInsetDp(12);
        // This is the ONLY refresh event for an already initialized feed.
        feed.setOnRefresh(() -> {
            if (!MobileDiscoveryRefreshPolicy.swipeStartsRefresh(true, true) ||
                    !fetch(true)) feed.finishManualRefresh();
        });
        root.addView(feed.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.setVisibility(View.GONE);
    }

    private int dp(float value) { return MobileUi.dp(activity, value); }
    View getView() { return root; }
    boolean isShown() { return visible; }

    void show() {
        if (destroyed) return;
        visible = true;
        root.setVisibility(View.VISIBLE);
        // No calls from onResume or onScroll; never reload populated content.
        if (!initialLoadCompleted) fetch(false);
    }

    void hide() {
        visible = false;
        root.setVisibility(View.GONE);
    }

    void onAccountChanged() {
        // Never show the previous account's recommendations after switching.
        cancelRequests();
        displayed.clear();
        lastRecommended = Collections.emptyList();
        lastSubscriptions = Collections.emptyList();
        lastExplore = Collections.emptyList();
        feed.setDiscoverVideos(Collections.emptyList(), true);
        initialLoadCompleted = false;
        hint.setText("Account changed. Pull down from the top to load your new mix.");
        // Do not start a network request on account-listener callback.
    }

    void destroy() {
        destroyed = true;
        cancelRequests();
    }

    private void cancelRequests() {
        requestId++;
        for (Disposable disposable : requests)
            if (disposable != null) disposable.dispose();
        requests.clear();
        remaining = 0;
        inFlight = false;
        feed.finishManualRefresh();
    }

    private boolean fetch(boolean manualSwipe) {
        if (destroyed || !MobileDiscoveryRefreshPolicy.shouldFetch(
                initialLoadCompleted, manualSwipe, inFlight)) return false;

        cancelRequests();
        inFlight = true;
        final int current = ++requestId;
        remaining = 3;
        pendingRecommended = Collections.emptyList();
        pendingSubscriptions = Collections.emptyList();
        pendingExplore = Collections.emptyList();
        previousTop = new HashSet<>();
        if (manualSwipe) {
            for (int i = 0; i < Math.min(18, displayed.size()); i++)
                previousTop.add(displayed.get(i).videoId);
            seed = (seed * 6364136223846793005L) ^ System.nanoTime();
        }
        BrowsePresenter presenter = BrowsePresenter.instance(activity);
        hint.setText("Your interests, subscriptions and more. Pull down from the top for a new mix.");
        try {
            Disposable recommended = presenter.getMobileDiscoveryRecommendations()
                    .take(1).map(MobileDiscoveryFeed::flatten)
                    .first(Collections.<Video>emptyList())
                    .timeout(10, TimeUnit.SECONDS)
                    .onErrorReturnItem(Collections.emptyList())
                    .subscribeOn(Schedulers.io())
                    .subscribe(videos -> root.post(() -> receive(current, 0, videos)),
                            error -> root.post(() -> receive(current, 0, Collections.emptyList())));
            requests.add(recommended);

            Disposable subscriptions = presenter.getMobileDiscoverySubscriptions()
                    .take(1).map(MobileDiscoveryFeed::fromGroup)
                    .first(Collections.<Video>emptyList())
                    .timeout(10, TimeUnit.SECONDS)
                    .onErrorReturnItem(Collections.emptyList())
                    .subscribeOn(Schedulers.io())
                    .subscribe(videos -> root.post(() -> receive(current, 1, videos)),
                            error -> root.post(() -> receive(current, 1, Collections.emptyList())));
            requests.add(subscriptions);

            Disposable explore = presenter.getMobileDiscoveryExplore()
                    .take(1).map(MobileDiscoveryFeed::flatten)
                    .first(Collections.<Video>emptyList())
                    .timeout(10, TimeUnit.SECONDS)
                    .onErrorReturnItem(Collections.emptyList())
                    .subscribeOn(Schedulers.io())
                    .subscribe(videos -> root.post(() -> receive(current, 2, videos)),
                            error -> root.post(() -> receive(current, 2, Collections.emptyList())));
            requests.add(explore);
        } catch (RuntimeException e) {
            Log.e(TAG, "Discover provider unavailable: " + e.getMessage());
            cancelRequests();
            initialLoadCompleted = true;
            hint.setText("Couldn't load recommendations. Pull down from the top to retry.");
            return false;
        }
        return true;
    }

    /** Called only on the app's UI thread after each of three capped requests. */
    private void receive(int current, int source, List<Video> videos) {
        if (destroyed || current != requestId || !inFlight) return;
        if (source == 0) pendingRecommended = videos;
        else if (source == 1) pendingSubscriptions = videos;
        else pendingExplore = videos;
        if (--remaining != 0) return;

        inFlight = false;
        initialLoadCompleted = true;
        if (!pendingRecommended.isEmpty()) lastRecommended = pendingRecommended;
        if (!pendingSubscriptions.isEmpty()) lastSubscriptions = pendingSubscriptions;
        if (!pendingExplore.isEmpty()) lastExplore = pendingExplore;
        ArrayList<MobileDiscoveryMixer.Item<Video>> history = new ArrayList<>();
        VideoStateService states = VideoStateService.instance(activity);
        if (states != null) {
            for (VideoStateService.State state : new ArrayList<>(states.getStates())) {
                Video item = state.video;
                if (item != null && !item.incognito && item.videoId != null)
                    history.add(new MobileDiscoveryMixer.Item<>(item.videoId,
                            item.getTitle(), item.channelId,
                            MobileDiscoveryMixer.FOR_YOU, false, item));
            }
        }

        List<MobileDiscoveryMixer.Item<Video>> selected = MobileDiscoveryMixer.mix(
                entries(lastRecommended, MobileDiscoveryMixer.FOR_YOU),
                entries(lastSubscriptions, MobileDiscoveryMixer.SUBSCRIPTIONS),
                entries(lastExplore, MobileDiscoveryMixer.EXPLORE),
                history, previousTop, seed, 65);
        if (!selected.isEmpty()) {
            displayed.clear();
            for (MobileDiscoveryMixer.Item<Video> item : selected) displayed.add(item.value);
            feed.setDiscoverVideos(displayed, true);
        } else if (displayed.isEmpty()) {
            hint.setText("No recommendations available. Pull down from the top to retry.");
        }
        feed.finishManualRefresh();
        for (Disposable d : requests) if (d != null) d.dispose();
        requests.clear();
    }

    private static List<MobileDiscoveryMixer.Item<Video>> entries(List<Video> videos, int source) {
        ArrayList<MobileDiscoveryMixer.Item<Video>> output = new ArrayList<>();
        for (Video video : videos) {
            if (video != null && video.videoId != null && !video.videoId.isEmpty())
                output.add(new MobileDiscoveryMixer.Item<>(video.videoId, video.getTitle(),
                        video.channelId, source, video.isSubscribed ||
                        source == MobileDiscoveryMixer.SUBSCRIPTIONS, video));
        }
        return output;
    }

    private static List<Video> fromGroup(MediaGroup group) {
        ArrayList<Video> result = new ArrayList<>();
        if (group == null || group.getMediaItems() == null) return result;
        for (MediaItem media : group.getMediaItems()) {
            Video video = Video.from(media);
            if (video != null && video.videoId != null && !video.videoId.isEmpty() &&
                    !video.isChapter && !video.isUpcoming) result.add(video);
        }
        return result;
    }

    private static List<Video> flatten(List<MediaGroup> groups) {
        ArrayList<Video> result = new ArrayList<>();
        if (groups == null) return result;
        for (MediaGroup group : groups) result.addAll(fromGroup(group));
        return result;
    }
}
