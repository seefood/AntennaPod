package de.danoeh.antennapod.ui.screen.queue;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
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
    public static final String TAG = "QueueRulesetEditFragment";

    private QueueRulesetViewModel viewModel;
    private RecyclerView rulesList;
    private TextView emptyView;
    private FloatingActionButton addRuleButton;
    private MaterialToolbar toolbar;
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

        // Setup toolbar with back navigation
        toolbar.setNavigationOnClickListener(v -> {
            getParentFragmentManager().popBackStack();
        });

        // Setup RecyclerView
        rulesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        // TODO: Create and set adapter for rules list
        // rulesList.setAdapter(new RefillRuleAdapter(Collections.emptyList()));

        // Setup add rule button
        addRuleButton.setOnClickListener(v -> {
            showAddRuleDialog();
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
            } else {
                emptyView.setVisibility(View.GONE);
                rulesList.setVisibility(View.VISIBLE);
                // TODO: Update adapter with new rules
                // adapter.updateRules(rules);
            }
        });
    }

    /**
     * Show dialog for adding a new rule.
     * Hides "Clear queue" option if it already exists as first rule (FR-032).
     */
    private void showAddRuleDialog() {
        // Check if "Clear queue" rule already exists by checking cached rules
        // This avoids I/O on main thread since rules are already loaded in ViewModel
        java.util.List<RefillRule> rules = viewModel.getRulesValue();
        boolean hasClearQueueRule = false;
        if (rules != null && !rules.isEmpty()) {
            // Check if first rule is CLEAR_QUEUE (it should be at position 0)
            RefillRule firstRule = rules.get(0);
            if (firstRule != null && firstRule.getRuleType() == RefillRule.RuleType.CLEAR_QUEUE) {
                hasClearQueueRule = true;
            }
        }
        showAddRuleDialogInternal(hasClearQueueRule);
    }

    /**
     * Internal method to show the add rule dialog with the appropriate options.
     *
     * @param hasClearQueueRule Whether a "Clear queue" rule already exists
     */
    private void showAddRuleDialogInternal(boolean hasClearQueueRule) {
        // Build rule type options
        String[] ruleTypes;
        RefillRule.RuleType[] ruleTypeValues;
        if (hasClearQueueRule) {
            // Hide "Clear queue" option if it already exists (FR-032)
            ruleTypes = new String[]{"Add Episodes"};
            ruleTypeValues = new RefillRule.RuleType[]{RefillRule.RuleType.ADD_EPISODES};
        } else {
            ruleTypes = new String[]{"Clear Queue", "Add Episodes"};
            ruleTypeValues = new RefillRule.RuleType[]{
                    RefillRule.RuleType.CLEAR_QUEUE,
                    RefillRule.RuleType.ADD_EPISODES
            };
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_rule_label)
                .setItems(ruleTypes, (dialog, which) -> {
                    RefillRule.RuleType selectedType = ruleTypeValues[which];
                    if (selectedType == RefillRule.RuleType.CLEAR_QUEUE) {
                        // Create "Clear queue" rule at position 0
                        createClearQueueRule();
                    } else {
                        // TODO: Show dialog for "Add Episodes" rule configuration
                        // For now, just create a basic rule
                        showAddEpisodesRuleDialog();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Create a "Clear queue" rule at position 0.
     */
    private void createClearQueueRule() {
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
                // Create clear queue rule at position 0
                DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.CLEAR_QUEUE,
                        null, null, null, null).get();
                // Refresh ruleset on main thread
                requireActivity().runOnUiThread(() -> viewModel.refreshRuleset());
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error creating clear queue rule", e);
            }
        });
    }

    /**
     * Show dialog for configuring "Add Episodes" rule.
     * TODO: Implement full configuration dialog (source, count, selection method)
     */
    private void showAddEpisodesRuleDialog() {
        // For now, show a simple message indicating this needs to be implemented
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_rule_label)
                .setMessage("Add Episodes rule configuration will be implemented in subsequent tasks.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
