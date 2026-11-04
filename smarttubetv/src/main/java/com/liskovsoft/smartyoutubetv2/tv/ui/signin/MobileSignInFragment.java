package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;

/** Native mobile YouTube sign-in. Reuses the original sign-in presenter,
 * QR provider, account polling, and completion callbacks, not the TV guide.
 */
public final class MobileSignInFragment extends Fragment implements SignInView {
    private SignInPresenter presenter;
    private FrameLayout root;
    private ImageView qr;
    private TextView code;
    private TextView description;
    private TextView browser;
    private String signInLink;

    private int dp(float v) { return MobileUi.dp(requireContext(), v); }

    private GradientDrawable surface(int color, float radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        presenter = SignInPresenter.instance(requireContext());
        presenter.setView(this);
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        Context context = requireContext();
        root = new FrameLayout(context);
        root.setBackgroundColor(Color.WHITE);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView back = text("‹", 34, 0xFF171717, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Close login");
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(v -> close());

        TextView headerText = text("Sign in to Soky Tube", 20, 0xFF171717, true);
        LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        label.setMarginStart(dp(8));
        header.addView(headerText, label);

        TextView close = text("×", 28, 0xFF222222, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close sign in");
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        close.setOnClickListener(v -> close());

        TextView prompt = text("Scan this QR code with your phone or browser", 16, 0xFF4A4A4A, false);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(dp(8), dp(18), dp(8), dp(14));
        column.addView(prompt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        qr = new ImageView(context);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setContentDescription("QR code for signing in");
        qr.setImageResource(R.drawable.activate_account_qrcode);
        column.addView(qr, new LinearLayout.LayoutParams(dp(210), dp(210)));

        description = text("Open the link, then enter the code below.", 13,
                0xFF656565, false);
        description.setGravity(Gravity.CENTER);
        description.setPadding(dp(8), dp(12), dp(8), 0);
        column.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        code = text("– – – –", 26, 0xFF171717, true);
        code.setGravity(Gravity.CENTER);
        code.setTextIsSelectable(true);
        code.setContentDescription("Sign in code. Long-press to copy.");
        code.setPadding(dp(8), dp(12), dp(8), dp(12));
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        codeParams.topMargin = dp(14);
        code.setBackground(surface(0xFFF2F3F5, 12));
        column.addView(code, codeParams);

        browser = text("Open sign-in page", 16, Color.WHITE, true);
        browser.setGravity(Gravity.CENTER);
        browser.setBackground(surface(0xFF191919, 24));
        browser.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(signInLink)) Utils.openLinkExt(requireContext(), signInLink);
        });
        LinearLayout.LayoutParams browserParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        browserParams.topMargin = dp(16);
        column.addView(browser, browserParams);

        TextView done = text("I've completed sign-in", 16, 0xFF161616, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(surface(0xFFF0F0F0, 24));
        done.setOnClickListener(v -> presenter.onActionClicked());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        doneParams.topMargin = dp(10);
        column.addView(done, doneParams);

        // Recalculate from the CURRENT window (not the device's physical panel).
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob) -> {
            if (r-l != orr-ol || b-t != ob-ot) resizeQr(r-l,b-t);
        });
        root.post(() -> resizeQr(root.getWidth(),root.getHeight()));
        return root;
    }

    private void resizeQr(int width, int height) {
        if (qr == null || width <= 0 || height <= 0) return;
        float density = Math.max(0.1f, getResources().getDisplayMetrics().density);
        // QR may use a compact 140dp size when the window is short, but remains
        // square and at least 100dp where physically possible.
        int size = Math.round(Math.min(dp(272), Math.min(width - dp(56),
                Math.max(dp(104), height * 0.32f))));
        size = Math.max(1, size);
        ViewGroup.LayoutParams params = qr.getLayoutParams();
        if (params.width != size || params.height != size) {
            params.width = size;
            params.height = size;
            qr.setLayoutParams(params);
        }
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        presenter.onViewInitialized();
    }

    @Override public void showCode(String userCode, String url) {
        showCode(userCode,url,null);
    }

    @Override public void showCode(String userCode, String url, String fullUrl) {
        if (!isAdded() || TextUtils.isEmpty(userCode)) return;
        signInLink = !TextUtils.isEmpty(fullUrl) ? fullUrl : url;
        code.setText(userCode);
        if (!TextUtils.isEmpty(url)) description.setText("Open " + url + " and enter the code");
        Glide.with(this).load(Utils.toQrCodeLink(signInLink))
                .placeholder(R.drawable.activate_account_qrcode)
                .error(R.drawable.activate_account_qrcode).into(qr);
    }

    @Override public void close() {
        if (getActivity() != null) getActivity().finish();
    }

    @Override public void onDestroy() {
        if (presenter != null) presenter.onViewDestroyed();
        super.onDestroy();
    }
}
