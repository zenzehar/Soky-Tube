package com.liskovsoft.smartyoutubetv2.tv.ui.car;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;

/**
 * CAR_LAUNCHER-only probe for hosts supporting direct parked Activities.
 *
 * No media, privileged actions, screen projection or video is started here.
 * The car's decision to list this Activity is deliberately left to the host.
 */
public final class SokyCarProbeActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        TextView notice = new TextView(this);
        notice.setBackgroundColor(Color.rgb(14, 17, 23));
        notice.setTextColor(Color.WHITE);
        notice.setTextSize(21);
        notice.setGravity(Gravity.CENTER);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        notice.setPadding(padding, padding, padding, padding);
        notice.setText("Soky Tube Car - Compatibility Test\n\n" +
                "The car has opened Soky Tube's own display.\n\n" +
                "Video is disabled until parked-mode verification is available.");
        setContentView(notice);
    }
}
