package de.danoeh.antennapod.ui.screen.queue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.RefillRule;

/**
 * RecyclerView adapter for displaying refill rules in a list.
 *
 * <p>Displays rule descriptions with edit and delete buttons.
 * Supports drag-to-reorder with protection for Clear queue rule at position 1.
 */
public class RefillRuleAdapter extends RecyclerView.Adapter<RefillRuleAdapter.RuleViewHolder> {
    List<RefillRule> rules = new ArrayList<>(); // Package-private for access from fragment
    OnRuleEditListener editListener; // Package-private for access from fragment
    OnRuleDeleteListener deleteListener; // Package-private for access from fragment
    OnRuleReorderListener reorderListener; // Package-private for access from fragment

    /**
     * Interface for rule edit callbacks.
     */
    public interface OnRuleEditListener {
        /**
         * Called when edit button is tapped for a rule.
         *
         * @param rule Rule to edit
         */
        void onRuleEdit(RefillRule rule);
    }

    /**
     * Interface for rule delete callbacks.
     */
    public interface OnRuleDeleteListener {
        /**
         * Called when delete button is tapped for a rule.
         *
         * @param rule Rule to delete
         */
        void onRuleDelete(RefillRule rule);
    }

    /**
     * Interface for rule reorder callbacks.
     */
    public interface OnRuleReorderListener {
        /**
         * Called when rules are reordered via drag-and-drop.
         *
         * @param fromPosition Original position
         * @param toPosition New position
         */
        void onRuleReordered(int fromPosition, int toPosition);
    }

    /**
     * Set the callback for rule edit requests.
     *
     * @param listener OnRuleEditListener callback
     */
    public void setOnRuleEditListener(OnRuleEditListener listener) {
        this.editListener = listener;
    }

    /**
     * Set the callback for rule delete requests.
     *
     * @param listener OnRuleDeleteListener callback
     */
    public void setOnRuleDeleteListener(OnRuleDeleteListener listener) {
        this.deleteListener = listener;
    }

    /**
     * Set the callback for rule reorder events.
     *
     * @param listener OnRuleReorderListener callback
     */
    public void setOnRuleReorderListener(OnRuleReorderListener listener) {
        this.reorderListener = listener;
    }

    /**
     * Update the rules list with efficient diffing.
     * Uses DiffUtil to compute differences and only update changed items.
     *
     * @param newRules New list of RefillRule objects
     */
    public void updateRules(List<RefillRule> newRules) {
        final List<RefillRule> finalNewRules = newRules != null
                ? newRules : new ArrayList<>();

        // Use DiffUtil for efficient updates instead of notifyDataSetChanged()
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return rules != null ? rules.size() : 0;
            }

