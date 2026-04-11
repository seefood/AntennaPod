package de.danoeh.antennapod.ui.screen.queue;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.NavDrawerData;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Dialog for adding or editing a single RefillRule.
 */
class AddEditRuleDialog {

    interface OnRuleChangedListener {
        void onRuleChanged();
    }

    private final Context context;
    private final long rulesetId;
    /** null = add mode, non-null = edit mode */
    @Nullable
    private final RefillRule existingRule;
    private final int insertPosition;
    private final OnRuleChangedListener listener;

    private List<Feed> feedList = new ArrayList<>();
    private List<String> tagList = new ArrayList<>();

    AddEditRuleDialog(@NonNull Context context,
                      long rulesetId,
                      @Nullable RefillRule existingRule,
                      int insertPosition,
                      @NonNull OnRuleChangedListener listener) {
        this.context = context;
        this.rulesetId = rulesetId;
        this.existingRule = existingRule;
        this.insertPosition = insertPosition;
        this.listener = listener;
    }

    void show() {
        Observable.fromCallable(() -> {
            List<Feed> feeds = DBReader.getFeedList();
            List<NavDrawerData.TagItem> tags = DBReader.getAllTags(Feed.STATE_SUBSCRIBED);
            return new Object[]{feeds, tags};
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    //noinspection unchecked
                    feedList = (List<Feed>) result[0];
                    //noinspection unchecked
                    List<NavDrawerData.TagItem> tagItems = (List<NavDrawerData.TagItem>) result[1];
                    tagList = new ArrayList<>();
                    for (NavDrawerData.TagItem tag : tagItems) {
                        tagList.add(tag.getTitle());
                    }
                    showDialog();
                }, throwable -> {
                    feedList = new ArrayList<>();
                    tagList = new ArrayList<>();
                    showDialog();
                });
    }

    private void showDialog() {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_refill_rule_edit, null, false);

        RadioGroup sourceTypeGroup = dialogView.findViewById(R.id.source_type_group);
        Spinner sourcePicker = dialogView.findViewById(R.id.source_picker_spinner);
        EditText countEditText = dialogView.findViewById(R.id.count_edit_text);
        MaterialButtonToggleGroup methodGroup = dialogView.findViewById(R.id.selection_method_group);

        // Populate source picker with feeds by default
        ArrayAdapter<String> feedAdapter = buildFeedAdapter();
        ArrayAdapter<String> tagAdapter = buildTagAdapter();
        sourcePicker.setAdapter(feedAdapter);

        // Pre-fill from existing rule
        if (existingRule != null) {
            switch (existingRule.getSourceType()) {
                case TAG:
                    sourceTypeGroup.check(R.id.source_tag_radio);
                    sourcePicker.setAdapter(tagAdapter);
                    int tagIdx = tagList.indexOf(existingRule.getSourceId());
                    if (tagIdx >= 0) {
                        sourcePicker.setSelection(tagIdx);
                    }
                    break;
                case INBOX:
                    sourceTypeGroup.check(R.id.source_inbox_radio);
                    sourcePicker.setVisibility(View.GONE);
                    break;
                default:
                    sourceTypeGroup.check(R.id.source_feed_radio);
                    selectFeedById(sourcePicker, existingRule.getSourceId());
                    break;
            }
            countEditText.setText(String.valueOf(existingRule.getCount()));
            switch (existingRule.getSelectionMethod()) {
                case NEWEST:
                    methodGroup.check(R.id.method_newest);
                    break;
                case RANDOM:
                    methodGroup.check(R.id.method_random);
                    break;
                default:
                    methodGroup.check(R.id.method_oldest);
                    break;
            }
        } else {
            sourceTypeGroup.check(R.id.source_feed_radio);
            methodGroup.check(R.id.method_oldest);
        }

        sourceTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.source_tag_radio) {
                sourcePicker.setAdapter(tagAdapter);
                sourcePicker.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.source_inbox_radio) {
                sourcePicker.setVisibility(View.GONE);
            } else {
                sourcePicker.setAdapter(feedAdapter);
                sourcePicker.setVisibility(View.VISIBLE);
            }
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(existingRule == null ? R.string.add_rule : R.string.queue_edit_rules)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel_label, null)
                .setPositiveButton(R.string.confirm_label, null); // set below to control dismiss

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Disable confirm if invalid
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String countStr = countEditText.getText() != null ? countEditText.getText().toString().trim() : "";
            int count;
            try {
                count = Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                count = 0;
            }
            if (count < 1) {
                countEditText.setError(context.getString(R.string.time_dialog_invalid_input));
                return;
            }

            int sourceChecked = sourceTypeGroup.getCheckedRadioButtonId();
            RefillRule.SourceType sourceType;
            String sourceId;
            if (sourceChecked == R.id.source_tag_radio) {
                sourceType = RefillRule.SourceType.TAG;
                if (tagList.isEmpty()) {
                    return;
                }
                sourceId = tagList.get(sourcePicker.getSelectedItemPosition());
            } else if (sourceChecked == R.id.source_inbox_radio) {
                sourceType = RefillRule.SourceType.INBOX;
                sourceId = null;
            } else {
                sourceType = RefillRule.SourceType.FEED;
                if (feedList.isEmpty()) {
                    return;
                }
                sourceId = String.valueOf(feedList.get(sourcePicker.getSelectedItemPosition()).getId());
            }

            int methodChecked = methodGroup.getCheckedButtonId();
            RefillRule.SelectionMethod method;
            if (methodChecked == R.id.method_newest) {
                method = RefillRule.SelectionMethod.NEWEST;
            } else if (methodChecked == R.id.method_random) {
                method = RefillRule.SelectionMethod.RANDOM;
            } else {
                method = RefillRule.SelectionMethod.OLDEST;
            }

            ExecutorService exec = DBWriter.getDbExecutor();
            final int finalCount = count;
            final String finalSourceId = sourceId;
            if (existingRule == null) {
                exec.execute(() -> {
                    try {
                        DBWriter.createRefillRule(rulesetId, insertPosition, method,
                                finalCount, sourceType, finalSourceId).get();
                        listener.onRuleChanged();
                    } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } else {
                exec.execute(() -> {
                    try {
                        DBWriter.updateRefillRule(existingRule.getId(),
                                method, finalCount, sourceType, finalSourceId).get();
                        listener.onRuleChanged();
                    } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            dialog.dismiss();
        });

        countEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (dialog.isShowing()) {
                    String t = s != null ? s.toString().trim() : "";
                    boolean valid;
                    try {
                        valid = Integer.parseInt(t) >= 1;
                    } catch (NumberFormatException e) {
                        valid = false;
                    }
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(valid);
                }
            }
        });
    }

    private ArrayAdapter<String> buildFeedAdapter() {
        List<String> titles = new ArrayList<>();
        for (Feed feed : feedList) {
            titles.add(feed.getTitle());
        }
        return new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, titles);
    }

    private ArrayAdapter<String> buildTagAdapter() {
        return new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, tagList);
    }

    private void selectFeedById(Spinner spinner, String feedIdStr) {
        if (feedIdStr == null) {
            return;
        }
        try {
            long feedId = Long.parseLong(feedIdStr);
            for (int i = 0; i < feedList.size(); i++) {
                if (feedList.get(i).getId() == feedId) {
                    spinner.setSelection(i);
                    return;
                }
            }
        } catch (NumberFormatException e) {
            // ignore
        }
    }
}
