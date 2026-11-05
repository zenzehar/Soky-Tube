package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.bumptech.glide.Glide;
import android.text.TextUtils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileSystemBars;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;

/** YouTube-like touch chrome; Android TV still uses the unchanged Leanback UI. */
public class BrowseActivity extends LeanbackActivity {
    private LinearLayout mMobileToolbar;
    private LinearLayout mMobileBottomBar;
    private View mMobileMain;
    private TextView mMobileTitle;
    private MobileYouPage mMobileYou;
    private TextView mYouTabInitial;
    private ImageView mYouTabPicture;
    private MediaServiceManager.AccountChangeListener mAccountListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.fragment_main);
            if (MobileUi.isHandheld(this)) {
                // Phone status, cutout and navigation bars must be visible
                // BEFORE applying layout; the TV app's themes are fullscreen.
                MobileSystemBars.install(this, true);
                installMobileNavigation();
            }
        } catch (NoClassDefFoundError e) {
            MessageHelpers.showMessage(this, e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!MobileUi.isHandheld(this)) return;
        MobileSystemBars.ensure(this, true);
        if (mMobileYou != null) mMobileYou.onResume();
        updateYouTabAvatar();
        // Visible after a crash/relaunch without requiring adb or special access.
        String report = MainApplication.consumeMobileCrashReport(this);
        if (report == null) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Soky Tube: last crash report")
                .setMessage(report)
                .setPositiveButton("Copy report", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager)
                                    getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("Soky Tube crash", report));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private int dp(float d) { return MobileUi.dp(this, d); }
    private BrowseFragment browseFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_frame);
        return fragment instanceof BrowseFragment ? (BrowseFragment) fragment : null;
    }
    private void showSections(View anchor) {
        BrowseFragment fragment = browseFragment();
        if (fragment != null) fragment.showMobileSections(anchor);
    }

    private void installMobileNavigation() {
        FrameLayout content = findViewById(android.R.id.content);
        mMobileMain = findViewById(R.id.main_frame);
        if (content == null || mMobileMain == null) return;

        mMobileToolbar = new LinearLayout(this);
        mMobileToolbar.setOrientation(LinearLayout.HORIZONTAL);
        mMobileToolbar.setGravity(Gravity.CENTER_VERTICAL);
        mMobileToolbar.setBackgroundColor(Color.WHITE);
        mMobileToolbar.setPadding(dp(8), 0, dp(8), 0);

        ImageButton menuButton = icon(R.drawable.mobile_ic_menu, "Open sections");
        mMobileToolbar.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        menuButton.setOnClickListener(v -> showSections(menuButton));

        TextView brandSymbol = new TextView(this);
        brandSymbol.setText("\u25B6");
        brandSymbol.setTextColor(Color.WHITE);
        brandSymbol.setTextSize(16);
        brandSymbol.setGravity(Gravity.CENTER);
        GradientDrawable red = new GradientDrawable();
        red.setColor(0xFFFF173B);
        red.setCornerRadius(dp(7));
        brandSymbol.setBackground(red);
        mMobileToolbar.addView(brandSymbol, new LinearLayout.LayoutParams(dp(35), dp(25)));
        brandSymbol.setOnClickListener(v -> selectHome());

        mMobileTitle = new TextView(this);
        mMobileTitle.setText("Soky Tube");
        mMobileTitle.setTextColor(0xFF171717);
        mMobileTitle.setTextSize(20);
        mMobileTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mMobileTitle.setSingleLine(true);
        mMobileTitle.setGravity(Gravity.CENTER_VERTICAL);
        mMobileTitle.setPadding(dp(6), 0, 0, 0);
        mMobileToolbar.addView(mMobileTitle, new LinearLayout.LayoutParams(0,
                ViewGroupHeight(), 1f));
        mMobileTitle.setOnClickListener(v -> selectHome());

        ImageButton searchButton = icon(R.drawable.mobile_ic_search, "Search");
        mMobileToolbar.addView(searchButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        searchButton.setOnClickListener(v -> onSearchRequested());

        mMobileBottomBar = new LinearLayout(this);
        mMobileBottomBar.setGravity(Gravity.CENTER);
        mMobileBottomBar.setBackgroundColor(Color.WHITE);
        mMobileBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        mMobileBottomBar.setElevation(dp(4));
        addTab(R.drawable.mobile_ic_home, "Home", v -> selectHome());
        addTab(R.drawable.mobile_ic_play, "Shorts", v -> selectNamedSection("shorts", v));
        addTab(R.drawable.mobile_ic_library, "Subscriptions",
                v -> selectNamedSection("subscription", v));
        addTab(R.drawable.mobile_ic_user, "You", v -> showYouPage());

        FrameLayout.LayoutParams header = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP);
        FrameLayout.LayoutParams footer = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM);
        content.addView(mMobileToolbar, header);
        content.addView(mMobileBottomBar, footer);

        mMobileYou = new MobileYouPage(this, new MobileYouPage.Navigation() {
            @Override public boolean openSection(String name) {
                BrowseFragment browse = browseFragment();
                if (browse != null && browse.selectMobileSectionByName(name)) {
                    hideYouPage();
                    return true;
                }
                return false;
            }
            @Override public void openVideo(Video video) {
                if (video != null) BrowsePresenter.instance(BrowseActivity.this)
                        .onVideoItemClicked(video);
            }
            @Override public void openSearch() { onSearchRequested(); }
        });
        FrameLayout.LayoutParams youParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP);
        youParams.bottomMargin = dp(64);
        content.addView(mMobileYou.getView(), youParams);
        mMobileToolbar.bringToFront();
        mMobileBottomBar.bringToFront();
        applyWindowSizing();
        if (mMobileBottomBar.getChildCount() > 0) highlightMobileTab(mMobileBottomBar.getChildAt(0));
        mAccountListener = account -> runOnUiThread(this::updateYouTabAvatar);
        MediaServiceManager.instance().addAccountListener(mAccountListener);
        updateYouTabAvatar();
    }

    private int ViewGroupHeight() { return dp(52); }

    private ImageButton icon(int drawable, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setColorFilter(0xFF202124);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(description);
        return button;
    }

    private void addTab(int image, String title, View.OnClickListener handler) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        if ("You".equals(title)) {
            FrameLayout avatar = new FrameLayout(this);
            mYouTabInitial = new TextView(this);
            mYouTabInitial.setGravity(Gravity.CENTER);
            mYouTabInitial.setTextColor(Color.WHITE);
            mYouTabInitial.setTextSize(13);
            mYouTabInitial.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            mYouTabInitial.setText("?");
            GradientDrawable round = new GradientDrawable();
            round.setShape(GradientDrawable.OVAL);
            round.setColor(0xFF006359);
            mYouTabInitial.setBackground(round);
            avatar.addView(mYouTabInitial, new FrameLayout.LayoutParams(-1, -1));
            mYouTabPicture = new ImageView(this);
            mYouTabPicture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mYouTabPicture.setVisibility(View.GONE);
            avatar.addView(mYouTabPicture, new FrameLayout.LayoutParams(-1, -1));
            column.addView(avatar, new LinearLayout.LayoutParams(dp(27), dp(27)));
        } else {
            ImageView iconView = new ImageView(this);
            iconView.setImageResource(image);
            iconView.setColorFilter(0xFF202124);
            column.addView(iconView, new LinearLayout.LayoutParams(dp(26), dp(26)));
        }
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(0xFF202124);
        label.setTextSize(11);
        label.setGravity(Gravity.CENTER);
        column.addView(label);
        column.setOnClickListener(v -> {
            handler.onClick(v);
            highlightMobileTab(column);
        });
        column.setContentDescription(title);
        column.setFocusable(true);
        mMobileBottomBar.addView(column,
                new LinearLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT, 1f));
    }

    private void highlightMobileTab(View selected) {
        if (mMobileBottomBar == null) return;
        for (int i = 0; i < mMobileBottomBar.getChildCount(); i++) {
            View view = mMobileBottomBar.getChildAt(i);
            if (!(view instanceof LinearLayout)) continue;
            LinearLayout tab = (LinearLayout) view;
            boolean active = view == selected;
            View tabIcon = tab.getChildAt(0);
            TextView tabText = (TextView) tab.getChildAt(1);
            int ink = active ? 0xFF111111 : 0xFF707070;
            if (tabIcon instanceof ImageView)
                ((ImageView) tabIcon).setColorFilter(ink);
            tabText.setTextColor(ink);
            tabText.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void updateYouTabAvatar() {
        if (mYouTabInitial == null || mYouTabPicture == null) return;
        Account account = MediaServiceManager.instance().getSelectedAccount();
        String name = account != null ? account.getName() : null;
        mYouTabInitial.setText(!TextUtils.isEmpty(name)
                ? name.substring(0, 1).toUpperCase(java.util.Locale.getDefault()) : "?");
        String image = account != null ? account.getAvatarImageUrl() : null;
        if (!TextUtils.isEmpty(image)) {
            mYouTabPicture.setVisibility(View.VISIBLE);
            Glide.with(mYouTabPicture).load(image).circleCrop().into(mYouTabPicture);
        } else {
            Glide.with(mYouTabPicture).clear(mYouTabPicture);
            mYouTabPicture.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        if (mMobileYou != null && mMobileYou.isShown()) {
            selectHome();
            if (mMobileBottomBar != null && mMobileBottomBar.getChildCount() > 0)
                highlightMobileTab(mMobileBottomBar.getChildAt(0));
            return;
        }
        BrowseFragment browse = browseFragment();
        if (browse != null && browse.closeMobileDrawer()) return;
        super.onBackPressed();
    }

    private void showYouPage() {
        if (mMobileYou == null) return;
        updateYouTabAvatar();
        mMobileYou.show();
        if (mMobileMain != null) mMobileMain.setVisibility(View.GONE);
        if (mMobileToolbar != null) mMobileToolbar.setVisibility(View.GONE);
        applyWindowSizing();
    }
    private void hideYouPage() {
        if (mMobileYou != null) mMobileYou.hide();
        if (mMobileMain != null) mMobileMain.setVisibility(View.VISIBLE);
        if (mMobileToolbar != null) mMobileToolbar.setVisibility(View.VISIBLE);
        applyWindowSizing();
    }
    private void selectHome() {
        hideYouPage();
        BrowseFragment fragment = browseFragment();
        if (fragment != null) fragment.selectSection(0, true);
    }
    private boolean selectNamedSection(String label, View source) {
        BrowseFragment fragment = browseFragment();
        if (fragment != null && fragment.selectMobileSectionByName(label)) {
            hideYouPage();
            return true;
        }
        showSections(source);
        return false;
    }
    private void applyWindowSizing() {
        if (mMobileToolbar == null || mMobileBottomBar == null || mMobileMain == null) return;
        int header = MobileUi.toolbarHeightPx(this);
        int footer = dp(Math.max(56, Math.min(72,
                52f + getResources().getConfiguration().fontScale * 10f)));
        FrameLayout.LayoutParams headerParams = (FrameLayout.LayoutParams) mMobileToolbar.getLayoutParams();
        headerParams.height = header;
        mMobileToolbar.setLayoutParams(headerParams);
        FrameLayout.LayoutParams footerParams = (FrameLayout.LayoutParams) mMobileBottomBar.getLayoutParams();
        footerParams.height = footer;
        mMobileBottomBar.setLayoutParams(footerParams);
        FrameLayout.LayoutParams fragmentParams = (FrameLayout.LayoutParams) mMobileMain.getLayoutParams();
        fragmentParams.topMargin = header;
        fragmentParams.bottomMargin = footer;
        mMobileMain.setLayoutParams(fragmentParams);
        if (mMobileYou != null) {
            FrameLayout.LayoutParams you = (FrameLayout.LayoutParams)
                    mMobileYou.getView().getLayoutParams();
            you.bottomMargin = footer;
            mMobileYou.getView().setLayoutParams(you);
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mMobileToolbar != null) {
            MobileSystemBars.ensure(this, true);
            applyWindowSizing();
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && MobileUi.isHandheld(this))
            MobileSystemBars.ensure(this, true);
    }

    @Override protected void onDestroy() {
        if (mMobileYou != null) {
            mMobileYou.destroy();
            mMobileYou = null;
        }
        if (mAccountListener != null) {
            MediaServiceManager.instance().removeAccountListener(mAccountListener);
            mAccountListener = null;
        }
        super.onDestroy();
    }

    @Override protected void initTheme() {
        int browseTheme = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseTheme > 0) setTheme(browseTheme);
        if (MobileUi.isHandheld(this))
            getTheme().applyStyle(R.style.SokyMobileSafeAreaOverlay, true);
    }
}
