package com.liskovsoft.smartyoutubetv2.tv.ui.car;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.ScreenManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.validation.HostValidator;

import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;

/**
 * The first step in a DIRECT Soky Tube car integration, not a third-party app.
 *
 * This service deliberately renders NO video. It proves whether an Android 12
 * phone can get an Android Auto host to discover our own CarAppService. A video
 * renderer must not be enabled until that is confirmed and the vehicle's
 * genuine parked state can be enforced. Keep this service out of production.
 */
public final class SokyCarProbeService extends CarAppService {
    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // Local Desktop Head Unit and debug hosts have varying certificates.
        // Never ship an unrestricted host validator in a release APK.
        return BuildConfig.DEBUG
                ? HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
                : new HostValidator.Builder(this).build();
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                return new Screen(getCarContext()) {
                    @NonNull
                    @Override
                    public Template onGetTemplate() {
                        Pane pane = new Pane.Builder()
                                .addRow(new Row.Builder()
                                        .setTitle("Soky Tube connected")
                                        .addText("Car display interface detected.")
                                        .build())
                                .addRow(new Row.Builder()
                                        .setTitle("Surface confirmed on car display")
                                        .addText("1202 x 462 verified in the original vehicle test.")
                                        .build())
                                .addRow(new Row.Builder()
                                        .setTitle("Video gate: BLOCKED")
                                        .addText(SokyCarPlaybackGate.blockReason())
                                        .build())
                                .addAction(new Action.Builder()
                                        .setTitle("Surface Diagnostics")
                                        .setOnClickListener(() -> getCarContext()
                                                .getCarService(ScreenManager.class)
                                                .push(new SokyCarSurfaceProbeScreen(getCarContext())))
                                        .build())
                                .addAction(new Action.Builder()
                                        .setTitle("Passenger Check")
                                        .setOnClickListener(() -> getCarContext()
                                                .getCarService(ScreenManager.class)
                                                .push(new SokyCarPassengerDisplayScreen(getCarContext())))
                                        .build())
                                .build();
                        return new PaneTemplate.Builder(pane)
                                .setTitle("Soky Tube Car - Passenger Display Test")
                                .setHeaderAction(Action.APP_ICON)
                                .build();
                    }
                };
            }
        };
    }
}
