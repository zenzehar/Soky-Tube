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
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;

/** YouTube-like touch chrome; Android TV still uses the unchanged Leanback UI. */
public class BrowseActivity extends LeanbackActivity {
    private LinearLayout mMobileToolbar;
    private LinearLayout mMobileBottomBar;
    private View mMobileMain;
    private TextView mMobileTitle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.fragment_main);
            if (MobileUi.isHandheld(this)) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                getWindow().setStatusBarColor(Color.WHITE);
                getWindow().setNavigationBarColor(Color.WHITE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                installMobileNavigation();
            }
        } catch (NoClassDefFoundError e) {
            MessageHelpers.showMessage(this, e.getMessage());
        }
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
        addTab(R.drawable.mobile_ic_user, "You", v -> {
            BrowseFragment browse = browseFragment();
            if (browse == null || !browse.selectMobileSectionByName("history")) {
                showSections(v);
            }
        });

        FrameLayout.LayoutParams header = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP);
        FrameLayout.LayoutParams footer = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM);
        content.addView(mMobileToolbar, header);
        content.addView(mMobileBottomBar, footer);
        mMobileToolbar.bringToFront();
        mMobileBottomBar.bringToFront();
        applyWindowSizing();
        if (mMobileBottomBar.getChildCount() > 0) highlightMobileTab(mMobileBottomBar.getChildAt(0));
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
        ImageView iconView = new ImageView(this);
        iconView.setImageResource(image);
        iconView.setColorFilter(0xFF202124);
        column.addView(iconView, new LinearLayout.LayoutParams(dp(26), dp(26)));
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
            ImageView tabIcon = (ImageView) tab.getChildAt(0);
            TextView tabText = (TextView) tab.getChildAt(1);
            int ink = active ? 0xFF111111 : 0xFF707070;
            tabIcon.setColorFilter(ink);
            tabText.setTextColor(ink);
            tabText.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    @Override
    public void onBackPressed() {
        BrowseFragment browse = browseFragment();
        if (browse != null && browse.closeMobileDrawer()) return;
        super.onBackPressed();
    }

    private void selectHome() {
        BrowseFragment fragment = browseFragment();
        if (fragment != null) fragment.selectSection(0, true);
    }
    private boolean selectNamedSection(String label, View source) {
        BrowseFragment fragment = browseFragment();
        if (fragment != null && fragment.selectMobileSectionByName(label)) return true;
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
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mMobileToolbar != null) applyWindowSizing();
    }

    @Override protected void initTheme() {
        int browseTheme = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseTheme > 0) setTheme(browseTheme);
    }
}
