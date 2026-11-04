package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.ObjectAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.SearchTagsProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileUi;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.MobileVideoFeed;

import java.util.ArrayList;
import java.util.List;

/** Mobile search with true Android IME, direct-tap suggestions and reusable results. */
final class MobileSearchPanel {
    interface Actions {
        void submit(String query);
        void openVideo(Video video);
        void goBack();
    }

    private final Context context;
    private final LinearLayout root;
    private final RecyclerView suggestions;
    private final MobileVideoFeed results;
    private final EditText input;
    private final TextView clear;
    private final SuggestAdapter adapter = new SuggestAdapter();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Actions actions;
    private SearchTagsProvider provider;
    private boolean showingResults;
    private int generation;
    private Runnable delayedSearch;

    MobileSearchPanel(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
        root = new LinearLayout(context);
        root.setBackgroundColor(Color.WHITE);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setPadding(dp(8), dp(6), dp(8), dp(6));
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(context);
        back.setImageResource(R.drawable.mobile_ic_back);
        back.setColorFilter(Color.BLACK);
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> actions.goBack());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout inputContainer = new LinearLayout(context);
        inputContainer.setGravity(Gravity.CENTER_VERTICAL);
        inputContainer.setBackground(rounded(0xFFF1F1F1, 26));
        input = new EditText(context);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(0xFF171717);
        input.setHintTextColor(0xFF777777);
        input.setHint("Search");
        input.setPadding(dp(18), 0, dp(4), 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        inputContainer.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1f));
        clear = new TextView(context);
        clear.setText("\u00D7");
        clear.setTextSize(30);
        clear.setTextColor(0xFF333333);
        clear.setGravity(Gravity.CENTER);
        clear.setContentDescription("Clear query");
        clear.setOnClickListener(v -> input.setText(""));
        inputContainer.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(inputContainer, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(header);

        suggestions = new RecyclerView(context);
        suggestions.setLayoutManager(new LinearLayoutManager(context));
        suggestions.setBackgroundColor(Color.WHITE);
        suggestions.setAdapter(adapter);
        suggestions.setItemAnimator(null);
        root.addView(suggestions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        results = new MobileVideoFeed(context, actions::openVideo, false);
        results.getView().setVisibility(View.GONE);
        root.addView(results.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                clear.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
                showingResults = false;
                showCurrentMode();
                requestSuggestions(text.toString());
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                            event.getAction() == KeyEvent.ACTION_DOWN)) {
                submit(input.getText().toString());
                return true;
            }
            return false;
        });
        clear.setVisibility(View.GONE);
    }

    private int dp(float value) { return MobileUi.dp(context, value); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }
    View view() { return root; }

    void setProvider(SearchTagsProvider provider) {
        this.provider = provider;
        if (!showingResults) requestSuggestions(input.getText().toString());
    }

    void focusAndShowKeyboard() {
        input.requestFocus();
        input.postDelayed(() -> {
            if (input.getWindowToken() == null) return;
            InputMethodManager ime = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (ime != null) ime.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    void submit(String value) {
        String query = value != null ? value.trim() : "";
        if (query.length() == 0) return;
        if (!query.equals(input.getText().toString())) input.setText(query);
        input.setSelection(input.length());
        generation++;
        if (delayedSearch != null) handler.removeCallbacks(delayedSearch);
        showingResults = true;
        showCurrentMode();
        InputMethodManager ime = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (ime != null) ime.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
        actions.submit(query);
    }

    void showResults(ObjectAdapter originalRows) {
        results.scheduleRows(originalRows);
        if (showingResults) showCurrentMode();
    }

    void dispose() {
        if (delayedSearch != null) handler.removeCallbacks(delayedSearch);
        generation++;
    }

    private void showCurrentMode() {
        suggestions.setVisibility(showingResults ? View.GONE : View.VISIBLE);
        results.getView().setVisibility(showingResults ? View.VISIBLE : View.GONE);
    }

    private void requestSuggestions(String query) {
        generation++;
        final int request = generation;
        if (delayedSearch != null) handler.removeCallbacks(delayedSearch);
        if (provider == null || query == null || query.trim().length() == 0) {
            adapter.setSuggestions(new ArrayList<>());
            return;
        }
        delayedSearch = () -> provider.search(query, found -> handler.post(() -> {
            if (request != generation || showingResults) return;
            ArrayList<String> values = new ArrayList<>();
            if (found != null) {
                for (Tag item : found) {
                    if (item != null && item.tag != null && !item.tag.trim().isEmpty()) {
                        values.add(item.tag);
                        if (values.size() >= 15) break;
                    }
                }
            }
            adapter.setSuggestions(values);
        }));
        handler.postDelayed(delayedSearch, 220);
    }

    private final class SuggestAdapter extends RecyclerView.Adapter<SuggestHolder> {
        private final ArrayList<String> items = new ArrayList<>();
        void setSuggestions(List<String> suggestions) {
            items.clear(); items.addAll(suggestions);
            notifyDataSetChanged();
        }
        @NonNull @Override public SuggestHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), 0, dp(10), 0);
            ImageView glass = new ImageView(context);
            glass.setImageResource(R.drawable.mobile_ic_search);
            glass.setColorFilter(Color.BLACK);
            row.addView(glass, new LinearLayout.LayoutParams(dp(25), dp(25)));

            TextView label = new TextView(context);
            label.setTextColor(0xFF161616);
            label.setTextSize(16);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMargins(dp(20), 0, dp(4), 0);
            row.addView(label, labelParams);
            TextView fill = new TextView(context);
            fill.setText("\u2197");
            fill.setGravity(Gravity.CENTER);
            fill.setTextSize(27);
            fill.setTextColor(0xFF333333);
            fill.setContentDescription("Copy suggestion into search field");
            row.addView(fill, new LinearLayout.LayoutParams(dp(52), dp(54)));
            return new SuggestHolder(row, label, fill);
        }
        @Override public void onBindViewHolder(@NonNull SuggestHolder holder, int position) {
            String suggestion = items.get(position);
            holder.label.setText(suggestion);
            // Tapping suggestion text runs search. Right arrow only fills the field.
            holder.itemView.setOnClickListener(v -> submit(suggestion));
            holder.fill.setOnClickListener(v -> {
                input.setText(suggestion);
                input.setSelection(input.length());
                focusAndShowKeyboard();
            });
        }
        @Override public int getItemCount() { return items.size(); }
    }

    private static final class SuggestHolder extends RecyclerView.ViewHolder {
        final TextView label, fill;
        SuggestHolder(View item, TextView label, TextView fill) {
            super(item); this.label = label; this.fill = fill;
        }
    }
}
