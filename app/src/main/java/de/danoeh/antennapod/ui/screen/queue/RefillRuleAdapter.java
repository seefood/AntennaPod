package de.danoeh.antennapod.ui.screen.queue;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.DBWriter;

/**
 * RecyclerView adapter for displaying and reordering RefillRules.
 */
class RefillRuleAdapter extends RecyclerView.Adapter<RefillRuleAdapter.RuleViewHolder> {

    interface OnEditRequestedListener {
        void onEditRequested(RefillRule rule, int position);
    }

    private final List<RefillRule> rules = new ArrayList<>();
    private Map<Long, String> feedTitles = new HashMap<>();
    private final long rulesetId;
    private final OnEditRequestedListener editListener;
    private ItemTouchHelper touchHelper;

    RefillRuleAdapter(long rulesetId, @NonNull OnEditRequestedListener editListener) {
        this.rulesetId = rulesetId;
        this.editListener = editListener;
        setHasStableIds(true);
    }

    void attachToRecyclerView(@NonNull RecyclerView rv) {
        DragCallback callback = new DragCallback();
        touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(rv);
    }

    void setFeedTitles(@NonNull Map<Long, String> titles) {
        feedTitles = titles;
        notifyDataSetChanged();
    }

    void submitList(@NonNull List<RefillRule> newRules) {
        rules.clear();
        rules.addAll(newRules);
        notifyDataSetChanged();
    }

    List<RefillRule> getCurrentList() {
        return Collections.unmodifiableList(rules);
    }

    @Override
    public long getItemId(int position) {
        return rules.get(position).getId();
    }

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_refill_rule, parent, false);
        return new RuleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        RefillRule rule = rules.get(position);
        holder.bind(rule, position);
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    class RuleViewHolder extends RecyclerView.ViewHolder {
        final View dragHandle;
        final TextView sourceLabel;
        final TextView methodCountLabel;

        RuleViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            sourceLabel = itemView.findViewById(R.id.source_label);
            methodCountLabel = itemView.findViewById(R.id.method_count_label);
        }

        void bind(RefillRule rule, int position) {
            sourceLabel.setText(formatSource(rule));
            methodCountLabel.setText(formatMethodCount(rule));

            itemView.setOnClickListener(v -> editListener.onEditRequested(rule, position));

            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null) {
                    touchHelper.startDrag(this);
                }
                return false;
            });
        }

        private String formatSource(RefillRule rule) {
            android.content.Context ctx = itemView.getContext();
            switch (rule.getSourceType()) {
                case TAG:
                    return ctx.getString(R.string.source_tag) + ": " + rule.getSourceId();
                case INBOX:
                    return ctx.getString(R.string.source_inbox);
                default:
                    String feedTitle = null;
                    try {
                        long feedId = Long.parseLong(rule.getSourceId());
                        feedTitle = feedTitles.get(feedId);
                    } catch (NumberFormatException ignored) {
                        // fall through to raw id
                    }
                    String displayName = feedTitle != null ? feedTitle : rule.getSourceId();
                    return ctx.getString(R.string.source_feed) + ": " + displayName;
            }
        }

        private String formatMethodCount(RefillRule rule) {
            android.content.Context ctx = itemView.getContext();
            String method;
            switch (rule.getSelectionMethod()) {
                case NEWEST:
                    method = ctx.getString(R.string.selection_newest);
                    break;
                case RANDOM:
                    method = ctx.getString(R.string.selection_random);
                    break;
                default:
                    method = ctx.getString(R.string.selection_oldest);
                    break;
            }
            return method + " · " + rule.getCount();
        }
    }

    private class DragCallback extends ItemTouchHelper.Callback {
        private int dragFromPosition = -1;
        private int dragToPosition = -1;

        @Override
        public int getMovementFlags(@NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                    ItemTouchHelper.RIGHT);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (from < 0 || to < 0 || from >= rules.size() || to >= rules.size()) {
                return false;
            }
            if (dragFromPosition < 0) {
                dragFromPosition = from;
            }
            dragToPosition = to;
            Collections.swap(rules, from, to);
            notifyItemMoved(from, to);
            return true;
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if (dragFromPosition >= 0 && dragToPosition >= 0
                    && dragFromPosition != dragToPosition) {
                // Persist new order
                List<Long> orderedIds = new ArrayList<>();
                for (RefillRule r : rules) {
                    orderedIds.add(r.getId());
                }
                DBWriter.reorderRefillRules(rulesetId, orderedIds);
            }
            dragFromPosition = -1;
            dragToPosition = -1;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getBindingAdapterPosition();
            if (position < 0 || position >= rules.size()) {
                return;
            }
            RefillRule deleted = rules.get(position);
            rules.remove(position);
            notifyItemRemoved(position);

            // Snapshot for undo
            final int deletedPosition = position;

            DBWriter.deleteRefillRule(deleted.getId());

            View rootView = viewHolder.itemView.getRootView();
            Snackbar.make(rootView, R.string.rule_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo, v ->
                            io.reactivex.rxjava3.core.Observable.fromCallable(() -> {
                                DBWriter.createRefillRule(rulesetId, deletedPosition,
                                        deleted.getSelectionMethod(),
                                        deleted.getCount(),
                                        deleted.getSourceType(),
                                        deleted.getSourceId()).get();
                                return true;
                            })
                                    .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                                    .subscribe(ignored -> { }, throwable -> { })
                    )
                    .show();
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false; // drag only via handle
        }
    }
}
