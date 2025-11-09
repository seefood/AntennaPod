package de.danoeh.antennapod.ui.screen.queue;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.NumberPicker;
import android.widget.TextView;

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
        adapter.setOnRuleReorderListener((fromPosition, toPosition) -> {
            reorderRules(fromPosition, toPosition);
        });

        // Setup drag-to-reorder with ItemTouchHelper
        ItemTouchHelper.SimpleCallback touchCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getBindingAdapterPosition();
                int toPosition = target.getBindingAdapterPosition();

                // Check if rules can be reordered
                if (!adapter.canReorderRule(fromPosition) || !adapter.canReorderRule(toPosition)) {
                    return false;
                }

                // Move in adapter - update adapter immediately for visual feedback
                RefillRule fromRule = adapter.getRuleAt(fromPosition);
                if (fromRule != null) {
                    // Update adapter list for immediate visual feedback
                    java.util.List<RefillRule> currentRules = new java.util.ArrayList<>(adapter.rules);
                    currentRules.remove(fromPosition);
                    currentRules.add(toPosition, fromRule);
                    adapter.updateRules(currentRules);

                    // Trigger reorder callback to update database
                    if (adapter.reorderListener != null) {
                        adapter.reorderListener.onRuleReordered(fromPosition, toPosition);
                    }
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
                return true;
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
            }
        };

        itemTouchHelper = new ItemTouchHelper(touchCallback);
        itemTouchHelper.attachToRecyclerView(rulesList);

        // Setup add rule button (append at end)
        addRuleButton.setOnClickListener(v -> {
            showAddRuleDialog(false);
        });

        // Setup insert rule button (insert at position 0)
        insertRuleButton.setOnClickListener(v -> {
            showAddRuleDialog(true);
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

        final AutoCompleteTextView sourceTypeInput = dialogView.findViewById(R.id.rule_source_type_input);
        final com.google.android.material.textfield.TextInputLayout sourceInputLayout =
                dialogView.findViewById(R.id.rule_source_input_layout);
        final AutoCompleteTextView sourceInput = dialogView.findViewById(R.id.rule_source_input);
        final AutoCompleteTextView selectionMethodInput = dialogView.findViewById(R.id.rule_selection_method_input);
        final NumberPicker countInput = dialogView.findViewById(R.id.rule_count_input);

        // Setup NumberPicker (1-20 range)
        countInput.setMinValue(1);
        countInput.setMaxValue(20);
        countInput.setValue(10);

        // Setup source type dropdown
        String[] sourceTypes = {
                requireContext().getString(R.string.source_type_feed),
                requireContext().getString(R.string.source_type_tag),
                requireContext().getString(R.string.source_type_inbox)
        };
        RefillRule.SourceType[] sourceTypeValues = {
                RefillRule.SourceType.FEED,
                RefillRule.SourceType.TAG,
                RefillRule.SourceType.INBOX
        };
        ArrayAdapter<String> sourceTypeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, sourceTypes);
        sourceTypeInput.setAdapter(sourceTypeAdapter);
        sourceTypeInput.setThreshold(1); // Show dropdown after 1 character
        sourceTypeInput.setOnItemClickListener((parent, view, position, id) -> {
            RefillRule.SourceType selectedType = sourceTypeValues[position];
            updateSourceInputVisibility(sourceInputLayout, sourceInput, selectedType);
        });
        // Show dropdown when clicked
        sourceTypeInput.setOnClickListener(v -> sourceTypeInput.showDropDown());

        // Setup selection method dropdown
        String[] selectionMethods = {
                requireContext().getString(R.string.selection_method_oldest),
                requireContext().getString(R.string.selection_method_newest),
                requireContext().getString(R.string.selection_method_random)
        };
        RefillRule.SelectionMethod[] selectionMethodValues = {
                RefillRule.SelectionMethod.OLDEST,
                RefillRule.SelectionMethod.NEWEST,
                RefillRule.SelectionMethod.RANDOM
        };
        ArrayAdapter<String> selectionMethodAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, selectionMethods);
        selectionMethodInput.setAdapter(selectionMethodAdapter);
        selectionMethodInput.setThreshold(1); // Show dropdown after 1 character
        // Show dropdown when clicked
        selectionMethodInput.setOnClickListener(v -> selectionMethodInput.showDropDown());

        // Pre-populate fields if editing
        if (rule != null) {
            // Set source type
            int sourceTypeIndex = -1;
            for (int i = 0; i < sourceTypeValues.length; i++) {
                if (sourceTypeValues[i] == rule.getSourceType()) {
                    sourceTypeIndex = i;
                    break;
                }
            }
            if (sourceTypeIndex >= 0) {
                sourceTypeInput.setText(sourceTypes[sourceTypeIndex], false);
                updateSourceInputVisibility(sourceInputLayout, sourceInput, rule.getSourceType());
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
                                            sourceInput.setText(feedName);
                                        });
                                        break;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Not a valid feed ID, use as-is
                                requireActivity().runOnUiThread(() -> {
                                    sourceInput.setText(rule.getSourceId());
                                });
                            }
                        } catch (Exception e) {
                            android.util.Log.e(TAG, "Error loading feed name", e);
                        }
                    });
                } else {
                    // Tag or other - use sourceId as-is
                    sourceInput.setText(rule.getSourceId());
                }
            }

            // Set selection method
            int selectionMethodIndex = -1;
            for (int i = 0; i < selectionMethodValues.length; i++) {
                if (selectionMethodValues[i] == rule.getSelectionMethod()) {
                    selectionMethodIndex = i;
                    break;
                }
            }
            if (selectionMethodIndex >= 0) {
                selectionMethodInput.setText(selectionMethods[selectionMethodIndex], false);
            }

            // Set count
            if (rule.getCount() != null) {
                int count = rule.getCount();
                if (count >= 1 && count <= 20) {
                    countInput.setValue(count);
                } else {
                    countInput.setValue(10); // Default if out of range
                }
            }
        } else {
            // Default values for new rule
            sourceTypeInput.setText(sourceTypes[0], false); // FEED
            selectionMethodInput.setText(selectionMethods[0], false); // OLDEST
            countInput.setValue(10); // Default count
            updateSourceInputVisibility(sourceInputLayout, sourceInput, RefillRule.SourceType.FEED);
        }

        // Store feed name to ID mapping for later use
        final Map<String, Long>[] feedNameToIdRef = new Map[]{new HashMap<>()};
        final Map<Long, String>[] feedIdToNameRef = new Map[]{new HashMap<>()};

        // Load feeds/tags for source dropdown (on background thread)
        executor.submit(() -> {
            try {
                java.util.List<Feed> feeds = DBReader.getFeedList();
                java.util.List<String> feedNames = new java.util.ArrayList<>();
                feedNameToIdRef[0] = new HashMap<>();
                feedIdToNameRef[0] = new HashMap<>();
                for (Feed feed : feeds) {
                    String feedName = feed.getTitle();
                    feedNames.add(feedName);
                    feedNameToIdRef[0].put(feedName, feed.getId());
                    feedIdToNameRef[0].put(feed.getId(), feedName);
                }

                // Get tags from NavDrawerData
                FeedOrder feedOrder = UserPreferences.getFeedOrder();
                FeedCounter feedCounter = UserPreferences.getFeedCounterSetting();
                de.danoeh.antennapod.storage.database.NavDrawerData navData =
                        DBReader.getNavDrawerData(null, feedOrder, feedCounter, 0);
                java.util.List<String> tagNames = new java.util.ArrayList<>();
                if (navData != null && navData.tags != null) {
                    for (de.danoeh.antennapod.storage.database.NavDrawerData.TagItem tag : navData.tags) {
                        if (tag != null && tag.getTitle() != null
                                && !tag.getTitle().equals(FeedPreferences.TAG_ROOT)
                                && !tag.getTitle().equals(FeedPreferences.TAG_UNTAGGED)) {
                            tagNames.add(tag.getTitle());
                        }
                    }
                }

                requireActivity().runOnUiThread(() -> {
                    // Setup source dropdown based on source type
                    sourceTypeInput.setOnItemClickListener((parent, view, position, id) -> {
                        RefillRule.SourceType selectedType = sourceTypeValues[position];
                        updateSourceInputVisibility(sourceInputLayout, sourceInput, selectedType);

                        // Update source dropdown based on type
                        if (selectedType == RefillRule.SourceType.FEED) {
                            ArrayAdapter<String> feedAdapter = new ArrayAdapter<>(requireContext(),
                                    android.R.layout.simple_dropdown_item_1line, feedNames);
                            sourceInput.setAdapter(feedAdapter);
                            sourceInput.setThreshold(1);
                            sourceInput.setOnClickListener(v -> sourceInput.showDropDown());
                        } else if (selectedType == RefillRule.SourceType.TAG) {
                            ArrayAdapter<String> tagAdapter = new ArrayAdapter<>(requireContext(),
                                    android.R.layout.simple_dropdown_item_1line, tagNames);
                            sourceInput.setAdapter(tagAdapter);
                            sourceInput.setThreshold(1);
                            sourceInput.setOnClickListener(v -> sourceInput.showDropDown());
                        }
                    });

                    // Trigger initial source dropdown setup
                    RefillRule.SourceType currentType = rule != null && rule.getSourceType() != null
                            ? rule.getSourceType() : RefillRule.SourceType.FEED;
                    if (currentType == RefillRule.SourceType.FEED) {
                        ArrayAdapter<String> feedAdapter = new ArrayAdapter<>(requireContext(),
                                android.R.layout.simple_dropdown_item_1line, feedNames);
                        sourceInput.setAdapter(feedAdapter);
                        sourceInput.setThreshold(1);
                        sourceInput.setOnClickListener(v -> sourceInput.showDropDown());
                    } else if (currentType == RefillRule.SourceType.TAG) {
                        ArrayAdapter<String> tagAdapter = new ArrayAdapter<>(requireContext(),
                                android.R.layout.simple_dropdown_item_1line, tagNames);
                        sourceInput.setAdapter(tagAdapter);
                        sourceInput.setThreshold(1);
                        sourceInput.setOnClickListener(v -> sourceInput.showDropDown());
                    }
                });
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error loading feeds/tags", e);
            }
        });

        // Build dialog
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(rule != null ? R.string.edit_rule_label
                        : (insertAtTop ? R.string.insert_rule_label : R.string.add_rule_label))
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // Validate and save rule (feedNameToId will be populated by background thread)
                    // Use a callback to ensure feedNameToId is available
                    executor.submit(() -> {
                        try {
                            // Wait for feeds to load if not already loaded
                            if (feedNameToIdRef[0].isEmpty()) {
                                java.util.List<Feed> feeds = DBReader.getFeedList();
                                feedNameToIdRef[0] = new HashMap<>();
                                for (Feed feed : feeds) {
                                    feedNameToIdRef[0].put(feed.getTitle(), feed.getId());
                                }
                            }
                            requireActivity().runOnUiThread(() -> {
                                saveRuleFromDialog(rule, insertAtTop, sourceTypeInput, sourceInput,
                                        selectionMethodInput, countInput, sourceTypeValues, selectionMethodValues,
                                        feedNameToIdRef[0]);
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
     * Update source input visibility based on source type.
     *
     * @param sourceInputLayout Layout for source input
     * @param sourceInput Source input field
     * @param sourceType Selected source type
     */
    private void updateSourceInputVisibility(
            com.google.android.material.textfield.TextInputLayout sourceInputLayout,
            AutoCompleteTextView sourceInput,
            RefillRule.SourceType sourceType) {
        if (sourceType == RefillRule.SourceType.INBOX) {
            sourceInputLayout.setVisibility(View.GONE);
            sourceInput.setText("");
        } else {
            sourceInputLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Save rule from dialog inputs.
     *
     * @param existingRule Existing rule to update (null for new rule)
     * @param insertAtTop If true, insert at position 0; if false, append at end (only for new rules)
     * @param sourceTypeInput Source type input
     * @param sourceInput Source input
     * @param selectionMethodInput Selection method input
     * @param countInput Count input
     * @param sourceTypeValues Source type enum values
     * @param selectionMethodValues Selection method enum values
     * @param feedNameToId Map of feed names to feed IDs (for FEED source type)
     */
    private void saveRuleFromDialog(@Nullable RefillRule existingRule,
                                     boolean insertAtTop,
                                     AutoCompleteTextView sourceTypeInput,
                                     AutoCompleteTextView sourceInput,
                                     AutoCompleteTextView selectionMethodInput,
                                     NumberPicker countInput,
                                     RefillRule.SourceType[] sourceTypeValues,
                                     RefillRule.SelectionMethod[] selectionMethodValues,
                                     Map<String, Long> feedNameToId) {
        // Validate inputs
        String sourceTypeText = sourceTypeInput.getText().toString();
        String selectionMethodText = selectionMethodInput.getText().toString();
        int count = countInput.getValue();

        // Find selected source type
        RefillRule.SourceType sourceType = null;
        String[] sourceTypeStrings = {
                requireContext().getString(R.string.source_type_feed),
                requireContext().getString(R.string.source_type_tag),
                requireContext().getString(R.string.source_type_inbox)
        };
        for (int i = 0; i < sourceTypeStrings.length; i++) {
            if (sourceTypeStrings[i].equals(sourceTypeText)) {
                sourceType = sourceTypeValues[i];
                break;
            }
        }

        // Find selected selection method
        RefillRule.SelectionMethod selectionMethod = null;
        String[] selectionMethodStrings = {
                requireContext().getString(R.string.selection_method_oldest),
                requireContext().getString(R.string.selection_method_newest),
                requireContext().getString(R.string.selection_method_random)
        };
        for (int i = 0; i < selectionMethodStrings.length; i++) {
            if (selectionMethodStrings[i].equals(selectionMethodText)) {
                selectionMethod = selectionMethodValues[i];
                break;
            }
        }

        // Count is already validated by NumberPicker (1-20 range)

        // Validate source (required for FEED and TAG)
        String sourceId = sourceInput.getText().toString().trim();
        if (sourceType == RefillRule.SourceType.FEED) {
            if (TextUtils.isEmpty(sourceId)) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.edit_rule_label)
                        .setMessage(R.string.rule_source_required)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            // Convert feed name to feed ID
            Long feedId = feedNameToId != null ? feedNameToId.get(sourceId) : null;
            if (feedId == null) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.edit_rule_label)
                        .setMessage("Feed not found: " + sourceId)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            sourceId = String.valueOf(feedId);
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
            sourceId = null;
        }

        // Save rule
        if (existingRule != null) {
            // Update existing rule
            updateRule(existingRule, sourceType, sourceId, selectionMethod, count);
        } else {
            // Create new rule
            createRule(insertAtTop, sourceType, sourceId, selectionMethod, count);
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
        QueueRuleset ruleset = viewModel.getRulesetValue();
        executor.submit(() -> {
            try {
                long rulesetId;
                if (ruleset == null) {
                    // Create ruleset first if it doesn't exist
                    long queueId = viewModel.getCurrentQueueId();
                    rulesetId = DBWriter.createQueueRuleset(queueId).get();
                } else {
                    rulesetId = ruleset.getId();
                }

                // Determine position
                int position;
                if (insertAtTop) {
                    // Insert at position 0 - shift existing rules up
                    position = 0;
                    java.util.List<RefillRule> existingRules = DBReader.getRefillRules(rulesetId);
                    if (!existingRules.isEmpty()) {
                        // Shift all existing rules up by 1
                        Map<Long, Integer> positionMap = new HashMap<>();
                        for (RefillRule rule : existingRules) {
                            positionMap.put(rule.getId(), rule.getPosition() + 1);
                        }
                        DBWriter.reorderRefillRules(rulesetId, positionMap).get();
                    }
                } else {
                    // Append at end - find highest position and add 1
                    java.util.List<RefillRule> existingRules = DBReader.getRefillRules(rulesetId);
                    if (existingRules.isEmpty()) {
                        position = 0;
                    } else {
                        int maxPosition = -1;
                        for (RefillRule rule : existingRules) {
                            if (rule.getPosition() > maxPosition) {
                                maxPosition = rule.getPosition();
                            }
                        }
                        position = maxPosition + 1;
                    }
                }

                // Create rule
                DBWriter.createRefillRule(rulesetId, position, RefillRule.RuleType.ADD_EPISODES,
                        selectionMethod, count, sourceType, sourceId).get();

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
     * Reorder rules after drag-and-drop.
     * Updates rule positions in the database.
     *
     * @param fromPosition Original position
     * @param toPosition New position
     */
    private void reorderRules(int fromPosition, int toPosition) {
        if (adapter == null) {
            return;
        }

        // Get current rules from adapter (already reordered by ItemTouchHelper)
        java.util.List<RefillRule> rules = new java.util.ArrayList<>(adapter.rules);

        // Build position map for reordering - map each rule ID to its new position
        Map<Long, Integer> positionMap = new HashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            RefillRule rule = rules.get(i);
            if (rule != null) {
                // Position in list is the new position
                positionMap.put(rule.getId(), i);
            }
        }

        // Update positions in database
        QueueRuleset ruleset = viewModel.getRulesetValue();
        if (ruleset != null) {
            executor.submit(() -> {
                try {
                    DBWriter.reorderRefillRules(ruleset.getId(), positionMap).get();
                    // Refresh ruleset on main thread
                    requireActivity().runOnUiThread(() -> viewModel.refreshRuleset());
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error reordering rules", e);
                }
            });
        }
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
