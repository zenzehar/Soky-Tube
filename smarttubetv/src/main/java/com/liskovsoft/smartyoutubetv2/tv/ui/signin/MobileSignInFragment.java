package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;

/**
 * Phone-first browser-assisted sign-in. The underlying account provider
 * offers device-code activation (NOT native Credential Manager tokens).
 * The phone can open activation directly; a QR code is optional only for
 * signing in from a different device. Retains the existing token polling,
 * success and account-selection presenter, so the TV flow is unaffected.
 */
public final class MobileSignInFragment extends Fragment implements SignInView {
    private SignInPresenter presenter;
    private FrameLayout root;
    private ImageView qr;
    private TextView code;
    private TextView instruction;
    private TextView status;
    private TextView openBrowser;
    private TextView showQr;
    private String signInLink;
    private String userCode;
    private boolean qrExpanded;

    private int dp(float number) { return MobileUi.dp(requireContext(), number); }

    private GradientDrawable surface(int colour, float radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(colour);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private TextView text(String value, int sizeSp, int colour, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextColor(colour);
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
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(8), dp(20), dp(30));
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView back = text("‹", 34, 0xFF161616, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Close sign-in");
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        back.setOnClickListener(v -> close());
        TextView title = text("Sign in", 21, 0xFF171717, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(10);
        toolbar.addView(title, titleParams);

        TextView heading = text("Continue with your Google account", 25,
                Color.BLACK, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(30);
        column.addView(heading, headingParams);

        TextView lead = text("Use the secure sign-in page in your phone's browser. "
                + "Soky Tube never asks for your Google password.", 15,
                0xFF616161, false);
        LinearLayout.LayoutParams leadParams = new LinearLayout.LayoutParams(-1, -2);
        leadParams.topMargin = dp(12);
        column.addView(lead, leadParams);

        TextView label = text("Your verification code", 15, 0xFF444444, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(34);
        column.addView(label, labelParams);

        code = text("Getting code…", 25, 0xFF181818, true);
        code.setGravity(Gravity.CENTER);
        code.setTextIsSelectable(true);
        code.setContentDescription("Verification code. Long press to copy");
        code.setPadding(dp(12), dp(18), dp(12), dp(18));
        code.setBackground(surface(0xFFF1F2F4, 14));
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(-1, -2);
        codeParams.topMargin = dp(10);
        column.addView(code, codeParams);
        code.setOnClickListener(v -> copyCode());

        TextView copy = text("Copy code", 15, 0xFF202020, true);
        copy.setGravity(Gravity.CENTER);
        copy.setBackground(surface(0xFFF1F2F4, 24));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, dp(52));
        copyParams.topMargin = dp(12);
        column.addView(copy, copyParams);
        copy.setOnClickListener(v -> copyCode());

        openBrowser = text("Continue in browser", 17, Color.WHITE, true);
        openBrowser.setGravity(Gravity.CENTER);
        openBrowser.setBackground(surface(0xFF171717, 28));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(-1, dp(56));
        openParams.topMargin = dp(16);
        column.addView(openBrowser, openParams);
        openBrowser.setOnClickListener(v -> {
            if (TextUtils.isEmpty(signInLink)) {
                Toast.makeText(requireContext(), "Sign-in link is not available",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            copyCode();
            status.setText("Approve the sign-in in your browser, then return here. "
                    + "Soky Tube checks automatically.");
            Utils.openLinkExt(requireContext(), signInLink);
        });

        instruction = text("If the browser asks for a code, paste the one above.",
                14, 0xFF555555, false);
        LinearLayout.LayoutParams instParams = new LinearLayout.LayoutParams(-1, -2);
        instParams.topMargin = dp(12);
        column.addView(instruction, instParams);

        status = text("Checking for sign-in approval automatically…",
                13, 0xFF666666, false);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.topMargin = dp(24);
        column.addView(status, stateParams);

        // QR is genuinely useful when approving from ANOTHER device, but
        // makes no sense as the primary action on the phone itself.
        showQr = text("Use another device? Show QR code  ▾", 14, 0xFF363636, true);
        showQr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams qrButtonParams = new LinearLayout.LayoutParams(-1, dp(52));
        qrButtonParams.topMargin = dp(22);
        column.addView(showQr, qrButtonParams);
        showQr.setOnClickListener(v -> {
            qrExpanded = !qrExpanded;
            qr.setVisibility(qrExpanded ? View.VISIBLE : View.GONE);
            showQr.setText(qrExpanded ? "Hide QR code  ▴" : "Use another device? Show QR code  ▾");
        });

        qr = new ImageView(context);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setContentDescription("QR code for signing in on another device");
        qr.setImageResource(R.drawable.activate_account_qrcode);
        qr.setVisibility(View.GONE);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(210), dp(210));
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(12);
        column.addView(qr, qrParams);

        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob) -> {
            if (r-l != orr-ol || b-t != ob-ot) resizeQr(r-l, b-t);
        });
        root.post(() -> resizeQr(root.getWidth(), root.getHeight()));
        return root;
    }

    private void resizeQr(int width, int height) {
        if (qr == null || width <= 0 || height <= 0) return;
        float density = Math.max(.1f, getResources().getDisplayMetrics().density);
        int side = Math.round(MobileUi.mobileQrSizeDp(
                width / density, height / density) * density);
        ViewGroup.LayoutParams params = qr.getLayoutParams();
        if (params.width != side || params.height != side) {
            params.width = side;
            params.height = side;
            qr.setLayoutParams(params);
        }
    }

    private void copyCode() {
        if (TextUtils.isEmpty(userCode)) return;
        ClipboardManager clipboard = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Soky Tube verification code", userCode));
        Toast.makeText(requireContext(), "Verification code copied", Toast.LENGTH_SHORT).show();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        presenter.onViewInitialized();
    }

    @Override public void showCode(String value, String url) {
        showCode(value, url, null);
    }

    @Override public void showCode(String value, String url, String fullUrl) {
        if (!isAdded() || code == null) return;
        root.post(() -> {
            if (!isAdded() || code == null) return;
            if (TextUtils.isEmpty(url)) {
                // Provider failures are not user codes.
                userCode = null;
                signInLink = null;
                code.setText(TextUtils.isEmpty(value) ? "Sign-in unavailable" : value);
                openBrowser.setAlpha(.45f);
                status.setText("Unable to start sign-in. Close and try again.");
                return;
            }
            userCode = value;
            signInLink = !TextUtils.isEmpty(fullUrl) ? fullUrl : url;
            code.setText(value);
            openBrowser.setAlpha(1f);
            status.setText("Waiting for approval. This page updates automatically.");
            Glide.with(this).load(Utils.toQrCodeLink(signInLink))
                    .placeholder(R.drawable.activate_account_qrcode)
                    .error(R.drawable.activate_account_qrcode).into(qr);
        });
    }

    @Override public void close() {
        if (getActivity() != null) getActivity().finish();
    }

    @Override public void onDestroy() {
        if (presenter != null) presenter.onViewDestroyed();
        super.onDestroy();
    }
}
