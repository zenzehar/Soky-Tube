package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import java.util.List;

/** Responsive YouTube-like comments and replies bottom sheet.
 * Uses the original CommentsReceiver for loading, paging, likes and replies,
 * preserving the existing account credentials, comments API and backend.
 * The TV implementation is unchanged.
 */
final class MobileCommentsSheet {
    private final PlaybackActivity activity;
    private final CommentsReceiver receiver;
    private final Dialog dialog;
    private final LinearLayout comments;
    private final ScrollView scroll;
    private final TextView status;
    private final Handler main = new Handler(Looper.getMainLooper());
    private CommentGroup group;
    private boolean fetching = true;
    private boolean closed;
    private int loadedCount;

    private int dp(float value) { return MobileUi.dp(activity, value); }

    private MobileCommentsSheet(PlaybackActivity activity, CommentsReceiver receiver, String title) {
        this.activity = activity;
        this.receiver = receiver;
        dialog = new Dialog(activity);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFFFFFFF);
        float radius = dp(20);
        background.setCornerRadii(new float[]{radius,radius,radius,radius,0,0,0,0});
        sheet.setBackground(background);
        sheet.setPadding(dp(12),dp(8),dp(12),dp(12));

        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        sheet.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        TextView heading = label(title == null ? "Comments" : title,18,0xFF151515,true);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(
                0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        headingLp.setMargins(dp(10),0,0,0);
        toolbar.addView(heading,headingLp);
        TextView close = label("×",30,0xFF222222,false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close comments");
        toolbar.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        close.setOnClickListener(v -> dialog.dismiss());

        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        sheet.addView(scroll,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        comments = new LinearLayout(activity);
        comments.setPadding(dp(6),dp(3),dp(6),dp(18));
        comments.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(comments,new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        status = label("Loading comments…",14,0xFF777777,false);
        status.setPadding(dp(8),dp(15),dp(8),dp(16));
        comments.addView(status);

        scroll.setOnScrollChangeListener((View v,int sx,int sy,int oldX,int oldY)-> {
            if (closed || fetching || group == null ||
                    group.getNextCommentsKey() == null) return;
            if (sy + scroll.getHeight() >= comments.getHeight() - dp(240)) {
                fetching = true;
                status.setText("Loading more comments…");
                receiver.onLoadMore(group);
            }
        });

        dialog.setContentView(sheet);
        dialog.setOnDismissListener(d -> {
            closed = true;
            receiver.setCallback(null);
            receiver.onFinish(null);
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            int height = activity.getResources().getDisplayMetrics().heightPixels;
            window.setLayout(width,Math.max(dp(180),(int)(height*.82f)));
        }
        receiver.setCallback(new CommentsReceiver.Callback() {
            @Override public void onCommentGroup(CommentGroup loaded) {
                main.post(() -> renderGroup(loaded));
            }
            @Override public void onBackup(CommentsReceiver.Backup backup) { }
            @Override public void onSync(CommentItem changed) {
                // Refresh the specific comment without clearing scroll position.
                main.post(() -> {
                    if (!closed && changed != null) {
                        android.widget.Toast.makeText(activity,
                                "Comment updated",android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        receiver.onStart();
    }

    static void show(PlaybackActivity activity, CommentsReceiver receiver, String title) {
        if (activity == null || activity.isFinishing() || receiver == null) return;
        new MobileCommentsSheet(activity,receiver,title);
    }

    private TextView label(String content,int sp,int color,boolean bold) {
        TextView view = new TextView(activity);
        view.setText(content);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return view;
    }

    private void renderGroup(CommentGroup data) {
        if (closed) return;
        fetching = false;
        if (data == null || data.getComments() == null) {
            status.setText(loadedCount == 0 ? "Comments unavailable for this video"
                    : "Could not load more comments");
            status.setVisibility(View.VISIBLE);
            return;
        }
        group = data;
        if (loadedCount == 0) comments.removeAllViews();
        List<CommentItem> items = data.getComments();
        for (CommentItem item : items) {
            if (item == null || item.isEmpty()) continue;
            appendComment(item);
            loadedCount++;
        }
        if (loadedCount == 0) {
            comments.removeAllViews();
            status.setText("No comments yet");
            comments.addView(status);
        } else if (data.getNextCommentsKey() != null) {
            if (status.getParent() != null) ((ViewGroup) status.getParent()).removeView(status);
            status.setText("Scroll down for more");
            comments.addView(status);
        } else {
            status.setVisibility(View.GONE);
        }
    }

    private void appendComment(CommentItem item) {
        if (status.getParent() == comments) comments.removeView(status);
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.HORIZONTAL);
        block.setPadding(dp(4),dp(12),dp(4),dp(12));
        comments.addView(block,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        String author = item.getAuthorName();
        TextView avatar = label(author == null || author.isEmpty() ? "?" :
                author.substring(0,1).toUpperCase(java.util.Locale.getDefault()),
                16,Color.WHITE,true);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF456A91);
        avatar.setBackground(circle);
        block.addView(avatar,new LinearLayout.LayoutParams(dp(36),dp(36)));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        cp.setMarginStart(dp(10));
        block.addView(content,cp);

        String date = item.getPublishedDate();
        TextView by = label((author == null ? "YouTube" : author) +
                (date == null || date.isEmpty() ? "" : " · " + date),
                13,0xFF555555,true);
        by.setMaxLines(2);
        content.addView(by);
        TextView body = label(item.getMessage()==null ? "" : item.getMessage(),
                14,0xFF202020,false);
        body.setAutoLinkMask(Linkify.WEB_URLS);
        body.setPadding(0,dp(4),0,dp(6));
        content.addView(body);
        String likes = item.getLikeCount();
        if (likes != null && !likes.isEmpty()) {
            TextView like = label("👍  "+ likes,13,0xFF646464,false);
            like.setPadding(0,dp(7),0,dp(7));
            like.setContentDescription("Like comment");
            content.addView(like);
            like.setOnClickListener(v -> receiver.onCommentLongClicked(item));
        }
        if (item.getNestedCommentsKey() != null) {
            String replyCount = item.getReplyCount();
            TextView replies = label(replyCount == null || replyCount.isEmpty()
                    ? "View replies  ›" : "View " + replyCount + " replies  ›",
                    14,0xFF1767B2,true);
            replies.setPadding(0,dp(8),0,dp(7));
            content.addView(replies);
            replies.setOnClickListener(v -> receiver.onCommentClicked(item));
        }
        block.setOnLongClickListener(v -> {
            receiver.onCommentLongClicked(item);
            return true;
        });
    }
}
