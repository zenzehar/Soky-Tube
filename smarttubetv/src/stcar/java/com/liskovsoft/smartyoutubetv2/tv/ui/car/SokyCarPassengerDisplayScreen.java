package com.liskovsoft.smartyoutubetv2.tv.ui.car;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

/**
 * Diagnostic only: investigate whether the existing phone/car setup exposes an
 * independently addressable passenger display to Soky Tube.
 *
 * IMPORTANT: display topology and presentation capability do NOT establish
 * physical rear-seat placement. Projected Android Auto sends an image to its
 * host; that projection is not necessarily an Android Display in the phone's
 * DisplayManager. It must not be misclassified as a passenger output.
 */
final class SokyCarPassengerDisplayScreen extends Screen {

    SokyCarPassengerDisplayScreen(@NonNull CarContext carContext) {
        super(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext context = getCarContext();
        boolean nativeAutomotive = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        DisplayManager manager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = manager == null ? new Display[0] : manager.getDisplays();
        Display[] presentations = manager == null
                ? new Display[0]
                : manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);

        int externalCount = 0;
        for (Display display : displays) {
            if (display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                externalCount++;
            }
        }

        // A virtual/presentation display is NOT proof that the screen is
        // rear-only; the host must provide trusted display-specific metadata.
        Pane pane = new Pane.Builder()
                .addRow(new Row.Builder()
                        .setTitle("Host platform")
                        .addText(nativeAutomotive
                                ? "Native Android Automotive OS"
                                : "Android phone projection (not native Automotive OS)")
                        .build())
                .addRow(new Row.Builder()
                        .setTitle("Android-visible display topology")
                        .addText("Total: " + displays.length
                                + " | Non-default: " + externalCount
                                + " | Presentation-capable: " + presentations.length)
                        .build())
                .addRow(new Row.Builder()
                        .setTitle("Passenger-only output: UNVERIFIED")
                        .addText("Android Auto does not label this Surface as rear-seat-only. "
                                + "An external display alone does not prove passenger placement.")
                        .build())
                .build();

        return new PaneTemplate.Builder(pane)
                .setTitle("Soky Car - Passenger Display Check")
                .setHeaderAction(Action.BACK)
                .build();
    }
}
