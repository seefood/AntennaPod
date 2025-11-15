package de.danoeh.antennapod.ui.screen.queue;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedOrder;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.QueueRuleset;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.QueueColorGradient;
import de.danoeh.antennapod.ui.common.QueueRulesetViewModel;
import de.danoeh.antennapod.ui.common.QueueViewModel;

/**
 * Fragment for editing queue refill rules.
 *
 * <p>Allows users to view, add, edit, remove, and reorder rules
 * that determine how a queue is automatically refilled.
 */
public class QueueRulesetEditFragment extends Fragment {
    public static final String TAG = "QueueRulesetEditFrag";

    private QueueRulesetViewModel viewModel;
    private RecyclerView rulesList;
    private TextView emptyView;
    private FloatingActionButton addRuleButton;
    private FloatingActionButton insertRuleButton;
    private MaterialToolbar toolbar;
    private RefillRuleAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int dragFrom = -1; // Track drag start position
    private int dragTo = -1;   // Track drag end position

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_queue_ruleset_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication()))
                .get(QueueRulesetViewModel.class);

        // Find views
        toolbar = view.findViewById(R.id.toolbar);
        rulesList = view.findViewById(R.id.rules_list);
        emptyView = view.findViewById(R.id.empty_view);
        addRuleButton = view.findViewById(R.id.add_rule_button);
        insertRuleButton = view.findViewById(R.id.insert_rule_button);

        // Setup toolbar with back navigation
        toolbar.setNavigationOnClickListener(v -> {
            getParentFragmentManager().popBackStack();
        });

        // Setup RecyclerView
        rulesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RefillRuleAdapter();
        rulesList.setAdapter(adapter);

        // Setup adapter callbacks
        adapter.setOnRuleEditListener(rule -> {
            showEditRuleDialog(rule);
        });
        adapter.setOnRuleDeleteListener(rule -> {
            showDeleteRuleConfirmation(rule);
        });

        // Setup drag-to-reorder with ItemTouchHelper (matches queue item behavior)
        ItemTouchHelper.SimpleCallback touchCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getBindingAdapterPosition();
                int toPosition = target.getBindingAdapterPosition();

                // Track positions for database update
                if (dragFrom == -1) {
                    dragFrom = fromPosition;
                }
                dragTo = toPosition;

                // Move in adapter immediately for visual feedback
                RefillRule fromRule = adapter.getRuleAt(fromPosition);
                if (fromRule != null) {
                    java.util.List<RefillRule> currentRules = new java.util.ArrayList<>(adapter.rules);
                    currentRules.remove(fromPosition);
                    currentRules.add(toPosition, fromRule);
                    adapter.updateRules(currentRules);
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe actions for rules
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false; // Disable long press - use drag handle instead
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // Visual feedback during drag
                    if (viewHolder != null) {
                        viewHolder.itemView.setAlpha(0.7f);
                    }
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                 @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setAlpha(1.0f);

                // After drag ends, check if actually moved and update database
                if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                    reallyMoved(dragFrom, dragTo);
                }
                dragFrom = dragTo = -1;
            }

        };

        itemTouchHelper = new ItemTouchHelper(touchCallback);
        itemTouchHelper.attachToRecyclerView(rulesList);

        // Set ItemTouchHelper in adapter for drag handle access
        adapter.setItemTouchHelper(itemTouchHelper);

        // Setup add rule button (append at end)
        addRuleButton.setOnClickListener(v -> {
            showAddRuleDialog(false);
        });

        // Setup insert rule button (insert at position 0)
        insertRuleButton.setOnClickListener(v -> {
            showAddRuleDialog(true);
        });

        // Observe refilling state and disable edits during refill (FR-036)
        viewModel.getIsRefilling().observe(getViewLifecycleOwner(), isRefilling -> {
            boolean enabled = !isRefilling;
            addRuleButton.setEnabled(enabled);
            insertRuleButton.setEnabled(enabled);
            rulesList.setEnabled(enabled);
            // Set adapter items to non-clickable during refill
            if (adapter != null) {
                adapter.setEditable(enabled);
            }
            // Show message if refill is in progress
            if (isRefilling) {
                // Temporarily disable UI feedback
                if (emptyView != null) {
                    emptyView.setText(R.string.queue_refilling_message);
                }
            } else if (emptyView != null) {
                // Update empty view message after refill completes
                refreshEmptyViewMessage();
            }
        });

        // Apply queue color gradient to toolbar
        QueueViewModel queueViewModel = new ViewModelProvider(requireActivity(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication()))
                .get(QueueViewModel.class);
        queueViewModel.getCurrentQueueColor().observe(getViewLifecycleOwner(), color -> {
            if (color != null && toolbar != null) {
                // Check for theme changes (but don't clear cache here to avoid infinite loop)
                queueViewModel.checkThemeChanged();
                // Get gradient (cache will be cleared by checkThemeChanged if theme changed)
                GradientDrawable gradient = queueViewModel.getGradientForColor(color);
                QueueColorGradient.applyGradientToToolbar(toolbar, gradient, color);
            }
        });

        // Observe ruleset data
        viewModel.getRuleset().observe(getViewLifecycleOwner(), ruleset -> {
            if (ruleset == null) {
                // No ruleset exists yet - show empty state
                emptyView.setVisibility(View.VISIBLE);
                rulesList.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                rulesList.setVisibility(View.VISIBLE);
            }
        });

        // Observe rules list
        viewModel.getRules().observe(getViewLifecycleOwner(), rules -> {
            if (rules == null || rules.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
                rulesList.setVisibility(View.GONE);
                // Hide insert button when no rules (can't insert if list is empty)
                if (insertRuleButton != null) {
                    insertRuleButton.setVisibility(View.GONE);
                }
            } else {
                emptyView.setVisibility(View.GONE);
                rulesList.setVisibility(View.VISIBLE);
                // Show insert button when rules exist
                if (insertRuleButton != null) {
                    insertRuleButton.setVisibility(View.VISIBLE);
                }
                // Update adapter with new rules
                if (adapter != null) {
                    adapter.updateRules(rules);
                }
            }
        });
    }

    /**
     * Refresh the empty view message to show "no rules" state.
     * Called when refill completes to restore the proper empty view message.
     */
    private void refreshEmptyViewMessage() {
        if (emptyView != null) {
            emptyView.setText(R.string.no_queue_rules_message);
        }
    }

    /**
     * Show dialog for adding a new rule.
     * Goes directly to rule configuration dialog (only "Add Episodes" rule type exists).
     *
     * @param insertAtTop If true, insert at position 0; if false, append at end
     */
    private void showAddRuleDialog(boolean insertAtTop) {
        // Go directly to rule configuration dialog (only "Add Episodes" rule type exists)
        showRuleEditDialog(null, insertAtTop);
    }

    /**
     * Show dialog for editing an existing rule.
     *
     * @param rule Rule to edit (null for new rule)
     */
    private void showEditRuleDialog(RefillRule rule) {
        showRuleEditDialog(rule, false);
    }

    /**
     * Show dialog for adding or editing a rule.
     *
     * @param rule Rule to edit (null for new rule)
     * @param insertAtTop If true, insert at position 0; if false, append at end (only for new rules)
     */
    private void showRuleEditDialog(@Nullable RefillRule rule, boolean insertAtTop) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_refill_rule_edit, null);

        final MaterialButton sourceTypeButton = dialogView.findViewById(R.id.rule_source_type_button);
        final TextView sourceLabel = dialogView.findViewById(R.id.rule_source_label);
        final MaterialButton sourceButton = dialogView.findViewById(R.id.rule_source_button);
        final MaterialButton selectionMethodButton = dialogView.findViewById(R.id.rule_selection_method_button);
        final NumberPicker countInput = dialogView.findViewById(R.id.rule_count_input);

        // Setup NumberPicker (1-10 range, default 2)
        countInput.setMinValue(1);
        countInput.setMaxValue(10);
        countInput.setValue(2);

        // Setup source type selection
        String[] sourceTypes = {
                requireContext().getString(R.string.source_type_feed),
                requireContext().getString(R.string.source_type_tag),
                requireContext().getString(R.string.source_type_inbox)
        };
        final RefillRule.SourceType[] sourceTypeValues = {
                RefillRule.SourceType.FEED,
                RefillRule.SourceType.TAG,
                RefillRule.SourceType.INBOX
        };
        final RefillRule.SourceType[] selectedSourceType = {RefillRule.SourceType.FEED};

        sourceTypeButton.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.rule_source_type_label)
                    .setItems(sourceTypes, (dialog, which) -> {
                        selectedSourceType[0] = sourceTypeValues[which];
                        sourceTypeButton.setText(sourceTypes[which]);
                        updateSourceButtonVisibility(sourceLabel, sourceButton, selectedSourceType[0]);
                    })
                    .show();
        });

        // Setup selection method selection
        String[] selectionMethods = {
                requireContext().getString(R.string.selection_method_oldest),
                requireContext().getString(R.string.selection_method_newest),
                requireContext().getString(R.string.selection_method_random)
        };
        final RefillRule.SelectionMethod[] selectionMethodValues = {
                RefillRule.SelectionMethod.OLDEST,
                RefillRule.SelectionMethod.NEWEST,
                RefillRule.SelectionMethod.RANDOM
        };
        final RefillRule.SelectionMethod[] selectedSelectionMethod = {RefillRule.SelectionMethod.OLDEST};

        selectionMethodButton.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.rule_selection_method_label)
                    .setItems(selectionMethods, (dialog, which) -> {
                        selectedSelectionMethod[0] = selectionMethodValues[which];
                        selectionMethodButton.setText(selectionMethods[which]);
                    })
                    .show();
        });

        // Store selected values
        final String[] selectedSourceName = {""};
        final String[] selectedSourceId = {""};

        // Setup source button click handler
        sourceButton.setOnClickListener(v -> {
            RefillRule.SourceType currentType = selectedSourceType[0];
            if (currentType == RefillRule.SourceType.INBOX) {
                // Inbox doesn't need source selection
                return;
            }

            // Load feeds/tags on background thread
            executor.submit(() -> {
                try {
                    java.util.List<String> items = new java.util.ArrayList<>();
                    if (currentType == RefillRule.SourceType.FEED) {
                        java.util.List<Feed> feeds = DBReader.getFeedList();
                        for (Feed feed : feeds) {
                            items.add(feed.getTitle());
                        }
                    } else if (currentType == RefillRule.SourceType.TAG) {
                        FeedOrder feedOrder = UserPreferences.getFeedOrder();
                        FeedCounter feedCounter = UserPreferences.getFeedCounterSetting();
                        de.danoeh.antennapod.storage.database.NavDrawerData navData =
                                DBReader.getNavDrawerData(null, feedOrder, feedCounter, 0);
                        if (navData != null && navData.tags != null) {
                            for (de.danoeh.antennapod.storage.database.NavDrawerData.TagItem tag : navData.tags) {
                                if (tag != null && tag.getTitle() != null
                                        && !tag.getTitle().equals(FeedPreferences.TAG_ROOT)
                                        && !tag.getTitle().equals(FeedPreferences.TAG_UNTAGGED)) {
                                    items.add(tag.getTitle());
                                }
                            }
                        }
                    }

                    final String[] itemsArray = items.toArray(new String[0]);
                    requireActivity().runOnUiThread(() -> {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.rule_source_label)
                                .setItems(itemsArray, (dialog, which) -> {
                                    selectedSourceName[0] = itemsArray[which];
                                    selectedSourceId[0] = itemsArray[which];
                                    sourceButton.setText(itemsArray[which]);
                                })
                                .show();
                    });
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error loading sources", e);
                }
            });
        });

        // Pre-populate fields if editing
        if (rule != null) {
            // Set source type
            int sourceTypeIndex = -1;
            for (int i = 0; i < sourceTypeValues.length; i++) {
                if (sourceTypeValues[i] == rule.getSourceType()) {
                    sourceTypeIndex = i;
                    selectedSourceType[0] = rule.getSourceType();
                    break;
                }
            }
            if (sourceTypeIndex >= 0) {
                sourceTypeButton.setText(sourceTypes[sourceTypeIndex]);
                updateSourceButtonVisibility(sourceLabel, sourceButton, rule.getSourceType());
            }

            // Set source (feed/tag) - need to convert feed ID to name for display
            if (rule.getSourceId() != null) {
                if (rule.getSourceType() == RefillRule.SourceType.FEED) {
                    // Load feeds to convert ID to name
                    executor.submit(() -> {
                        try {
                            java.util.List<Feed> feeds = DBReader.getFeedList();
                            String feedIdStr = rule.getSourceId();
                            try {
                                long feedId = Long.parseLong(feedIdStr);
                                for (Feed feed : feeds) {
                                    if (feed.getId() == feedId) {
                                        String feedName = feed.getTitle();
                                        requireActivity().runOnUiThread(() -> {
                                            selectedSourceName[0] = feedName;
                                            selectedSourceId[0] = feedIdStr;
                                            sourceButton.setText(feedName);
                                        });
                                        break;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Not a valid feed ID, use as-is
                                requireActivity().runOnUiThread(() -> {
                                    selectedSourceName[0] = rule.getSourceId();
                                    selectedSourceId[0] = rule.getSourceId();
                                    sourceButton.setText(rule.getSourceId());
                                });
                            }
                        } catch (Exception e) {
                            android.util.Log.e(TAG, "Error loading feed name", e);
                        }
                    });
                } else {
                    // Tag or other - use sourceId as-is
                    selectedSourceName[0] = rule.getSourceId();
                    selectedSourceId[0] = rule.getSourceId();
                    sourceButton.setText(rule.getSourceId());
                }
            }

            // Set selection method
            int selectionMethodIndex = -1;
            for (int i = 0; i < selectionMethodValues.length; i++) {
                if (selectionMethodValues[i] == rule.getSelectionMethod()) {
                    selectionMethodIndex = i;
                    selectedSelectionMethod[0] = rule.getSelectionMethod();
                    break;
                }
            }
            if (selectionMethodIndex >= 0) {
                selectionMethodButton.setText(selectionMethods[selectionMethodIndex]);
            }

            // Set count
            if (rule.getCount() != null) {
                int count = rule.getCount();
                if (count >= 1 && count <= 10) {
                    countInput.setValue(count);
                } else {
                    countInput.setValue(2); // Default if out of range
                }
            }
        } else {
            // Default values for new rule
            sourceTypeButton.setText(sourceTypes[0]); // FEED
            selectionMethodButton.setText(selectionMethods[0]); // OLDEST
            countInput.setValue(2); // Default count
            updateSourceButtonVisibility(sourceLabel, sourceButton, RefillRule.SourceType.FEED);
        }

        // Store feed name to ID mapping for later use
        final Map<String, Long>[] feedNameToIdRef = new Map[]{new HashMap<>()};

        // Build dialog
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(rule != null ? R.string.edit_rule_label
                        : (insertAtTop ? R.string.insert_rule_label : R.string.add_rule_label))
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // Validate and save rule
                    executor.submit(() -> {
                        try {
                            // Load feeds to get name-to-ID mapping
                            if (feedNameToIdRef[0].isEmpty()) {
                                java.util.List<Feed> feeds = DBReader.getFeedList();
                                feedNameToIdRef[0] = new HashMap<>();
                                for (Feed feed : feeds) {
                                    feedNameToIdRef[0].put(feed.getTitle(), feed.getId());
                                }
                            }
                            requireActivity().runOnUiThread(() -> {
                                saveRuleFromDialog(rule, insertAtTop, selectedSourceType[0],
                                        selectedSourceName[0], selectedSourceId[0], selectedSelectionMethod[0],
                                        countInput.getValue(), feedNameToIdRef[0]);
                            });
                        } catch (Exception e) {
                            android.util.Log.e(TAG, "Error loading feeds for validation", e);
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null);

        dialogBuilder.show();
    }

    /**
     * Update source button visibility based on source type.
     *
     * @param sourceLabel Label for source input
     * @param sourceButton Source button
     * @param sourceType Selected source type
     */
    private void updateSourceButtonVisibility(
            TextView sourceLabel,
            MaterialButton sourceButton,
            RefillRule.SourceType sourceType) {
        if (sourceType == RefillRule.SourceType.INBOX) {
            sourceLabel.setVisibility(View.GONE);
            sourceButton.setVisibility(View.GONE);
            sourceButton.setText("");
        } else {
            sourceLabel.setVisibility(View.VISIBLE);
            sourceButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Save rule from dialog inputs.
     *
     * @param existingRule Existing rule to update (null for new rule)
     * @param insertAtTop If true, insert at position 0; if false, append at end (only for new rules)
     * @param sourceType Selected source type
     * @param sourceName Selected source name (for display/validation)
     * @param sourceId Selected source ID (may be name for FEED, will be converted to ID)
     * @param selectionMethod Selected selection method
     * @param count Selected count
     * @param feedNameToId Map of feed names to feed IDs (for FEED source type)
     */
    private void saveRuleFromDialog(@Nullable RefillRule existingRule,
                                     boolean insertAtTop,
                                     RefillRule.SourceType sourceType,
                                     String sourceName,
                                     String sourceId,
                                     RefillRule.SelectionMethod selectionMethod,
                                     int count,
                                     Map<String, Long> feedNameToId) {
        // Count is already validated by NumberPicker (1-10 range)

        // Validate source (required for FEED and TAG)
        String finalSourceId = sourceId;
        if (sourceType == RefillRule.SourceType.FEED) {
            if (TextUtils.isEmpty(sourceName)) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.edit_rule_label)
                        .setMessage(R.string.rule_source_required)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            // Convert feed name to feed ID
            Long feedId = feedNameToId != null ? feedNameToId.get(sourceName) : null;
            if (feedId == null) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.edit_rule_label)
                        .setMessage("Feed not found: " + sourceName)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            finalSourceId = String.valueOf(feedId);
        } else if (sourceType == RefillRule.SourceType.TAG) {
            if (TextUtils.isEmpty(sourceId)) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.edit_rule_label)
                        .setMessage(R.string.rule_source_required)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            // Tag name is stored as-is
        } else if (sourceType == RefillRule.SourceType.INBOX) {
            finalSourceId = null;
        }

        // Save rule
        if (existingRule != null) {
            // Update existing rule
            updateRule(existingRule, sourceType, finalSourceId, selectionMethod, count);
        } else {
            // Create new rule
            createRule(insertAtTop, sourceType, finalSourceId, selectionMethod, count);
        }
    }

    /**
     * Create a new rule.
     *
     * @param insertAtTop If true, insert at position 0; if false, append at end
     * @param sourceType Source type
     * @param sourceId Source ID
     * @param selectionMethod Selection method
     * @param count Count
     */
    private void createRule(boolean insertAtTop,
                             RefillRule.SourceType sourceType,
                             String sourceId,
                             RefillRule.SelectionMethod selectionMethod,
                             int count) {
        // Get queue ID from UserPreferences
        long queueId = UserPreferences.getCurrentQueueId();
        executor.submit(() -> {
            try {
                long rulesetId;
                // Create ruleset if it doesn't exist
                QueueRuleset ruleset = DBReader.getQueueRuleset(queueId);
                if (ruleset == null) {
                    rulesetId = DBWriter.createQueueRuleset(queueId).get();
                } else {
                    rulesetId = ruleset.getId();
                }

                // Load existing rules
                java.util.List<RefillRule> existingRules = DBReader.getRefillRules(rulesetId);

                // If inserting at top, shift all existing rules first to make room
                if (insertAtTop && !existingRules.isEmpty()) {
                    Map<Long, Integer> positionMap = new HashMap<>();
                    for (RefillRule rule : existingRules) {
                        // Shift all existing rules up by 1
                        positionMap.put(rule.getId(), rule.getPosition() + 1);
                    }
                    // Apply shift before creating new rule
                    DBWriter.reorderRefillRules(rulesetId, positionMap).get();
                }

                // Now create rule at determined position (0 for top, size for append)
                int insertPosition = insertAtTop ? 0 : existingRules.size();
                long ruleId = DBWriter.createRefillRule(rulesetId, insertPosition, RefillRule.RuleType.ADD_EPISODES,
                        selectionMethod, count, sourceType, sourceId).get();

                if (ruleId <= 0) {
                    android.util.Log.e(TAG, "Failed to create rule - ruleId is " + ruleId);
                    return;
                }

                // Refresh ruleset on main thread
                requireActivity().runOnUiThread(() -> viewModel.refreshRuleset());
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error creating rule", e);
            }
        });
    }

    /**
     * Update an existing rule.
     *
     * @param rule Rule to update
     * @param sourceType New source type
     * @param sourceId New source ID
     * @param selectionMethod New selection method
     * @param count New count
     */
    private void updateRule(RefillRule rule,
                             RefillRule.SourceType sourceType,
                             String sourceId,
                             RefillRule.SelectionMethod selectionMethod,
                             int count) {
        executor.submit(() -> {
            try {
                DBWriter.updateRefillRule(rule.getId(), RefillRule.RuleType.ADD_EPISODES,
                        selectionMethod, count, sourceType, sourceId).get();

                // Refresh ruleset on main thread
                requireActivity().runOnUiThread(() -> viewModel.refreshRuleset());
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error updating rule", e);
            }
        });
    }

    /**
     * Show confirmation dialog before deleting a rule.
     *
     * @param rule Rule to delete
     */
    private void showDeleteRuleConfirmation(RefillRule rule) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_rule_label)
                .setMessage("Are you sure you want to delete this rule?")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    deleteRule(rule);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Delete a rule from the ruleset.
     *
     * @param rule Rule to delete
     */
    private void deleteRule(RefillRule rule) {
        executor.submit(() -> {
            try {
                DBWriter.deleteRefillRule(rule.getId()).get();
                // Refresh ruleset on main thread
                requireActivity().runOnUiThread(() -> viewModel.refreshRuleset());
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error deleting rule", e);
            }
        });
    }

    /**
     * Update database after rules are reordered via drag-and-drop.
     * Maps current adapter positions to rule IDs for database update.
     *
     * @param fromPosition Original position
     * @param toPosition New position
     */
    private void reallyMoved(int fromPosition, int toPosition) {
        if (adapter == null) {
            return;
        }

        // Get current rules from adapter (already reordered by ItemTouchHelper)
        java.util.List<RefillRule> rules = new java.util.ArrayList<>(adapter.rules);

        // Build position map - map each rule ID to its new position index
        Map<Long, Integer> positionMap = new HashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            RefillRule rule = rules.get(i);
            if (rule != null) {
                positionMap.put(rule.getId(), i);
            }
        }

        // Update positions in database on background thread
        long queueId = UserPreferences.getCurrentQueueId();
        executor.submit(() -> {
            try {
                QueueRuleset ruleset = DBReader.getQueueRuleset(queueId);
                if (ruleset != null) {
                    DBWriter.reorderRefillRules(ruleset.getId(), positionMap).get();
                    // Refresh ruleset on main thread to ensure UI is in sync
                    requireActivity().runOnUiThread(() -> viewModel.refreshRuleset());
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error reordering rules", e);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
        }
        executor.shutdown();
    }
}