            @Override
            public int getNewListSize() {
                return finalNewRules.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                RefillRule oldRule = rules != null ? rules.get(oldItemPosition) : null;
                RefillRule newRule = finalNewRules.get(newItemPosition);
                return oldRule != null && oldRule.getId() == newRule.getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                RefillRule oldRule = rules != null ? rules.get(oldItemPosition) : null;
                RefillRule newRule = finalNewRules.get(newItemPosition);
                if (oldRule == null) {
                    return false;
                }
                // Compare all relevant fields
                return oldRule.getPosition() == newRule.getPosition()
                        && oldRule.getRuleType() == newRule.getRuleType()
                        && oldRule.getSelectionMethod() == newRule.getSelectionMethod()
                        && Objects.equals(oldRule.getCount(), newRule.getCount())
                        && oldRule.getSourceType() == newRule.getSourceType()
                        && Objects.equals(oldRule.getSourceId(), newRule.getSourceId());
            }
        });

        this.rules = finalNewRules;
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Get the rule at the specified position.
     *
     * @param position Position in the list
     * @return RefillRule at position, or null if invalid
     */
    public RefillRule getRuleAt(int position) {
        if (rules != null && position >= 0 && position < rules.size()) {
            return rules.get(position);
        }
        return null;
    }

    /**
     * Check if a rule at the given position can be reordered.
     * Clear queue rule at position 1 cannot be reordered (FR-033).
     *
     * @param position Position to check
     * @return true if rule can be reordered, false otherwise
     */
    public boolean canReorderRule(int position) {
        RefillRule rule = getRuleAt(position);
        if (rule == null) {
            return false;
        }
        // Clear queue rule at position 1 cannot be reordered (FR-033)
        if (rule.getRuleType() == RefillRule.RuleType.CLEAR_QUEUE && rule.getPosition() == 0) {
            return false;
        }
        return true;
    }

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.refill_rule_item, parent, false);
        return new RuleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        if (rules != null && position >= 0 && position < rules.size()) {
            RefillRule rule = rules.get(position);
            holder.bind(rule, position, editListener, deleteListener, canReorderRule(position));
        }
    }

    @Override
    public int getItemCount() {
        return rules != null ? rules.size() : 0;
    }

    /**
     * ViewHolder for refill rule list items.
     */
    public static class RuleViewHolder extends RecyclerView.ViewHolder {
        private final ImageView dragHandle;
        private final ImageView ruleIcon;
        private final TextView ruleDescription;
        private final ImageView editButton;
        private final ImageView deleteButton;

        public RuleViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.rule_item_drag_handle);
            ruleIcon = itemView.findViewById(R.id.rule_item_icon);
            ruleDescription = itemView.findViewById(R.id.rule_item_description);
            editButton = itemView.findViewById(R.id.rule_item_edit_button);
            deleteButton = itemView.findViewById(R.id.rule_item_delete_button);
        }

        /**
         * Bind rule data to the view holder.
         *
         * @param rule Rule to display
         * @param position Position in the list
         * @param editListener Callback for edit button
         * @param deleteListener Callback for delete button
         * @param canReorder Whether this rule can be reordered
         */
        public void bind(RefillRule rule,
                         int position,
                         OnRuleEditListener editListener,
                         OnRuleDeleteListener deleteListener,
                         boolean canReorder) {
            // Show/hide drag handle based on reorder capability
            dragHandle.setVisibility(canReorder ? View.VISIBLE : View.GONE);

            // Set rule icon based on rule type
            if (rule.getRuleType() == RefillRule.RuleType.CLEAR_QUEUE) {
                ruleIcon.setImageResource(android.R.drawable.ic_menu_delete);
            } else {
                ruleIcon.setImageResource(android.R.drawable.ic_menu_add);
            }

            // Set rule description
            String description = formatRuleDescription(rule);
            ruleDescription.setText(description);

            // Handle edit button
            if (editButton != null) {
                editButton.setOnClickListener(v -> {
                    if (editListener != null) {
                        editListener.onRuleEdit(rule);
                    }
                });
            }

            // Handle delete button
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    if (deleteListener != null) {
                        deleteListener.onRuleDelete(rule);
                    }
                });
            }
        }

        /**
         * Format rule description for display.
         *
         * @param rule Rule to format
         * @return Formatted description string
         */
        private String formatRuleDescription(RefillRule rule) {
            if (rule.getRuleType() == RefillRule.RuleType.CLEAR_QUEUE) {
                return itemView.getContext().getString(R.string.clear_queue_rule_description);
            } else {
                // Format: "Add X episodes from SOURCE (METHOD)"
                String sourceName = formatSourceName(rule);
                String methodName = formatSelectionMethod(rule);
                int count = rule.getCount() != null ? rule.getCount() : 0;
                return itemView.getContext().getString(R.string.add_episodes_rule_description,
                        count, sourceName, methodName);
            }
        }

        /**
         * Format source name for display.
         *
         * @param rule Rule to format
         * @return Source name string
         */
        private String formatSourceName(RefillRule rule) {
            if (rule.getSourceType() == null) {
                return "Unknown";
            }
            switch (rule.getSourceType()) {
                case FEED:
                    return "Feed " + rule.getSourceId();
                case TAG:
                    return "Tag: " + rule.getSourceId();
                case INBOX:
                    return "Inbox";
                default:
                    return "Unknown";
            }
        }

        /**
         * Format selection method for display.
         *
         * @param rule Rule to format
         * @return Selection method string
         */
        private String formatSelectionMethod(RefillRule rule) {
            if (rule.getSelectionMethod() == null) {
                return "Unknown";
            }
            switch (rule.getSelectionMethod()) {
                case OLDEST:
                    return "Oldest";
                case NEWEST:
                    return "Newest";
                case RANDOM:
                    return "Random";
                default:
                    return "Unknown";
            }
        }
    }
}
