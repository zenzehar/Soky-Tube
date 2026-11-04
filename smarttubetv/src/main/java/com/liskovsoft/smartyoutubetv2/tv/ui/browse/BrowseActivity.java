package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class BrowseActivity extends LeanbackActivity {
    private static final String TAG = BrowseActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.fragment_main);
            if (MobileUi.isHandheld(this)) {
                installMobileNavigation();
            }
        } catch (NoClassDefFoundError e) {
            // Failed resolution of: Landroidx/lifecycle/ViewTreeLifecycleOwner;
            MessageHelpers.showMessage(this, e.getMessage());
        }
    }

    private void installMobileNavigation() {
        FrameLayout content = findViewById(android.R.id.content);
        View mainFragment = findViewById(R.id.main_frame);
        if (content == null || mainFragment == null) return;

        int height = MobileUi.dp(this, 60);
        FrameLayout.LayoutParams fragmentParams = (FrameLayout.LayoutParams) mainFragment.getLayoutParams();
        fragmentParams.bottomMargin = height;
        mainFragment.setLayoutParams(fragmentParams);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(MobileUi.dp(this, 4), 0, MobileUi.dp(this, 4), 0);
        bar.setBackgroundColor(Color.rgb(20, 20, 24));
        addAction(bar, "Sections", v -> {
            androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_frame);
            if (fragment instanceof BrowseFragment) {
                ((BrowseFragment) fragment).openMobileSections();
            }
        });
        addAction(bar, "Home", v -> {
            androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_frame);
            if (fragment instanceof BrowseFragment) {
                ((BrowseFragment) fragment).selectSection(0, true);
            }
        });
        addAction(bar, "Search", v -> onSearchRequested());
        addAction(bar, "Back", v -> onBackPressed());
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
        content.addView(bar, barParams);
    }

    private void addAction(LinearLayout bar, String label, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(MobileUi.dp(this, 48));
        button.setContentDescription(label);
        button.setOnClickListener(action);
        bar.addView(button, new LinearLayout.LayoutParams(0, MobileUi.dp(this, 56), 1f));
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) {
            setTheme(browseThemeResId);
        }
    }
}
