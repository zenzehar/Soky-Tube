package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * A real handheld You tab. Uses the same accounts/history/playlists sources
 * as the TV browse presenter; no fabricated identity, thumbnails or playlists.
 * The TV BrowseFragment is neither replaced nor modified.
 */
final class MobileYouPage {
    interface Navigation {
        boolean openSection(String name);
        void openVideo(Video video);
        void openSearch();
    }

    private final BrowseActivity activity;
    private final Navigation navigation;
    private final FrameLayout root;
    private final SwipeRefreshLayout refresher;
    private final ScrollView scroll;
    private final LinearLayout page;
    private final TextView initials;
    private final ImageView portrait;
    private final TextView accountName;
    private final TextView accountDetails;
    private final LinearLayout historyRow;
    private final LinearLayout playlistsRow;
    private final TextView historyHint;
    private final TextView playlistsHint;
    private Disposable historyRequest;
    private Disposable playlistsRequest;
    private VideoGroup retainedHistory;
    private VideoGroup retainedPlaylists;
    private boolean shown;
    private final MediaServiceManager.AccountChangeListener accountListener;

    MobileYouPage(BrowseActivity activity, Navigation navigation) {
        this.activity = activity;
        this.navigation = navigation;
        root = new FrameLayout(activity);
        root.setBackgroundColor(Color.WHITE);
        root.setVisibility(View.GONE);
        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(5), dp(16), dp(26));
        page.setBackgroundColor(Color.WHITE);
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refresher = new SwipeRefreshLayout(activity);
        refresher.setColorSchemeColors(0xFFFF173B, 0xFF222222);
        refresher.addView(scroll);
        refresher.setOnChildScrollUpCallback((parent, child) -> scroll.canScrollVertically(-1));
        refresher.setOnRefreshListener(this::refresh);
        root.addView(refresher, new FrameLayout.LayoutParams(-1, -1));

