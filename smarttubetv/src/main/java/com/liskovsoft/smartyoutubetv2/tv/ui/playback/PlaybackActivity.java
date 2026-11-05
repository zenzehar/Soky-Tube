package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.annotation.TargetApi;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.graphics.Rect;
import android.content.Intent;
import android.view.View;
import android.os.Build;
import android.os.Bundle;
import android.content.res.Configuration;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngine;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

/**
 * Loads PlaybackFragment and delegates input from a game controller.
 * <br>
 * For more information on game controller capabilities with leanback, review the
 * <a href="https://developer.android.com/training/game-controllers/controller-input.html">docs</href>.
 */
public class PlaybackActivity extends LeanbackActivity {
    private static final String TAG = PlaybackActivity.class.getSimpleName();
    private static final float GAMEPAD_TRIGGER_INTENSITY_ON = 0.5f;
    // Off-condition slightly smaller for button debouncing.
    private static final float GAMEPAD_TRIGGER_INTENSITY_OFF = 0.45f;
    private boolean gamepadTriggerPressed = false;
    private PlaybackFragment mPlaybackFragment;
    private MobileWatchPage mMobileWatch;
    private boolean mMobileAudioOnly;
    private boolean mMobilePipTransition;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean mobile = MobileUi.isHandheld(this);
        if (mobile) com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication.setMobilePlaybackPhase("creating playback screen");
        setContentView(mobile ? R.layout.fragment_playback_mobile : R.layout.fragment_playback);
        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof PlaybackFragment) {
            mPlaybackFragment = (PlaybackFragment) fragment;
        }
        if (mobile && mPlaybackFragment != null) mMobileWatch = new MobileWatchPage(this, mPlaybackFragment);
        if (mobile) com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication.setMobilePlaybackPhase("playback screen attached");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (MobileUi.isHandheld(this) && mPlaybackFragment != null) {
            if (mMobileWatch != null) mMobileWatch.applyLayout();
            // Rebind only AFTER the responsive 16:9 container has measured
            // its new dimensions, otherwise controls use the old orientation.
            getWindow().getDecorView().post(() -> {
                if (!isFinishing() && mPlaybackFragment != null)
                    mPlaybackFragment.onMobileWindowResized();
            });
        }
    }

    void onMobileVideoChanged(com.liskovsoft.smartyoutubetv2.common.app.models.data.Video video) {
        if (mMobileWatch != null) mMobileWatch.onVideoChanged(video);
        if (MobileUi.isHandheld(this) && video != null && mPlaybackFragment != null) {
            com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication
                    .setMobilePlaybackPhase("video selected / awaiting decoder");
            // v7.2 crash isolation: do NOT start a foreground service from
            // video metadata delivery. The media player may not be ready yet.
            // PiP and explicit Background audio still start the service.
            // Keep the foreground player and native TV code untouched.
            updateMobilePipParams();
        }
    }
    void onMobileSuggestionsChanged(androidx.leanback.widget.ObjectAdapter rows) {
        if (mMobileWatch != null) mMobileWatch.onSuggestions(rows);
    }
    void onMobileControlsRequested(boolean show) {
        if (mMobileWatch != null) mMobileWatch.setControlsShown(show);
    }
    void onMobileSponsorSegmentsChanged(
            java.util.List<com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment> segments) {
        if (mMobileWatch != null) mMobileWatch.setSponsorSegments(segments);
    }
    void onMobileChaptersChanged(
            java.util.List<com.liskovsoft.smartyoutubetv2.common.app.models.data.Video> chapters) {
        if (mMobileWatch != null) mMobileWatch.setChapters(chapters);
    }

    public void startMobileBackgroundAudio() {
        if (!MobileUi.isHandheld(this) || mPlaybackFragment == null) return;
        mMobileAudioOnly = true;
        mMobilePipTransition = false;
        mPlaybackFragment.keepMobilePlaybackInBackground(true);
        MobilePlaybackService.start(this);
        // The foreground session owns media notification / steering buttons.
        // Keep the existing ExoPlayer rather than duplicating audio playback.
        moveTaskToBack(true);
    }

    public void enterMobilePip(boolean browse) {
        if (!MobileUi.isHandheld(this) || mPlaybackFragment == null ||
                Build.VERSION.SDK_INT < 26 || !getPackageManager()
                .hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE))
            return;
        if (isInPipMode()) return;
        mMobileAudioOnly = false;
        mMobilePipTransition = true;
        mPlaybackFragment.keepMobilePlaybackInBackground(true);
        MobilePlaybackService.start(this);
        try {
            updateMobilePipParams();
            if (enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(isMobileShorts() ? 9 : 16,
                            isMobileShorts() ? 16 : 9)).build())) {
                if (browse) {
                    // PiP remains visible while the Browse activity comes to the
                    // foreground. Do not instantiate or attach a second player.
                    getWindow().getDecorView().postDelayed(() -> {
                        if (!isFinishing() && isInPipMode()) {
                            Intent intent = new Intent(this,
                              com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity.class);
                            startActivity(intent);
                        }
                    }, 180);
                }
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            Log.e(TAG, "Mobile PiP unavailable: " + e.getMessage());
            mMobilePipTransition = false;
        }
    }

    private boolean isMobileShorts() {
        return mPlaybackFragment != null && mPlaybackFragment.getVideo() != null &&
                mPlaybackFragment.getVideo().isShorts;
    }

    private void updateMobilePipParams() {
        if (!MobileUi.isHandheld(this) || Build.VERSION.SDK_INT < 26) return;
        PictureInPictureParams.Builder params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(isMobileShorts() ? 9 : 16,
                        isMobileShorts() ? 16 : 9));
        View viewport = findViewById(R.id.mobile_player_frame);
        if (viewport != null && viewport.getWidth() > 0 && viewport.getHeight() > 0) {
            Rect bounds = new Rect();
            if (viewport.getGlobalVisibleRect(bounds)) params.setSourceRectHint(bounds);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            params.setAutoEnterEnabled(!mMobileAudioOnly && mPlaybackFragment != null
                    && mPlaybackFragment.getPlayWhenReady());
            params.setSeamlessResizeEnabled(true);
        }
        try {
            setPictureInPictureParams(params.build());
        } catch (IllegalStateException | IllegalArgumentException e) {
            Log.e(TAG, "Unable to update PiP parameters: " + e.getMessage());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (mMobileWatch != null) mMobileWatch.resume();
        if (MobileUi.isHandheld(this)) {
            mMobileAudioOnly = false;
            updateMobilePipParams();
        }
        // Only the compact mobile touch player is shown in the video area.
        // The TV Leanback control row remains hidden on handheld devices.
    }
    @Override protected void onStop() {
        if (mMobileWatch != null) mMobileWatch.stop();
        if (MobileUi.isHandheld(this) && mPlaybackFragment != null) {
            if (isFinishing() && !mMobileAudioOnly) {
                // Swiping away/closing PiP is an EXIT, not a request to keep
                // an invisible video playing forever in the background.
                mPlaybackFragment.setPlayWhenReady(false);
                mPlaybackFragment.keepMobilePlaybackInBackground(false);
                MobilePlaybackService.stop(this);
            } else if (mPlaybackFragment.getPlayWhenReady()) {
                mPlaybackFragment.keepMobilePlaybackInBackground(true);
            }
        }
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        if (MobileUi.isHandheld(this) && !mMobileAudioOnly && mPlaybackFragment != null) {
            // PiP close can destroy the Activity without first invoking
            // onPictureInPictureModeChanged(false). Always stop owned playback.
            mPlaybackFragment.setPlayWhenReady(false);
            mPlaybackFragment.keepMobilePlaybackInBackground(false);
            MobilePlaybackService.stop(this);
        }
        if (mMobileWatch != null) mMobileWatch.release();
        mMobileWatch = null;
        super.onDestroy();
    }

    @Override
    protected void initTheme() {
        int playerThemeResId = MainUIData.instance(this).getColorScheme().playerThemeResId;
        if (playerThemeResId > 0) {
            setTheme(playerThemeResId);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchKeyEvent(event);
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchTouchEvent(event);
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchGenericMotionEvent(event);
        }

        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (MobileUi.isHandheld(this) && mPlaybackFragment != null &&
                event.getRepeatCount() == 0) {
            // Fallback for direct steering-wheel/Bluetooth media key delivery.
            // Background/Android Auto commands are handled by MediaSession.
            if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                mPlaybackFragment.skipToNext();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                mPlaybackFragment.skipToPrevious();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                mPlaybackFragment.setPlayWhenReady(!mPlaybackFragment.getPlayWhenReady());
                MobilePlaybackService.refresh();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                mPlaybackFragment.setPlayWhenReady(true);
                MobilePlaybackService.refresh();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                mPlaybackFragment.setPlayWhenReady(false);
                MobilePlaybackService.refresh();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            mPlaybackFragment.skipToNext();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            mPlaybackFragment.skipToPrevious();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            mPlaybackFragment.rewind();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            mPlaybackFragment.fastForward();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // This method will handle gamepad events.
        if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.rewind();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.fastForward();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF
                && event.getAxisValue(MotionEvent.AXIS_RTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF) {
            gamepadTriggerPressed = false;
        } else if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0 && event.getAction() == MotionEvent.ACTION_SCROLL) {
            // mouse wheel handling
            Utils.volumeUp(this, getPlaybackView(), event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    // For N devices that support it, not "officially"
    // More: https://medium.com/s23nyc-tech/drop-in-android-video-exoplayer2-with-picture-in-picture-e2d4f8c1eb30
    @TargetApi(24)
    @SuppressWarnings("deprecation")
    private void enterPipMode() {
        // NOTE: When exiting PIP mode onPause is called immediately after onResume

        // Also, avoid enter pip on stop!
        // More info: https://developer.android.com/guide/topics/ui/picture-in-picture#continuing_playback

        if (Helpers.isPictureInPictureSupported(this)) {
            if (wannaEnterToPip()) {
                Log.d(TAG, "Entering PIP mode...");

                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        PictureInPictureParams.Builder params = new PictureInPictureParams.Builder();
                        enterPictureInPictureMode(params.build());
                    } else {
                        enterPictureInPictureMode();
                    }
                } catch (Exception e) {
                    // Device doesn't support picture-in-picture mode
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    /**
     * BACK pressed, PIP player's button pressed
     */
    @Override
    public void finish() {
        Log.d(TAG, "Finishing activity...");
        if (MobileUi.isHandheld(this)) {
            // Legacy SmartTube TV finish() deliberately re-enters PiP and
            // blocks destruction. On a phone the X on a floating window
            // instead means STOP: no invisible playback or stale notification.
            if (!mMobileAudioOnly && mPlaybackFragment != null) {
                mPlaybackFragment.setPlayWhenReady(false);
                mPlaybackFragment.keepMobilePlaybackInBackground(false);
                MobilePlaybackService.stop(this);
            }
            super.finishReally();
            return;
        }

        //if (isBackgroundBackEnabled()) {
        //    mPlaybackFragment.blockEngine(true);
        //}

        // NOTE: When exiting PIP mode onPause is called immediately after onResume

        // Also, avoid enter pip on stop!
        // More info: https://developer.android.com/guide/topics/ui/picture-in-picture#continuing_playback

        // NOTE: block back button for PIP.
        // User pressed PIP button in the player.
        if (!skipPip()) {
            enterPipMode(); // NOTE: without this call app will hangs when pressing on PIP button
        }

        if (doNotDestroy() && !skipPip()) {
            mPlaybackFragment.blockEngine(true);
            // Ensure to opening this activity when the user is returning to the app
            getViewManager().blockTop(this);
            getViewManager().startParentView(this);
        } else {
            if (getPlayerTweaksData().isKeepFinishedActivityEnabled()) {
                //moveTaskToBack(true); // Don't do this or you'll have problems when player overlaps other apps (e.g. casting)
                getViewManager().startParentView(this);

                // Player with TextureView keeps running in background because onStop() fired with huge delay (~5sec).
                mPlaybackFragment.maybeReleasePlayer();
            } else {
                super.finish();
            }
        }
    }

    @Override
    public void finishReally() {
        super.finishReally();

        mPlaybackFragment.onFinish();
    }

    @Override
    protected void onPause() {
        boolean hasDialogBug = AppDialogPresenter.instance(this).isDialogShown() && Build.VERSION.SDK_INT <= 23;
        boolean isScreenOff = getPlayerData().getBackgroundMode() != PlayerData.BACKGROUND_MODE_DEFAULT && Utils.isHardScreenOff(this);

        if (hasDialogBug || isScreenOff) {
            mPlaybackFragment.blockEngine(true);
        }

        // Run the code before the contained fragment
        super.onPause();
    }

    @SuppressWarnings("deprecation")
    private void enterBackgroundPlayMode() {
        if (Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT < 26) {
            if (Build.VERSION.SDK_INT == 21) {
                // Playback pause fix?
                mPlaybackFragment.showOverlay(true);
            }

            if (mPlaybackFragment.isPlaying()) {
                // Argument equals true to notify the system that the activity
                // wishes to be visible behind other translucent activities
                if (!requestVisibleBehind(true)) {
                    // App-specific method to stop playback and release resources
                    // because call to requestVisibleBehind(true) failed
                    mPlaybackFragment.onDestroy();
                }
            } else {
                // Argument equals false because the activity is not playing
                requestVisibleBehind(false);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onVisibleBehindCanceled() {
        // App-specific method to stop playback and release resources
        mPlaybackFragment.onDestroy();
        super.onVisibleBehindCanceled();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        mPlaybackFragment.onPIPChanged(isInPictureInPictureMode);
        if (mMobileWatch != null) {
            mMobileWatch.setPipMode(isInPictureInPictureMode);
            mMobileWatch.setControlsShown(!isInPictureInPictureMode);
        }
        if (!isInPictureInPictureMode) mMobilePipTransition = false;
    }

    /**
     * HOME or BACK pressed
     */
    @Override
    public void onUserLeaveHint() {
        if (MobileUi.isHandheld(this)) {
            if (isFinishing() || isBackPressed() || getViewManager().isNewViewPending()
                    || AppDialogPresenter.instance(this).isDialogShown()) return;
            if (mMobileAudioOnly) {
                MobilePlaybackService.start(this);
                return;
            }
            if (mPlaybackFragment != null && mPlaybackFragment.getPlayWhenReady()) {
                MobilePlaybackService.start(this);
                // Android 12+ auto-enter is smoother; older phones need an
                // explicit entry on Home/recents gesture or hardware button.
                if (Build.VERSION.SDK_INT < 31) enterMobilePip(false);
                else updateMobilePipParams();
            }
            return;
        }
        // Check that user not open dialog/search activity instead of really leaving the activity
        // Activity may be overlapped by the dialog, back is pressed or new view started
        if (isBackPressed() || isFinishing() || getViewManager().isNewViewPending() || getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK) {
            return;
        }

        switch (getPlayerData().getBackgroundMode()) {
            case PlayerData.BACKGROUND_MODE_PLAY_BEHIND:
                enterBackgroundPlayMode();
                // Do we need to do something additional when running Play Behind?
                break;
            case PlayerData.BACKGROUND_MODE_PIP:
                enterPipMode();
                if (doNotDestroy()) {
                    mPlaybackFragment.blockEngine(true);
                    // Ensure to opening this activity when the user will return to the app
                    getViewManager().blockTop(this);
                    // Enable collapse app to Home launcher
                    //getViewManager().enableMoveToBack(true);
                }
                break;
            case PlayerData.BACKGROUND_MODE_SOUND:
                if (doNotDestroy()) {
                    // Ensure to continue a playback
                    mPlaybackFragment.blockEngine(true);
                    getViewManager().blockTop(this);
                    //getViewManager().enableMoveToBack(true);
                }
                break;
        }
    }

    public boolean isInPipMode() {
        if (Build.VERSION.SDK_INT < 24) {
            return false;
        }

        return isInPictureInPictureMode();
    }

    public PlaybackView getPlaybackView() {
        return mPlaybackFragment;
    }

    private boolean skipPip() {
        return isBackPressed() && getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME;
    }

    private boolean isEngineBlocked() {
        return mPlaybackFragment != null && mPlaybackFragment.isEngineBlocked();
    }

    @TargetApi(24)
    private boolean wannaEnterToPip() {
        //return mPlaybackFragment != null && mPlaybackFragment.getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_PIP && !isInPictureInPictureMode();
        //return mPlaybackFragment != null && mPlaybackFragment.isEngineBlocked() && !isInPictureInPictureMode();
        boolean isPip = getPlayerData().getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP || isEngineBlocked();
        return isPip && !isInPictureInPictureMode();
    }

    private boolean doNotDestroy() {
        sIsInPipMode = isInPipMode();
        //return sIsInPipMode || mPlaybackFragment.getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_SOUND;
        //return sIsInPipMode || mPlaybackFragment.isEngineBlocked();
        boolean isBackground = getPlayerData().getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_SOUND || isEngineBlocked();
        return sIsInPipMode || isBackground;
    }

    //private boolean isBackgroundBackEnabled() {
    //    return getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK ||
    //            getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK;
    //}
}
