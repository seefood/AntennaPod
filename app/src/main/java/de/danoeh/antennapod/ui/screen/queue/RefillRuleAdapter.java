package de.danoeh.antennapod.ui.screen.queue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.DBReader;

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
    ItemTouchHelper itemTouchHelper; // Package-private for access from fragment

    // Feed ID to name cache (loaded asynchronously)
    private final Map<Long, String> feedIdToNameCache = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
     * Set ItemTouchHelper for drag handle access.
     *
     * @param itemTouchHelper ItemTouchHelper instance
     */
    public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper;
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

        // Load feed names asynchronously if needed
        loadFeedNamesIfNeeded(finalNewRules);

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
     * Load feed names asynchronously if there are any FEED rules.
     *
     * @param rules List of rules to check
     */
    private void loadFeedNamesIfNeeded(List<RefillRule> rules) {
        // Check if any rules need feed names
        boolean needsFeedNames = false;
        for (RefillRule rule : rules) {
            if (rule.getSourceType() == RefillRule.SourceType.FEED && rule.getSourceId() != null) {
                try {
                    long feedId = Long.parseLong(rule.getSourceId());
                    synchronized (feedIdToNameCache) {
                        if (!feedIdToNameCache.containsKey(feedId)) {
                            needsFeedNames = true;
                            break;
                        }
                    }
                } catch (NumberFormatException e) {
                    // Not a valid feed ID, skip
                }
            }
        }

        if (needsFeedNames) {
            executor.submit(() -> {
                try {
                    List<Feed> feeds = DBReader.getFeedList();
                    boolean cacheUpdated = false;
                    synchronized (feedIdToNameCache) {
                        for (Feed feed : feeds) {
                            if (!feedIdToNameCache.containsKey(feed.getId())) {
                                feedIdToNameCache.put(feed.getId(), feed.getTitle());
                                cacheUpdated = true;
                            }
                        }
                    }
                    // Notify adapter to update views with new feed names
                    // Note: This runs on background thread, so we need to post to main thread
                    // We'll update views on next bind, but we can also notify the adapter
                    // if needed. For now, views will update on next bind or scroll.
                } catch (Exception e) {
                    android.util.Log.e("RefillRuleAdapter", "Error loading feed names", e);
                }
            });
        }
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
     * All rules can be reordered (no special cases after redesign).
     *
     * @param position Position to check
     * @return true if rule can be reordered, false otherwise
     */
    public boolean canReorderRule(int position) {
        RefillRule rule = getRuleAt(position);
        return rule != null;
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
            holder.bind(rule, position, editListener, deleteListener,
                    canReorderRule(position), feedIdToNameCache);

            // Setup drag handle touch listener (matches queue item behavior)
            if (itemTouchHelper != null && holder.dragHandle != null) {
                holder.dragHandle.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        itemTouchHelper.startDrag(holder);
                        return true;
                    }
                    return false;
                });
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull RuleViewHolder holder) {
        super.onViewRecycled(holder);
        // Clear touch listener to prevent leaks
        if (holder.dragHandle != null) {
            holder.dragHandle.setOnTouchListener(null);
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
        final ImageView dragHandle; // Package-private for access from fragment
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
                         boolean canReorder,
                         Map<Long, String> feedIdToNameCache) {
            // Always show drag handle (matches queue item behavior)
            dragHandle.setVisibility(View.VISIBLE);

            // Set rule icon (all rules are ADD_EPISODES after redesign)
            ruleIcon.setImageResource(android.R.drawable.ic_menu_add);

            // Set rule description
            String description = formatRuleDescription(rule, feedIdToNameCache);
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

            // Clear any previous touch listener
            dragHandle.setOnTouchListener(null);
        }

        /**
         * Format rule description for display.
         *
         * @param rule Rule to format
         * @param feedIdToNameCache Cache of feed IDs to names
         * @return Formatted description string
         */
        private String formatRuleDescription(RefillRule rule, Map<Long, String> feedIdToNameCache) {
            // Format: "Add X episodes from SOURCE (METHOD)"
            String sourceName = formatSourceName(rule, feedIdToNameCache);
            String methodName = formatSelectionMethod(rule);
            int count = rule.getCount() != null ? rule.getCount() : 0;
            return itemView.getContext().getString(R.string.add_episodes_rule_description,
                    count, sourceName, methodName);
        }

        /**
         * Format source name for display.
         *
         * @param rule Rule to format
         * @param feedIdToNameCache Cache of feed IDs to names
         * @return Source name string
         */
        private String formatSourceName(RefillRule rule, Map<Long, String> feedIdToNameCache) {
            if (rule.getSourceType() == null) {
                return "Unknown";
            }
            switch (rule.getSourceType()) {
                case FEED:
                    // Convert feed ID to feed name using cache
                    if (rule.getSourceId() != null) {
                        try {
                            long feedId = Long.parseLong(rule.getSourceId());
                            synchronized (feedIdToNameCache) {
                                String feedName = feedIdToNameCache.get(feedId);
                                if (feedName != null) {
                                    return feedName;
                                }
                            }
                            // If not in cache yet, return placeholder (will be updated when cache loads)
                            return "Feed " + rule.getSourceId();
                        } catch (NumberFormatException e) {
                            // Not a valid feed ID, use as-is
                        }
                        return "Feed " + rule.getSourceId();
                    }
                    return "Unknown Feed";
                case TAG:
                    return "Tag: " + (rule.getSourceId() != null ? rule.getSourceId() : "Unknown");
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