        // YouTube's compact page actions are WITHIN the You screen, not the
        // regular Soky Tube logo toolbar above it.
        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        addIcon(actions, R.drawable.icon_notification, "Notifications",
                () -> openSection("notification"));
        addIcon(actions, R.drawable.mobile_ic_search, "Search", navigation::openSearch);
        addIcon(actions, R.drawable.mobile_ic_settings, "Settings",
                () -> openSection("settings"));
        page.addView(actions, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout identity = new LinearLayout(activity);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(-1, dp(105));
        identityParams.topMargin = dp(11);
        page.addView(identity, identityParams);
        FrameLayout avatarHolder = new FrameLayout(activity);
        avatarHolder.setBackground(round(0xFF006359, 100));
        initials = text("?", 32, Color.WHITE, true);
        initials.setGravity(Gravity.CENTER);
        avatarHolder.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        portrait = new ImageView(activity);
        portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarHolder.addView(portrait, new FrameLayout.LayoutParams(-1, -1));
        identity.addView(avatarHolder, new LinearLayout.LayoutParams(dp(78), dp(78)));
        avatarHolder.setOnClickListener(v -> openAccountPicker());

        LinearLayout identityLabels = new LinearLayout(activity);
        identityLabels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelsParams.leftMargin = dp(15);
        identity.addView(identityLabels, labelsParams);
        accountName = text("Not signed in", 24, Color.BLACK, true);
        accountName.setSingleLine(true);
        accountName.setEllipsize(TextUtils.TruncateAt.END);
        identityLabels.addView(accountName);
        accountDetails = text("Sign in to view your account  ›", 13, 0xFF555555, false);
        accountDetails.setMaxLines(2);
        LinearLayout.LayoutParams small = new LinearLayout.LayoutParams(-1, -2);
        small.topMargin = dp(3);
        identityLabels.addView(accountDetails, small);
        identityLabels.setOnClickListener(v -> openAccountPicker());

        HorizontalScrollView chipScroller = new HorizontalScrollView(activity);
        chipScroller.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(activity);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chipScroller.addView(chips);
        addChip(chips, "Switch account", this::openAccountPicker);
        addChip(chips, "Google Account", () -> browser("https://myaccount.google.com/"));
        addChip(chips, "Use without account", () -> new android.app.AlertDialog.Builder(activity)
                .setTitle("Use without an account?")
                .setMessage("This selects the signed-out profile. It is not the same as YouTube Incognito mode.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    AccountSelectionPresenter.instance(activity).selectAccount(null);
                    refresh();
                })
                .setNegativeButton("Cancel", null).show());
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-1, dp(58));
        chipParams.topMargin = dp(14);
        page.addView(chipScroller, chipParams);

        historyRow = new LinearLayout(activity);
        historyRow.setOrientation(LinearLayout.HORIZONTAL);
        historyHint = section(page, "History", () -> openSection("history"), false);
        page.addView(horizontal(historyRow), new LinearLayout.LayoutParams(-1, dp(198)));

        playlistsRow = new LinearLayout(activity);
        playlistsRow.setOrientation(LinearLayout.HORIZONTAL);
        playlistsHint = section(page, "Playlists", () -> openSection("playlist"), true);
        page.addView(horizontal(playlistsRow), new LinearLayout.LayoutParams(-1, dp(190)));

        separator();
        menuItem("Your videos", R.drawable.mobile_ic_play, () -> openSection("my videos"));
        menuItem("History", R.drawable.icon_history, () -> openSection("history"));
        menuItem("Your playlists", R.drawable.icon_playlist, () -> openSection("playlist"));
        menuItem("Settings", R.drawable.mobile_ic_settings, () -> openSection("settings"));
        separator();

        TextView note = text("Your videos, history and playlists are loaded from the connected account. Some YouTube-specific account features are not supported by Soky Tube.",
                12, 0xFF777777, false);
        note.setPadding(0, dp(15), dp(8), dp(8));
        page.addView(note);

        accountListener = account -> activity.runOnUiThread(() -> {
            if (shown) {
                updateAccount();
                refresh();
            }
        });
        MediaServiceManager.instance().addAccountListener(accountListener);
        updateAccount();
    }

    View getView() { return root; }
    private int dp(float size) { return MobileUi.dp(activity, size); }

    void show() {
        if (!shown) {
            shown = true;
            root.setVisibility(View.VISIBLE);
            updateAccount();
            refresh();
        } else {
            root.setVisibility(View.VISIBLE);
        }
    }
    void hide() {
        shown = false;
        root.setVisibility(View.GONE);
        disposeRequests();
        refresher.setRefreshing(false);
    }
    boolean isShown() { return shown; }
    void onResume() {
        if (shown) {
            updateAccount();
            refresh();
        }
    }
    void destroy() {
        hide();
        MediaServiceManager.instance().removeAccountListener(accountListener);
    }

    private void updateAccount() {
        Account account = MediaServiceManager.instance().getSelectedAccount();
        String name = account != null && !TextUtils.isEmpty(account.getName())
                ? account.getName() : "Not signed in";
        String email = account != null ? account.getEmail() : null;
        accountName.setText(name);
        accountDetails.setText(email != null && !email.isEmpty()
                ? email + "  ·  Account settings ›"
                : account == null ? "Sign in to view your account ›" : "Account settings ›");
        String initial = !TextUtils.isEmpty(name) ? name.substring(0, 1).toUpperCase(Locale.getDefault()) : "?";
        initials.setText(initial);
        String imageUrl = account == null ? null : account.getAvatarImageUrl();
        if (TextUtils.isEmpty(imageUrl)) {
            Glide.with(activity).clear(portrait);
            portrait.setVisibility(View.GONE);
        } else {
            portrait.setVisibility(View.VISIBLE);
            Glide.with(activity).load(imageUrl).circleCrop().into(portrait);
        }
    }

    private void refresh() {
        disposeRequests();
        historyRow.removeAllViews();
        playlistsRow.removeAllViews();
        historyHint.setText("Loading history…");
        playlistsHint.setText("Loading playlists…");
        // The shared media-service observables are the same sources used by
        // SmartTube's existing History and Playlists browse sections.
        try {
            historyRequest = YouTubeServiceManager.instance().getContentService()
                    .getHistoryObserve()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(group -> applyGroup(group, false),
                            error -> showError(false));
            playlistsRequest = YouTubeServiceManager.instance().getContentService()
                    .getPlaylistsObserve()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(group -> applyGroup(group, true),
                            error -> showError(true));
        } catch (RuntimeException ex) {
            showError(false);
            showError(true);
        }
        root.postDelayed(() -> refresher.setRefreshing(false), 1200);
    }
    private void disposeRequests() {
        if (historyRequest != null && !historyRequest.isDisposed()) historyRequest.dispose();
        if (playlistsRequest != null && !playlistsRequest.isDisposed()) playlistsRequest.dispose();
        historyRequest = null;
        playlistsRequest = null;
    }
    private void showError(boolean playlist) {
        if (!shown) return;
        (playlist ? playlistsHint : historyHint).setText("Unable to load · tap heading to open");
        refresher.setRefreshing(false);
    }

    private void applyGroup(MediaGroup mediaGroup, boolean playlist) {
        if (!shown) return;
        VideoGroup converted = mediaGroup != null ? VideoGroup.from(mediaGroup) : null;
        if (playlist) retainedPlaylists = converted;
        else retainedHistory = converted;
        LinearLayout row = playlist ? playlistsRow : historyRow;
        TextView hint = playlist ? playlistsHint : historyHint;
        row.removeAllViews();
        List<Video> videos = converted != null ? converted.getVideos() : null;
        if (videos == null || videos.isEmpty()) {
            hint.setText(playlist ? "No playlists yet" : "No recent history");
            return;
        }
        hint.setText("");
        int shownCount = 0;
        for (Video video : videos) {
            if (video == null || video.isChapter) continue;
            row.addView(previewCard(video, playlist));
            if (++shownCount >= 14) break;
        }
        if (shownCount == 0) hint.setText(playlist ? "No playlists yet" : "No recent history");
        refresher.setRefreshing(false);
    }

    private View previewCard(Video video, boolean playlist) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(166), -1);
        params.rightMargin = dp(11);
        card.setLayoutParams(params);
        FrameLayout thumbnail = new FrameLayout(activity);
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFFF0F0F0);
        thumbnail.addView(image, new FrameLayout.LayoutParams(-1, -1));
        Glide.with(image).load(video.getCardImageUrl()).centerCrop().into(image);
        if (playlist) {
            TextView overlay = text("▶", 16, Color.WHITE, true);
            overlay.setGravity(Gravity.CENTER);
            overlay.setBackgroundColor(0x66000000);
            FrameLayout.LayoutParams pos = new FrameLayout.LayoutParams(dp(40), -1, Gravity.END);
            thumbnail.addView(overlay, pos);
        } else if (video.getDurationMs() > 0L) {
            long seconds = video.getDurationMs() / 1000L;
            String duration = seconds >= 3600
                    ? String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                    : String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
            TextView badge = text(duration, 11, Color.WHITE, true);
            badge.setPadding(dp(4), dp(2), dp(4), dp(2));
            badge.setBackground(round(0xDC171717, 4));
            FrameLayout.LayoutParams pos = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.END | Gravity.BOTTOM);
            pos.setMargins(0, 0, dp(5), dp(5));
            thumbnail.addView(badge, pos);
        }
        card.addView(thumbnail, new LinearLayout.LayoutParams(-1, dp(93)));
        TextView title = text(video.getTitle() != null ? video.getTitle() : "Untitled",
                13, Color.BLACK, false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(43));
        titleParams.topMargin = dp(8);
        card.addView(title, titleParams);
        String subtitle = video.getAuthor();
        if (playlist && video.getSecondTitle() != null)
            subtitle = video.getSecondTitle().toString();
        TextView meta = text(subtitle != null ? subtitle : "", 12, 0xFF777777, false);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(meta);
        card.setOnClickListener(v -> navigation.openVideo(video));
        card.setContentDescription((playlist ? "Playlist " : "History video ") + title.getText());
        return card;
    }

    private TextView section(LinearLayout parent, String heading, Runnable action,
            boolean addButton) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(59));
        params.topMargin = dp(14);
        parent.addView(bar, params);
        TextView title = text(heading + "  ›", 20, Color.BLACK, true);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        title.setOnClickListener(v -> action.run());
        if (addButton) {
            TextView plus = text("+", 32, Color.BLACK, false);
            plus.setGravity(Gravity.CENTER);
            bar.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
            // Opens the real playlists section; no fake creation controls.
            plus.setOnClickListener(v -> action.run());
        }
        TextView hint = text("", 12, 0xFF777777, false);
        parent.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        return hint;
    }

    private HorizontalScrollView horizontal(LinearLayout content) {
        HorizontalScrollView wrapper = new HorizontalScrollView(activity);
        wrapper.setHorizontalScrollBarEnabled(false);
        wrapper.setFillViewport(false);
        wrapper.setClipToPadding(false);
        wrapper.addView(content, new HorizontalScrollView.LayoutParams(-2, -1));
        return wrapper;
    }

    private void addIcon(LinearLayout parent, int resource, String label, Runnable action) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(resource);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(Color.BLACK);
        button.setContentDescription(label);
        parent.addView(button, new LinearLayout.LayoutParams(dp(49), dp(49)));
        button.setOnClickListener(v -> action.run());
    }

    private void addChip(LinearLayout parent, String label, Runnable action) {
        TextView chip = text(label, 13, Color.BLACK, false);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackground(round(0xFFF2F2F2, 36));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(39));
        params.rightMargin = dp(8);
        parent.addView(chip, params);
        chip.setOnClickListener(v -> action.run());
    }

    private void menuItem(String label, int icon, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = new ImageView(activity);
        image.setImageResource(icon);
        image.setColorFilter(Color.BLACK);
        image.setPadding(dp(7), dp(7), dp(7), dp(7));
        row.addView(image, new LinearLayout.LayoutParams(dp(37), dp(37)));
        TextView name = text(label, 17, Color.BLACK, false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.leftMargin = dp(17);
        row.addView(name, titleParams);
        page.addView(row, new LinearLayout.LayoutParams(-1, dp(59)));
        row.setOnClickListener(v -> action.run());
    }
    private void separator() {
        View line = new View(activity);
        line.setBackgroundColor(0xFFEAEAEA);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.topMargin = dp(14);
        params.bottomMargin = dp(12);
        page.addView(line, params);
    }

    private TextView text(String message, int size, int color, boolean bold) {
        TextView result = new TextView(activity);
        result.setText(message);
        result.setTextSize(size);
        result.setTextColor(color);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }
    private GradientDrawable round(int color, float dpRadius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(dpRadius));
        return shape;
    }
    private void openAccountPicker() {
        AccountSelectionPresenter.instance(activity).show(true);
    }
    private boolean openSection(String target) {
        boolean opened = navigation.openSection(target);
        if (!opened) android.widget.Toast.makeText(activity,
                "Section not available for this account", android.widget.Toast.LENGTH_SHORT).show();
        return opened;
    }
    private void browser(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException ex) {
            android.widget.Toast.makeText(activity, "No browser available", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
