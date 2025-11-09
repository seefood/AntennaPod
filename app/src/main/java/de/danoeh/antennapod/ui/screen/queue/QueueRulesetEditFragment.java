package de.danoeh.antennapod.ui.screen.queue;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import de.danoeh.antennapod.model.feed.QueueRuleset;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.DBWriter;
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
     * Only shows "Add Episodes" option (Clear queue is handled separately in QueueFragment).
     *
     * @param insertAtTop If true, insert at position 0; if false, append at end
     */
    private void showAddRuleDialog(boolean insertAtTop) {
        // Only show "Add Episodes" option - Clear queue is handled in QueueFragment menu
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(insertAtTop ? R.string.insert_rule_label : R.string.add_rule_label)
                .setItems(new String[]{"Add Episodes"}, (dialog, which) -> {
                    // Show dialog for "Add Episodes" rule configuration
                    showAddEpisodesRuleDialog(insertAtTop);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Show dialog for configuring "Add Episodes" rule.
     * TODO: Implement full configuration dialog (source, count, selection method)
     *
     * @param insertAtTop If true, insert at position 0; if false, append at end
     */
    private void showAddEpisodesRuleDialog(boolean insertAtTop) {
        // For now, show a simple message indicating this needs to be implemented
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(insertAtTop ? R.string.insert_rule_label : R.string.add_rule_label)
                .setMessage("Add Episodes rule configuration will be implemented in subsequent tasks.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * Show dialog for editing an existing rule.
     *
     * @param rule Rule to edit
     */
    private void showEditRuleDialog(RefillRule rule) {
        // TODO: Implement edit rule dialog (T036)
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit_rule_label)
                .setMessage("Edit rule dialog will be implemented in task T036.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
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
