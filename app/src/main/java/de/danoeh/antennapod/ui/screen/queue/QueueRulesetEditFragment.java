package de.danoeh.antennapod.ui.screen.queue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.RefillRule;

/**
 * Fragment for viewing and editing the RefillRuleset associated with the active queue.
 */
public class QueueRulesetEditFragment extends Fragment
        implements MaterialToolbar.OnMenuItemClickListener {

    public static final String TAG = "QueueRulesetEditFragment";
    private static final int MAX_RULES = 20;
    private static final long QUEUE_ID = 1L;

    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private RefillRuleAdapter adapter;
    private QueueRulesetViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_queue_ruleset_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView = view.findViewById(R.id.empty_view);

        toolbar.setTitle(getString(R.string.queue_edit_rules));
        toolbar.inflateMenu(R.menu.queue_ruleset_edit);
        toolbar.setOnMenuItemClickListener(this);
        toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        viewModel = new ViewModelProvider(this).get(QueueRulesetViewModel.class);
        viewModel.init(QUEUE_ID);

        adapter = new RefillRuleAdapter(QUEUE_ID, (rule, position) -> {
            if (Boolean.TRUE.equals(viewModel.getIsRefillInProgress().getValue())) {
                Snackbar.make(view, R.string.rule_editor_disabled_during_refill,
                        Snackbar.LENGTH_SHORT).show();
                return;
            }
            openEditDialog(rule, position);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        adapter.attachToRecyclerView(recyclerView);

        viewModel.getRulesData().observe(getViewLifecycleOwner(), this::onRulesUpdated);
        viewModel.getIsRefillInProgress().observe(getViewLifecycleOwner(), inProgress -> {
            refreshMenuState(inProgress != null && inProgress);
        });

        // Ensure ruleset exists
        viewModel.ensureRulesetAndRun(() -> { });
    }

    private void onRulesUpdated(QueueRulesetViewModel.RulesData data) {
        if (data == null || data.rules.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
        if (data != null) {
            adapter.submitList(data.rules, data.feedTitles);
        }
        refreshMenuState(Boolean.TRUE.equals(viewModel.getIsRefillInProgress().getValue()));
    }

    private void refreshMenuState(boolean refillInProgress) {
        if (toolbar.getMenu() == null) {
            return;
        }
        int ruleCount = adapter.getCurrentList().size();
        boolean atCap = ruleCount >= MAX_RULES;

        MenuItem addRule = toolbar.getMenu().findItem(R.id.action_add_rule);
        MenuItem addAtTop = toolbar.getMenu().findItem(R.id.action_add_rule_at_top);
        if (addRule != null) {
            addRule.setEnabled(!refillInProgress && !atCap);
        }
        if (addAtTop != null) {
            addAtTop.setEnabled(!refillInProgress && !atCap);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (Boolean.TRUE.equals(viewModel.getIsRefillInProgress().getValue())) {
            View view = getView();
            if (view != null) {
                Snackbar.make(view, R.string.rule_editor_disabled_during_refill,
                        Snackbar.LENGTH_SHORT).show();
            }
            return true;
        }
        if (adapter.getCurrentList().size() >= MAX_RULES) {
            View view = getView();
            if (view != null) {
                Snackbar.make(view, R.string.rule_limit_reached, Snackbar.LENGTH_SHORT).show();
            }
            return true;
        }

        int itemId = item.getItemId();
        if (itemId == R.id.action_add_rule) {
            openAddDialog(adapter.getCurrentList().size());
            return true;
        } else if (itemId == R.id.action_add_rule_at_top) {
            openAddDialog(0);
            return true;
        }
        return false;
    }

    private void openAddDialog(int position) {
        long rulesetId = viewModel.getRulesetId();
        if (rulesetId < 0) {
            return;
        }
        new AddEditRuleDialog(requireContext(), rulesetId, null, position,
                () -> viewModel.loadRules()).show();
    }

    private void openEditDialog(RefillRule rule, int position) {
        new AddEditRuleDialog(requireContext(), rule.getRulesetId(), rule, position,
                () -> viewModel.loadRules()).show();
    }
}
