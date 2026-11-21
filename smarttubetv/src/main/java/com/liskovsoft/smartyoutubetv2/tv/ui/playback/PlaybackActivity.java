package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.annotation.TargetApi;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.graphics.Rect;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.app.KeyguardManager;
import android.os.PowerManager;
import android.view.WindowManager;
import android.view.View;
import android.os.Build;
import android.os.Bundle;
import android.content.res.Configuration;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileSystemBars;
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
    // Android 12+ Home auto-PiP can dispatch onStop before PiP=true arrives.
    private boolean mMobileAutoPipHandoffPending;
    private boolean mMobilePipSessionActive;
    private boolean mOpenBrowseAfterPip;
    private boolean mBrowseLaunchedForPip;
    private boolean mMobileScreenOff;
    private boolean mMobileUnlockSurfacePending;
    private boolean mMobileInForeground;
    private int mMobileWakeRepairAttempts;
    private boolean mMobileScreenReceiverRegistered;
    private boolean mMobileRotationRecoveryPending;
    private boolean mMobileRotationFrameMeasured;
    private int mMobileRotationGeneration;
    private long mMobileRotationMeasuredAtMs;
    private boolean mMobileRotationSurfaceBound;
    private final BroadcastReceiver mMobileScreenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                // A power-button lock is NOT a user request for PiP. If video
                // audio is allowed to continue, keep its decoder alive, but
                // never let the watch Activity appear over the keyguard.
                mMobileScreenOff = true;
                mMobileUnlockSurfacePending = true;
                mMobileWakeRepairAttempts = 0;
                mOpenBrowseAfterPip = false;
                if (mPlaybackFragment != null &&
                        (mPlaybackFragment.getPlayWhenReady() ||
                         MobileCarPlaybackPolicy.keepPausedSession(
                                 MobilePlaybackService.hasCarMediaBrowserClient(),
                                 mMobileAudioOnly,
                                 mPlaybackFragment.hasMobilePreparedMedia(),
                                 isFinishing(), false))) {
                    // A paused car session must remain bound to ExoPlayer
                    // across screen-off. Previously only a PLAYING video set
                    // this flag, so Fragment.onStop released paused audio
                    // and Android Auto needed a phone unlock to resume.
                    mPlaybackFragment.keepMobilePlaybackInBackground(true);
                    MobilePlaybackService.start(PlaybackActivity.this);
                }
                updateMobilePipParams(); // setAutoEnterEnabled(false)
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                // Screen on is NOT sufficient: wait for the real keyguard
                // unlock (or an already-unlocked device) before surface repair.
                mMobileScreenOff = false;
                scheduleMobileSurfaceRecovery();
                updateMobilePipParams();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean mobile = MobileUi.isHandheld(this);
        if (mobile) com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication.setMobilePlaybackPhase("creating playback screen");
        setContentView(mobile ? R.layout.fragment_playback_mobile : R.layout.fragment_playback);
        if (mobile) MobileSystemBars.install(this, false);
        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof PlaybackFragment) {
            mPlaybackFragment = (PlaybackFragment) fragment;
        }
        if (mobile && mPlaybackFragment != null) mMobileWatch = new MobileWatchPage(this, mPlaybackFragment);
        if (mobile) {
            // Inherited TV manifests/windows can bypass the phone's lock UI.
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(false);
                setTurnScreenOn(false);
            }
            mMobileScreenOff = !isMobileScreenInteractive();
            registerMobileScreenReceiver();
            com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication
                    .setMobilePlaybackPhase("playback screen attached");
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && MobileUi.isHandheld(this) && !isInPipMode()) {
            updateMobilePlayerWindowMode(
                    getResources().getConfiguration().orientation ==
                            Configuration.ORIENTATION_LANDSCAPE);
            if (mMobileWatch != null) mMobileWatch.onPlayerWindowFocusRestored();
            scheduleMobileSurfaceRecovery();
            if (mMobileRotationRecoveryPending)
                scheduleMobileRotationVideoRecovery(0);
        }
    }

    /** Called from watch layout, but system window flags change only once per
     * mode via MobileSystemBars. Video-frame resizing must never reapply them.
     */
    void updateMobilePlayerWindowMode(boolean landscape) {
        if (MobileUi.isHandheld(this) && !isInPipMode())
            MobileSystemBars.setVideoFullscreen(this, landscape);
    }

    /** Explicit touch control independent of the accelerometer setting. */
    void toggleMobileFullscreen() {
        if (MobileUi.isHandheld(this) && mMobileWatch != null && !isInPipMode())
            mMobileWatch.toggleFullscreen();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (MobileUi.isHandheld(this) && mPlaybackFragment != null) {
            updateMobilePlayerWindowMode(
                    newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
            beginMobileVisualTransitionRecovery();
            if (mMobileWatch != null)
                mMobileWatch.onFullscreenConfigurationChanged();
            // Rebind only AFTER the responsive 16:9 container has measured
            // its new dimensions, otherwise controls use the old orientation.
            getWindow().getDecorView().post(() -> {
                if (!isFinishing() && mPlaybackFragment != null)
                    mPlaybackFragment.onMobileWindowResized();
            });
            // Re-initialize the EXISTING decoder's output only after the new
            // portrait/landscape Surface has actually attached to its window.
            // Android rotation frequently invalidates SurfaceView while
            // ExoPlayer continues outputting audio.
            scheduleMobileRotationVideoRecovery(0);
        }
    }

    /**
     * Enter a compositor recovery epoch for either screen orientation OR
     * system PiP expansion. Both can invalidate the displayed texture even
     * though the decoder keeps playing audio. The existing recovery loop
     * waits for a freshly composited frame after layout changes.
     */
    private void beginMobileVisualTransitionRecovery() {
        mMobileRotationRecoveryPending = !isInPipMode();
        mMobileRotationFrameMeasured = false;
        mMobileRotationMeasuredAtMs = android.os.SystemClock.uptimeMillis();
        mMobileRotationSurfaceBound = false;
        mMobileRotationGeneration++;
    }

    /** A configuration event precedes the actual fullscreen frame resize.
     * Do not treat the OLD surface being valid as proof that the video has
     * recovered before the new layout was measured.
     */
    void onMobileVideoFrameLaidOut(int width, int height) {
        if (!mMobileRotationRecoveryPending || width <= 0 || height <= 0) return;
        mMobileRotationFrameMeasured = true;
        mMobileRotationMeasuredAtMs = android.os.SystemClock.uptimeMillis();
        mMobileRotationSurfaceBound = false;
        // Cancel the older configuration-only timer: wait for the measured
        // fullscreen frame before trying to reattach the player output.
        mMobileRotationGeneration++;
        scheduleMobileRotationVideoRecovery(0);
    }

    private void scheduleMobileRotationVideoRecovery(int attempt) {
        if (!mMobileRotationRecoveryPending) return;
        if (attempt > MobileRotationSurfacePolicy.MAX_ATTEMPTS) {
            mMobileRotationRecoveryPending = false;
            Log.w(TAG, "Mobile fullscreen output unavailable after layout retries");
            captureMobileVideoDiagnostics("no-new-frame-after-retries");
            return;
        }
        final int generation = mMobileRotationGeneration;
        getWindow().getDecorView().postDelayed(() -> {
            if (generation != mMobileRotationGeneration ||
                    !mMobileRotationRecoveryPending || isFinishing() || isInPipMode() ||
                    mPlaybackFragment == null || !isMobileScreenInteractive() ||
                    isMobileKeyguardLocked()) return;
            // A resized SurfaceTexture can arrive after onConfigurationChanged.
            // Wait for actual video-frame dimensions before binding it.
            if (MobileRotationSurfacePolicy.waitForMeasuredFrame(
                    mMobileRotationFrameMeasured, attempt)) {
                scheduleMobileRotationVideoRecovery(attempt + 1);
                return;
            }
            long updatedFrameMs = mPlaybackFragment.lastMobileVideoFrameUptimeMs();
            if (MobileRotationSurfacePolicy.hasNewFrame(
                    updatedFrameMs, mMobileRotationMeasuredAtMs)) {
                // This proves the decoder's output was visibly composited on
                // the NEW fullscreen texture, not merely a still-valid old
                // surface. Stop retries as soon as the first frame arrives.
                mMobileRotationRecoveryPending = false;
                captureMobileVideoDiagnostics("fresh-texture-frame-confirmed");
                return;
            }
            if (MobileRotationSurfacePolicy.shouldRebind(attempt)) {
                mMobileRotationSurfaceBound |=
                        mPlaybackFragment.restoreMobileVideoSurfaceAfterUnlock();
            }
            // A paused video may not produce a new texture frame until play.
            if (mMobileRotationSurfaceBound && !mPlaybackFragment.getPlayWhenReady()) {
                mMobileRotationRecoveryPending = false;
                return;
            }
            // Wait for a rendered frame, with just a few spaced rebinds to
            // avoid repeatedly interrupting a slow hardware decoder.
            scheduleMobileRotationVideoRecovery(attempt + 1);
        }, attempt == 0 ? 100L : 170L);
    }

    /** A user-triggered, privacy-safe report. Captures only player/window
     * geometry and recent texture-frame age, never Google account,
     * video title, URL, history or access tokens.
     */
    private String mobileVideoDiagnosticsSnapshot(String reason) {
        View frame = findViewById(R.id.mobile_player_frame);
        View decor = getWindow().getDecorView();
        long lastFrameMs = mPlaybackFragment != null
                ? mPlaybackFragment.lastMobileVideoFrameUptimeMs() : 0L;
        long nowMs = android.os.SystemClock.uptimeMillis();
        return "Soky Tube mobile v7.13 playback diagnostics" +
                "\nreason: " + reason +
                "\nscreenOrientation: " + getResources().getConfiguration().orientation +
                "\npiP: " + isInPipMode() +
                "\nwindowFocus: " + decor.hasWindowFocus() +
                "\nplayerReady: " + (mPlaybackFragment != null &&
                        mPlaybackFragment.isEngineInitialized()) +
                "\nplayWhenReady: " + (mPlaybackFragment != null &&
                        mPlaybackFragment.getPlayWhenReady()) +
                "\nframeMeasured: " + mMobileRotationFrameMeasured +
                "\nframeSize: " + (frame == null ? "missing" :
                        frame.getWidth() + "x" + frame.getHeight()) +
                "\nframeScale: " + (frame == null ? "n/a" :
                        frame.getScaleX() + "," + frame.getScaleY()) +
                "\nframeTranslation: " + (frame == null ? "n/a" :
                        frame.getTranslationX() + "," + frame.getTranslationY()) +
                "\nlastCompositedFrameAgoMs: " + (lastFrameMs > 0 ?
                        nowMs - lastFrameMs : -1) +
                "\nrotationPending: " + mMobileRotationRecoveryPending +
                "\ncarBrowserConnected: " + MobilePlaybackService.hasCarMediaBrowserClient();
    }

    private void captureMobileVideoDiagnostics(String reason) {
        if (!MobileUi.isHandheld(this)) return;
        getSharedPreferences("soky_video_diagnostics", MODE_PRIVATE).edit()
                .putString("last", mobileVideoDiagnosticsSnapshot(reason))
                .apply();
    }

    void copyMobileVideoDiagnostics() {
        String previous = getSharedPreferences("soky_video_diagnostics", MODE_PRIVATE)
                .getString("last", "No completed fullscreen transition recorded.");
        String report = previous + "\n\n" +
                mobileVideoDiagnosticsSnapshot("user-requested-current-state");
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText(
                    "Soky Tube playback diagnostics", report));
            android.widget.Toast.makeText(this,
                    "Playback diagnostics copied", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isMobileScreenInteractive() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        return manager == null || manager.isInteractive();
    }

    private boolean isMobileKeyguardLocked() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isKeyguardLocked();
    }

    private void registerMobileScreenReceiver() {
        if (mMobileScreenReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(mMobileScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else
                registerReceiver(mMobileScreenReceiver, filter);
            mMobileScreenReceiverRegistered = true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Mobile screen receiver unavailable: " + e.getMessage());
        }
    }

    /** The UI may resume BEFORE the device unlock animation is finished. */
    private void scheduleMobileSurfaceRecovery() {
        if (!mMobileUnlockSurfacePending || !mMobileInForeground ||
                isFinishing() || mPlaybackFragment == null) return;
        if (!MobileScreenTransitionPolicy.shouldRestoreVideoSurface(
                mMobileUnlockSurfacePending, isMobileScreenInteractive(),
                isMobileKeyguardLocked(), isInPipMode(), true)) return;
        if (!getWindow().getDecorView().hasWindowFocus()) return;
        getWindow().getDecorView().post(() -> {
            if (!mMobileUnlockSurfacePending || !mMobileInForeground ||
                    !isMobileScreenInteractive() || isMobileKeyguardLocked() ||
                    isInPipMode() || isFinishing()) return;
            if (mPlaybackFragment.restoreMobileVideoSurfaceAfterUnlock()) {
                mMobileUnlockSurfacePending = false;
                mMobileWakeRepairAttempts = 0;
                if (mMobileWatch != null) mMobileWatch.applyLayout();
            } else if (++mMobileWakeRepairAttempts <= 5) {
                getWindow().getDecorView().postDelayed(
                        this::scheduleMobileSurfaceRecovery, 200);
            }
        });
    }

    /** Refresh prepared Android 12+ auto-PiP when ExoPlayer truly starts. */
    void onMobilePlaybackReadinessChanged(boolean playing) {
        if (MobileUi.isHandheld(this) && !isFinishing()) updateMobilePipParams();
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
        MobilePlaybackService.setExplicitAudioOnly(true);
        mMobilePipTransition = false;
        mMobilePipSessionActive = false;
        mOpenBrowseAfterPip = false;
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
        MobilePlaybackService.setExplicitAudioOnly(false);
        mMobilePipTransition = true;
        mOpenBrowseAfterPip = browse;
        mBrowseLaunchedForPip = false;
        mPlaybackFragment.keepMobilePlaybackInBackground(true);
        MobilePlaybackService.start(this);
        try {
            updateMobilePipParams();
            PictureInPictureParams.Builder params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(isMobileShorts() ? 9 : 16,
                            isMobileShorts() ? 16 : 9));
            View viewport = findViewById(R.id.mobile_player_frame);
            Rect source = mobilePipSourceRect(viewport);
            if (source != null) params.setSourceRectHint(source);
            if (Build.VERSION.SDK_INT >= 31) params.setSeamlessResizeEnabled(true);
            boolean entered = enterPictureInPictureMode(params.build());
            if (entered) {
                mMobilePipSessionActive = true;
                // Do not assume Android completes the transition in 180 ms.
                // The lifecycle callback will open Browse when PiP is visible.
                // This fallback handles OEMs that delay/omit that callback.
                if (browse) getWindow().getDecorView().postDelayed(
                        this::launchBrowseOncePipIsVisible, 650);
            } else {
                mMobilePipTransition = false;
                mOpenBrowseAfterPip = false;
                mPlaybackFragment.keepMobilePlaybackInBackground(false);
                MobilePlaybackService.stop(this);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            Log.e(TAG, "Mobile PiP unavailable: " + e.getMessage());
            mMobilePipTransition = false;
            mOpenBrowseAfterPip = false;
            mPlaybackFragment.keepMobilePlaybackInBackground(false);
            MobilePlaybackService.stop(this);
        }
    }

    private void launchBrowseOncePipIsVisible() {
        if (!mOpenBrowseAfterPip || mBrowseLaunchedForPip ||
                isFinishing() || !isInPipMode()) return;
        mBrowseLaunchedForPip = true;
        mOpenBrowseAfterPip = false;
        // Bring the EXISTING browsing task to the front without replacing the
        // one video decoder playing in PiP. Browse remains fully interactive.
        Intent intent = new Intent(this,
                com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void stopClosedMobilePip() {
        if (mMobileAudioOnly || mPlaybackFragment == null) return;
        MobilePlaybackService.setExplicitAudioOnly(false);
        mMobilePipSessionActive = false;
        mMobilePipTransition = false;
        mMobileAutoPipHandoffPending = false;
        mOpenBrowseAfterPip = false;
        mPlaybackFragment.setPlayWhenReady(false);
        mPlaybackFragment.keepMobilePlaybackInBackground(false);
        // The original TV background block otherwise prevents engine release.
        mPlaybackFragment.blockEngine(false);
        mPlaybackFragment.maybeReleasePlayer();
        MobilePlaybackService.stop(this);
    }

    private Rect mobilePipSourceRect(View viewport) {
        if (viewport == null || viewport.getWidth() <= 0 || viewport.getHeight() <= 0)
            return null;
        // getGlobalVisibleRect can clip the transformed video to the original
        // 16:9 parent. Supply the ACTUAL scaled rectangle in screen space as
        // the native PiP animation's source.
        int[] location = new int[2];
        viewport.getLocationOnScreen(location);
        int width = Math.max(1, Math.round(viewport.getWidth() * viewport.getScaleX()));
        int height = Math.max(1, Math.round(viewport.getHeight() * viewport.getScaleY()));
        return new Rect(location[0], location[1],
                location[0] + width, location[1] + height);
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
        Rect source = mobilePipSourceRect(viewport);
        if (source != null) params.setSourceRectHint(source);
        if (Build.VERSION.SDK_INT >= 31) {
            params.setAutoEnterEnabled(MobileScreenTransitionPolicy.shouldAutoEnterPip(
                    mPlaybackFragment != null && mPlaybackFragment.getPlayWhenReady(),
                    mMobileAudioOnly, !mMobileScreenOff && isMobileScreenInteractive(),
                    isMobileKeyguardLocked(), isFinishing(), isInPipMode(),
                    mMobilePipTransition,
                    AppDialogPresenter.instance(this).isDialogShown()));
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
        if (MobileUi.isHandheld(this)) {
            mMobileInForeground = true;
            if (isMobileScreenInteractive() && !isMobileKeyguardLocked())
                mMobileScreenOff = false;
        }
        if (MobileUi.isHandheld(this) && !isInPipMode())
            updateMobilePlayerWindowMode(
                    getResources().getConfiguration().orientation ==
                            Configuration.ORIENTATION_LANDSCAPE);
        if (mMobileWatch != null) mMobileWatch.resume();
        if (MobileUi.isHandheld(this)) {
            mMobileAudioOnly = false;
            MobilePlaybackService.setExplicitAudioOnly(false);
            // Expanded PiP returns to a resumed full-screen player. A dismissed
            // PiP does not resume the Activity; onStop instead stops the engine.
            if (!isInPipMode()) {
                mMobilePipSessionActive = false;
                mMobilePipTransition = false;
                mMobileAutoPipHandoffPending = false;
            }
            updateMobilePipParams();
            scheduleMobileSurfaceRecovery();
        }
        // Only the compact mobile touch player is shown in the video area.
        // The TV Leanback control row remains hidden on handheld devices.
    }
    @Override protected void onStop() {
        if (mMobileWatch != null) mMobileWatch.stop();
        if (MobileUi.isHandheld(this) && mPlaybackFragment != null) {
            // PiP dismissal must end playback even if Android reports the
            // screen OFF or the power manager temporarily noninteractive.
            // Those conditions previously let the audio service escape.
            if (!isInPipMode() && MobilePipLifecyclePolicy.shouldStopOnStop(
                    mMobilePipSessionActive, mMobileAudioOnly,
                    mMobilePipTransition || mMobileAutoPipHandoffPending,
                    isChangingConfigurations(), isFinishing())) {
                stopClosedMobilePip();
            } else if (MobileCarPlaybackPolicy.keepPausedSession(
                    MobilePlaybackService.hasCarMediaBrowserClient(),
                    mMobileAudioOnly,
                    mPlaybackFragment.hasMobilePreparedMedia(),
                    isFinishing(),
                    mMobilePipSessionActive && !isInPipMode())) {
                // onStop happens BEFORE Fragment.onStop; retain its decoder
                // and the MediaSessionConnector even while playWhenReady=false.
                mPlaybackFragment.keepMobilePlaybackInBackground(true);
                MobilePlaybackService.start(this);
            } else if (mPlaybackFragment.getPlayWhenReady()) {
                mPlaybackFragment.keepMobilePlaybackInBackground(true);
            }
        }
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        if (mMobileScreenReceiverRegistered) {
            try {
                unregisterReceiver(mMobileScreenReceiver);
            } catch (IllegalArgumentException ignored) {}
            mMobileScreenReceiverRegistered = false;
        }
        if (MobileUi.isHandheld(this) && !isChangingConfigurations() &&
                !mMobileAudioOnly && mPlaybackFragment != null) {
            stopClosedMobilePip();
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
        if (MobileUi.isHandheld(this))
            getTheme().applyStyle(R.style.SokyMobileSafeAreaOverlay, true);
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
                stopClosedMobilePip();
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
        mMobileInForeground = false;
        if (MobileUi.isHandheld(this) && mPlaybackFragment != null &&
                MobileCarPlaybackPolicy.keepPausedSession(
                        MobilePlaybackService.hasCarMediaBrowserClient(),
                        mMobileAudioOnly, mPlaybackFragment.hasMobilePreparedMedia(),
                        isFinishing(),
                        mMobilePipSessionActive && !isInPipMode())) {
            // Start/retain the media playback service while this Activity is
            // still transitioning to the background, BEFORE a locked device
            // restricts new foreground-service launches.
            mPlaybackFragment.keepMobilePlaybackInBackground(true);
            MobilePlaybackService.start(this);
        }
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
        if (MobileUi.isHandheld(this) && !isInPictureInPictureMode &&
                mMobilePipSessionActive) {
            // Native PiP expanded: the operating system has already done
            // the window animation. Reset the touch transform immediately
            // and verify the FIRST new composited video frame. There may be
            // no orientation change, so onConfigurationChanged isn't called.
            beginMobileVisualTransitionRecovery();
        }
        if (mMobileWatch != null) {
            mMobileWatch.setPipMode(isInPictureInPictureMode);
            mMobileWatch.setControlsShown(!isInPictureInPictureMode);
        }
        if (MobileUi.isHandheld(this)) {
            if (isInPictureInPictureMode) {
                mMobilePipSessionActive = true;
                mMobilePipTransition = false;
                mMobileAutoPipHandoffPending = false;
                if (mPlaybackFragment != null) {
                    // Android 12 may enter PiP automatically, bypassing
                    // enterMobilePip(), so retain the SAME decoder/session.
                    mPlaybackFragment.keepMobilePlaybackInBackground(true);
                    MobilePlaybackService.start(this);
                }
                // The platform reports PiP only once it is actually active:
                // reopen Home here, not on a fixed 180 ms assumption.
                getWindow().getDecorView().post(this::launchBrowseOncePipIsVisible);
            } else {
                // PiP=false can mean expand or dismissal. Only a resumed,
                // visible playback screen is an expansion; otherwise the
                // original player and foreground audio must terminate.
                mMobilePipTransition = false;
                getWindow().getDecorView().postDelayed(() -> {
                    if (MobilePipLifecyclePolicy.shouldStopAfterPipExit(
                            mMobilePipSessionActive, mMobileInForeground,
                            mMobileAudioOnly, isInPipMode(), isChangingConfigurations(),
                            mMobileAutoPipHandoffPending)) {
                        stopClosedMobilePip();
                    }
                }, 500);
            }
        }
    }

    /**
     * HOME or BACK pressed
     */
    @Override
    public void onUserLeaveHint() {
        if (MobileUi.isHandheld(this)) {
            if (isFinishing() || isBackPressed() || mMobilePipTransition ||
                    isInPipMode() || getViewManager().isNewViewPending()
                    || AppDialogPresenter.instance(this).isDialogShown()) return;
            if (mMobileAudioOnly) {
                MobilePlaybackService.start(this);
                return;
            }
            if (mPlaybackFragment != null &&
                    MobileScreenTransitionPolicy.shouldAutoEnterPip(
                            mPlaybackFragment.getPlayWhenReady(), mMobileAudioOnly,
                            !mMobileScreenOff && isMobileScreenInteractive(),
                            isMobileKeyguardLocked(), isFinishing(),
                            isInPipMode(), mMobilePipTransition, false)) {
                // User deliberately leaves via Home or Recents. Do NOT treat
                // a power-button lock as this action.
                mPlaybackFragment.keepMobilePlaybackInBackground(true);
                MobilePlaybackService.start(this);
                if (Build.VERSION.SDK_INT < 31) {
                    enterMobilePip(false);
                } else {
                    // Home does NOT necessarily deliver PiP=true before onStop.
                    // Prevent the PiP-dismissal path from pausing a still-playing
                    // video during the Android 12+ automatic PiP handoff.
                    mMobileAutoPipHandoffPending = true;
                    updateMobilePipParams(); // preserve the OS auto-enter animation
                    getWindow().getDecorView().postDelayed(() -> {
                        if (!mMobileAutoPipHandoffPending) return;
                        mMobileAutoPipHandoffPending = false;
                        // No successful PiP callback: fail closed rather than
                        // leave an invisible player/audio service running.
                        if (!isInPipMode() && !mMobileInForeground &&
                                !mMobileAudioOnly && mPlaybackFragment != null)
                            stopClosedMobilePip();
                    }, 3000);
                }
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
