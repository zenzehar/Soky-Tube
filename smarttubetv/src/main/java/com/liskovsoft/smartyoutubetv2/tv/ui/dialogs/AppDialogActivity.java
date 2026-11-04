package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GlobalKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.misc.PlayerKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

public class AppDialogActivity extends MotherActivity {
    private static final String TAG = AppDialogActivity.class.getSimpleName();
    private AppDialogFragment mFragment;
    private GlobalKeyTranslator mGlobalKeyTranslator;
    private View mMobileSheet;
    private View mMobileToolbar;
    private TextView mMobileTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean mobile = MobileUi.isHandheld(this);
        setContentView(mobile ? R.layout.fragment_app_settings_mobile
                : R.layout.fragment_app_settings);
        // Can't use getSupportFragmentManager because AppDialogFragment isn't subclass of androidx fragment
        mFragment = (AppDialogFragment) getFragmentManager().findFragmentById(R.id.app_settings_fragment);
        if (mobile) {
            mMobileSheet = findViewById(R.id.mobile_settings_sheet);
            mMobileToolbar = findViewById(R.id.mobile_settings_toolbar);
            mMobileTitle = findViewById(R.id.mobile_settings_title);
            View back = findViewById(R.id.mobile_settings_back);
            View close = findViewById(R.id.mobile_settings_close);
            if (back != null) back.setOnClickListener(v -> {
                if (mFragment != null && mFragment.canGoBack()) mFragment.goBack();
                else finish();
            });
            if (close != null) close.setOnClickListener(v -> finish());
            // Measured-window changes, font settings, folding and multi-window
            // immediately resize the entire sheet without scrolling it sideways.
            View contentRoot = findViewById(android.R.id.content);
            if (contentRoot != null) {
                // Watch the actual WINDOW container, not the capped sheet:
                // the sheet can remain 640dp wide when split-screen shrinks.
                contentRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
                    if ((r - l) != (orr - ol)) requestMobileSettingsLayout();
                });
            }
            getWindow().getDecorView().post(this::requestMobileSettingsLayout);
        }

        mGlobalKeyTranslator = new PlayerKeyTranslator(this);
        mGlobalKeyTranslator.apply();
    }

    public void setMobilePanelTitle(CharSequence text) {
        if (mMobileTitle != null && text != null) mMobileTitle.setText(text);
    }

    /** Reflow both the root preference pane and any nested option/dialog pane.
     * Leanback's TV defaults reserve a left gutter; on phones both panes must
     * occupy this sheet's full content width rather than 80% of the screen.
     */
    public void requestMobileSettingsLayout() {
        if (mMobileSheet == null) return;
        float fullDp = MobileUi.windowWidthDp(this);
        int width = MobileUi.dp(this, MobileUi.settingsPanelWidthDp(fullDp));
        if (mMobileSheet.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams sheet = (FrameLayout.LayoutParams) mMobileSheet.getLayoutParams();
            if (sheet.width != width || sheet.gravity != Gravity.CENTER) {
                sheet.width = width;
                sheet.height = ViewGroup.LayoutParams.MATCH_PARENT;
                sheet.gravity = Gravity.CENTER;
                mMobileSheet.setLayoutParams(sheet);
            }
        }
        if (mMobileToolbar != null && mMobileToolbar.getLayoutParams() != null) {
            int height = MobileUi.toolbarHeightPx(this);
            if (mMobileToolbar.getLayoutParams().height != height) {
                ViewGroup.LayoutParams p = mMobileToolbar.getLayoutParams();
                p.height = height;
                mMobileToolbar.setLayoutParams(p);
            }
        }
        if (mFragment != null && mFragment.getView() != null) {
            View root = mFragment.getView();
            expandMobilePreferencePane(root.findViewById(R.id.settings_preference_fragment_container));
            int nestedId = getResources().getIdentifier("settings_dialog_fragment_container",
                    "id", getPackageName());
            if (nestedId != 0) expandMobilePreferencePane(root.findViewById(nestedId));
        }
    }

    private void expandMobilePreferencePane(View pane) {
        if (pane == null || pane.getLayoutParams() == null) return;
        ViewGroup.LayoutParams params = pane.getLayoutParams();
        boolean changed = params.width != ViewGroup.LayoutParams.MATCH_PARENT;
        if (changed) params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        if (params instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) params;
            if (frame.gravity != Gravity.FILL) {
                frame.gravity = Gravity.FILL;
                changed = true;
            }
            if (frame.leftMargin != 0 || frame.rightMargin != 0) {
                frame.leftMargin = 0;
                frame.rightMargin = 0;
                changed = true;
            }
        }
        if (changed) pane.setLayoutParams(params);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (mMobileSheet != null) mMobileSheet.post(this::requestMobileSettingsLayout);
    }

    @Override
    protected void initTheme() {
        int settingsThemeResId = MainUIData.instance(this).getColorScheme().settingsThemeResId;
        if (settingsThemeResId > 0) {
            setTheme(settingsThemeResId);
        }
    }

    private void setupActivity() {
        // Fix crash in AppSettingsActivity: "Only fullscreen opaque activities can request orientation"
        // Error happen only on Android O (api 26) when you set "portrait" orientation in manifest
        // So, to fix the problem, set orientation here instead of manifest
        // More info: https://stackoverflow.com/questions/48072438/java-lang-illegalstateexception-only-fullscreen-opaque-activities-can-request-o
        if (VERSION.SDK_INT != 26) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        //if (KeyHelpers.isConfirmKey(event.getKeyCode()) && !event.isLongPress()) {
        //    return true;
        //}

        //return mGlobalKeyTranslator.translate(event) || super.dispatchKeyEvent(event);
        KeyEvent newEvent = mGlobalKeyTranslator.translate(event);
        return handleNavigation(newEvent) || super.dispatchKeyEvent(newEvent);
    }
    
    private boolean handleNavigation(KeyEvent event) {
        if (event == null) {
            return false;
        }

        // Toggle dialog
        if (!mFragment.isOverlay() && (KeyHelpers.isLeftRightKey(event.getKeyCode()) || KeyHelpers.isMenuKey(event.getKeyCode()))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                finish();
            }
            return true;
        }

        // Notification dialog type. Imitate notification behavior.
        if (mFragment.isOverlay() && (KeyHelpers.isNavigationKey(event.getKeyCode()) || KeyHelpers.isMenuKey(event.getKeyCode()))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                finish();
            }
            PlaybackView view = PlaybackPresenter.instance(this).getView();
            if (view instanceof Fragment) {
                Activity activity = ((Fragment) view).getActivity();
                if (activity != null) {
                    activity.dispatchKeyEvent(event);
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public void finish() {
        // NOTE: Fragment's onDestroy/onDestroyView are not reliable way to catch dialog finish
        Log.d(TAG, "Dialog finish");
        if (mFragment != null) { // fragment isn't created yet (expandable = true)
            // Fix touchscreen/mouse DPAD emulation on API 28+
            // The bug on API 28+ when using touchscreen/mouse to traverse nested dialog categories.
            // In this case the back gesture closes dialog instead of return to the previous category.
            // Cause: touchscreen/mouse input flips the view hierarchy into touch mode, which back-handling
            // treats differently than real D-pad input.
            if (isBackPressed() && mFragment.canGoBack()) {
                mFragment.goBack();
                // No additional event like onResume is fired, so we should reset the state explicitly
                resetBackState();
                return;
            }

            mFragment.onFinish();
        }

        // Destroy dialog when BACK is pressed. NoHistory isn't reliable if combined with singleInstance
        finishReally();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        // Respect PIP mode
        if (ViewManager.instance(this).getTopView() == PlaybackView.class && PlaybackPresenter.instance(this).getContext() instanceof PlaybackActivity) {
            ((PlaybackActivity) PlaybackPresenter.instance(this).getContext()).onUserLeaveHint();
        }

        finish();
    }
}
