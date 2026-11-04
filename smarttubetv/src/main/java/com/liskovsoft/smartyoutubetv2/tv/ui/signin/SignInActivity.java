package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import androidx.leanback.app.GuidedStepSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;

public class SignInActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == savedInstanceState) {
            if (MobileUi.isHandheld(this)) {
                // Use a phone-native sign-in view backed by the SAME presenter.
                // TV retains GuidedStepSupportFragment unchanged.
                getSupportFragmentManager().beginTransaction()
                        .replace(android.R.id.content, new MobileSignInFragment(), "mobile_signin")
                        .commitNow();
            } else {
                GuidedStepSupportFragment.addAsRoot(this, new SignInFragment(), android.R.id.content);
            }
        }
    }

    @Override
    public void finish() {
        super.finish();

        finishReally();
    }
}
