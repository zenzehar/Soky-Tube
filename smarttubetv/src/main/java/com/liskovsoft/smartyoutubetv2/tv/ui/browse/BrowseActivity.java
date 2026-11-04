package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;

/**
 * Android TV retains its original Leanback screen. Only touch devices receive
 * the compact mobile header: a hamburger menu and an immediately available search.
 */
public class BrowseActivity extends LeanbackActivity {
    private static final String TAG = BrowseActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.fragment_main);
            if (MobileUi.isHandheld(this)) installMobileNavigation();
        } catch (NoClassDefFoundError e) {
            MessageHelpers.showMessage(this, e.getMessage());
        }
    }

    private BrowseFragment browseFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_frame);
        return fragment instanceof BrowseFragment ? (BrowseFragment) fragment : null;
    }

    private void installMobileNavigation() {
        FrameLayout content = findViewById(android.R.id.content);
        View mainFragment = findViewById(R.id.main_frame);
        if (content == null || mainFragment == null) return;

        final int barHeight = MobileUi.dp(this, 54);
        FrameLayout.LayoutParams fragmentParams = (FrameLayout.LayoutParams) mainFragment.getLayoutParams();
        // No bottom toolbar, so the last browsing row stays accessible above gesture navigation.
        fragmentParams.topMargin = barHeight;
        fragmentParams.bottomMargin = 0;
        mainFragment.setLayoutParams(fragmentParams);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.rgb(18, 18, 22));
        toolbar.setPadding(MobileUi.dp(this, 4), 0, MobileUi.dp(this, 4), 0);

        TextView hamburger = new TextView(this);
        hamburger.setText("\u2630");
        hamburger.setTextColor(Color.WHITE);
        hamburger.setTextSize(26);
        hamburger.setGravity(Gravity.CENTER);
        hamburger.setContentDescription("Open sections and navigation menu");
        hamburger.setClickable(true);
        hamburger.setFocusable(true);
        toolbar.addView(hamburger, new LinearLayout.LayoutParams(MobileUi.dp(this, 52), barHeight));
        hamburger.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, hamburger);
            menu.getMenu().add("Sections").setOnMenuItemClickListener(item -> {
                BrowseFragment browse = browseFragment();
                if (browse != null) browse.showMobileSections(hamburger);
                return true;
            });
            menu.getMenu().add("Home").setOnMenuItemClickListener(item -> {
                BrowseFragment browse = browseFragment();
                if (browse != null) browse.selectSection(0, true);
                return true;
            });
            menu.getMenu().add("Search").setOnMenuItemClickListener(item -> {
                onSearchRequested();
                return true;
            });
            menu.getMenu().add("Back").setOnMenuItemClickListener(item -> {
                onBackPressed();
                return true;
            });
            menu.show();
        });

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(MobileUi.dp(this, 8), 0, 0, 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, barHeight, 1f));

        ImageButton search = new ImageButton(this);
        search.setImageResource(android.R.drawable.ic_menu_search);
        search.setColorFilter(Color.WHITE);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setContentDescription("Search videos");
        search.setOnClickListener(v -> onSearchRequested());
        toolbar.addView(search, new LinearLayout.LayoutParams(MobileUi.dp(this, 52), barHeight));

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, barHeight, Gravity.TOP);
        content.addView(toolbar, barParams);
        toolbar.bringToFront();
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) setTheme(browseThemeResId);
    }
}
