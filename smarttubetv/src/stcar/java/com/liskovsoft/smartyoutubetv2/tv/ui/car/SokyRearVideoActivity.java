package com.liskovsoft.smartyoutubetv2.tv.ui.car;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.Locale;

/**
 * Independent rear-passenger VIDEO experiment for displays that Android
 * explicitly enumerates through DisplayManager.DISPLAY_CATEGORY_PRESENTATION.
 *
 * It NEVER uses CarAppService, Android Auto's navigation SurfaceCallback,
 * a fake speed reading, or driving-restriction overrides. Android Auto
 * projection is not itself a display in this category.
 *
 * The user must confirm the physical screen is strictly rear-seat only.
 * Android display enumeration alone does NOT verify screen placement.
 */
public final class SokyRearVideoActivity extends Activity
        implements DisplayManager.DisplayListener {
    private static final int PICK_VIDEO = 4097;

    private DisplayManager displayManager;
    private TextView status;
    private CheckBox passengerOnly;
    private Button pickButton;
    private Button playButton;
    private Button pauseButton;
    private Button backButton;
    private Button forwardButton;

    private Uri chosenVideo;
    private RearPresentation presentation;
    private int activeDisplayId = Display.INVALID_DISPLAY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        buildPhoneControls();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (displayManager != null) {
            displayManager.registerDisplayListener(this, null);
        }
        refreshStatus();
    }

    @Override
    protected void onStop() {
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(this);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        closePresentation();
        super.onDestroy();
    }

    private void buildPhoneControls() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackgroundColor(Color.rgb(19, 25, 37));

        TextView heading = new TextView(this);
        heading.setText("Soky Rear Video");
        heading.setTextSize(24f);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, 0, 0, dp(12));
        panel.addView(heading);

        TextView explanation = new TextView(this);
        explanation.setText("Play a video on a separately addressable rear-passenger display. "
                + "This does not send video through Android Auto's navigation screen.");
        explanation.setTextColor(Color.rgb(226, 232, 241));
        explanation.setTextSize(15f);
        panel.addView(explanation);

        passengerOnly = new CheckBox(this);
        passengerOnly.setText("I confirm the destination screen is only visible to rear passengers");
        passengerOnly.setTextColor(Color.WHITE);
        passengerOnly.setPadding(0, dp(14), 0, dp(6));
        passengerOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) closePresentation();
            updateButtons();
        });
        panel.addView(passengerOnly);

        status = new TextView(this);
        status.setTextColor(Color.rgb(184, 223, 255));
        status.setTextSize(15f);
        status.setPadding(0, dp(12), 0, dp(12));
        panel.addView(status);

        Button rescan = new Button(this);
        rescan.setText("REFRESH DISPLAYS");
        rescan.setOnClickListener(view -> refreshStatus());
        panel.addView(rescan);

        pickButton = new Button(this);
        pickButton.setText("CHOOSE VIDEO");
        pickButton.setOnClickListener(view -> chooseVideo());
        panel.addView(pickButton);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        backButton = button(controls, "-10s", view -> seekBy(-10000));
        playButton = button(controls, "PLAY", view -> controlPlayback(true));
        pauseButton = button(controls, "PAUSE", view -> controlPlayback(false));
        forwardButton = button(controls, "+10s", view -> seekBy(10000));
        panel.addView(controls);

        TextView footer = new TextView(this);
        footer.setText("If you only see 1 phone display and 0 presentation displays, "
                + "your Android Auto projection is not a separately addressable screen. "
                + "This test cannot play through that interface.");
        footer.setTextSize(13f);
        footer.setTextColor(Color.rgb(192, 195, 203));
        footer.setPadding(0, dp(18), 0, 0);
        panel.addView(footer);

        setContentView(panel);
        updateButtons();
    }

    private Button button(LinearLayout row, String label, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12f);
        button.setOnClickListener(action);
        row.addView(button, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Display destination() {
        if (displayManager == null) return null;
        Display[] candidates = displayManager.getDisplays(
                DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        // Do not ever target the phone screen.
        for (Display display : candidates) {
            if (display.getDisplayId() != Display.DEFAULT_DISPLAY &&
                    display.getState() == Display.STATE_ON) {
                return display;
            }
        }
        return null;
    }

    private void refreshStatus() {
        if (status == null) return;
        int phoneVisible = displayManager == null ? 0
                : displayManager.getDisplays().length;
        Display[] outputs = displayManager == null ? new Display[0]
                : displayManager.getDisplays(
                    DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        Display selected = destination();

        String message = String.format(Locale.US,
                "Android-visible displays: %d\nPresentation outputs: %d\n",
                phoneVisible, outputs.length);
        if (selected != null) {
            message += "Secondary output detected: " + selected.getName()
                    + " (ID " + selected.getDisplayId() + ")"
                    + "\nPlacement cannot be verified automatically.";
        } else {
            message += "NO independent presentation display detected. "
                    + "Android Auto projection is not a usable Presentation output.";
        }
        status.setText(message);

        if (presentation != null && (selected == null ||
                selected.getDisplayId() != activeDisplayId)) {
            closePresentation();
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean canChoose = passengerOnly != null && passengerOnly.isChecked()
                && destination() != null;
        if (pickButton != null) pickButton.setEnabled(canChoose);
        boolean playingReady = canChoose && presentation != null;
        if (playButton != null) playButton.setEnabled(playingReady);
        if (pauseButton != null) pauseButton.setEnabled(playingReady);
        if (backButton != null) backButton.setEnabled(playingReady);
        if (forwardButton != null) forwardButton.setEnabled(playingReady);
    }

    private void chooseVideo() {
        if (destination() == null || !passengerOnly.isChecked()) {
            refreshStatus();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_VIDEO || resultCode != RESULT_OK ||
                data == null || data.getData() == null) {
            return;
        }
        chosenVideo = data.getData();
        if (passengerOnly.isChecked()) {
            startOnSecondaryDisplay();
        }
    }

    private void startOnSecondaryDisplay() {
        Display display = destination();
        if (display == null) {
            refreshStatus();
            return;
        }
        closePresentation();
        try {
            RearPresentation newPresentation = new RearPresentation(this, display);
            newPresentation.setOnDismissListener(dialog -> {
                if (presentation == newPresentation) {
                    presentation = null;
                    activeDisplayId = Display.INVALID_DISPLAY;
                    updateButtons();
                }
            });
            newPresentation.show();
            presentation = newPresentation;
            activeDisplayId = display.getDisplayId();
            newPresentation.play(chosenVideo);
            status.append("\nVideo opened on secondary display.");
        } catch (WindowManager.InvalidDisplayException |
                 WindowManager.BadTokenException error) {
            status.append("\nDisplay was rejected by Android: " + error.getMessage());
            closePresentation();
        } catch (RuntimeException error) {
            status.append("\nVideo output failed: " + error.getClass().getSimpleName());
            closePresentation();
        }
        updateButtons();
    }

    private void closePresentation() {
        RearPresentation active = presentation;
        presentation = null;
        activeDisplayId = Display.INVALID_DISPLAY;
        if (active != null) {
            active.stopAndDismiss();
        }
        updateButtons();
    }

    private void controlPlayback(boolean play) {
        if (presentation == null || !passengerOnly.isChecked()) return;
        if (play) presentation.resume(); else presentation.pause();
    }

    private void seekBy(int deltaMs) {
        if (presentation == null || !passengerOnly.isChecked()) return;
        presentation.seekBy(deltaMs);
    }

    @Override public void onDisplayAdded(int displayId) { refreshStatus(); }
    @Override public void onDisplayRemoved(int displayId) { refreshStatus(); }
    @Override public void onDisplayChanged(int displayId) { refreshStatus(); }

    private static final class RearPresentation extends Presentation {
        private VideoView video;

        RearPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            FrameLayout frame = new FrameLayout(getContext());
            frame.setBackgroundColor(Color.BLACK);
            video = new VideoView(getContext());
            FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
            frame.addView(video, fill);
            setContentView(frame);
        }

        void play(Uri uri) {
            if (uri == null || video == null) return;
            video.setVideoURI(uri);
            video.setOnPreparedListener(player -> {
                player.setLooping(false);
                video.start();
            });
            video.setOnErrorListener((player, what, extra) -> true);
            video.requestFocus();
        }

        void resume() {
            if (video != null) video.start();
        }

        void pause() {
            if (video != null && video.isPlaying()) video.pause();
        }

        void seekBy(int deltaMs) {
            if (video == null) return;
            int target = Math.max(0, video.getCurrentPosition() + deltaMs);
            int duration = video.getDuration();
            if (duration > 0) target = Math.min(target, duration);
            video.seekTo(target);
        }

        void stopAndDismiss() {
            if (video != null) video.stopPlayback();
            dismiss();
        }
    }
}
