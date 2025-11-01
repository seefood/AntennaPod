package de.danoeh.antennapod.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.QueueMetadata;

/**
 * RecyclerView adapter for displaying queues in a list.
 *
 * Displays queue name and color swatch for each queue.
 * Supports queue selection callbacks.
 */
public class QueueListAdapter extends RecyclerView.Adapter<QueueListAdapter.QueueViewHolder> {
    private List<QueueMetadata> queueList;
    private OnQueueSelectedListener listener;
    private OnQueueEditRequestListener editListener;
    private long currentQueueId = -1;

    /**
     * Interface for queue selection callbacks.
     */
    public interface OnQueueSelectedListener {
        /**
         * Called when a queue item is tapped.
         *
         * @param queueId ID of the selected queue
         */
        void onQueueSelected(long queueId);
    }

    /**
     * Interface for queue edit button callbacks.
     */
    public interface OnQueueEditRequestListener {
        /**
         * Called when edit button is tapped for a queue.
         *
         * @param queueId ID of the queue to edit
         */
        void onEditQueueRequested(long queueId);
    }

    /**
     * Create adapter with queue list.
     *
     * @param queueList List of QueueMetadata objects to display
     */
    public QueueListAdapter(List<QueueMetadata> queueList) {
        this.queueList = queueList;
    }

    /**
     * Set the callback for queue selection.
     *
     * @param listener OnQueueSelectedListener callback
     */
    public void setOnQueueSelectedListener(OnQueueSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * Set the callback for queue edit requests.
     *
     * @param editListener OnQueueEditRequestListener callback
     */
    public void setOnQueueEditRequestListener(OnQueueEditRequestListener editListener) {
        this.editListener = editListener;
    }

    /**
     * Set the ID of the currently active queue.
     * Used to highlight the active queue in the list.
     *
     * @param currentQueueId ID of the current queue
     */
    public void setCurrentQueueId(long currentQueueId) {
        this.currentQueueId = currentQueueId;
        notifyDataSetChanged();
    }

    /**
     * Update the queue list.
     *
     * @param newQueueList New list of QueueMetadata objects
     */
    public void updateQueueList(List<QueueMetadata> newQueueList) {
        this.queueList = newQueueList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.queue_item, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        QueueMetadata queue = queueList.get(position);
        holder.bind(queue, currentQueueId, listener, editListener);
    }

    @Override
    public int getItemCount() {
        return queueList != null ? queueList.size() : 0;
    }

    /**
     * ViewHolder for queue list items.
     */
    public static class QueueViewHolder extends RecyclerView.ViewHolder {
        private final TextView queueNameText;
        private final ImageView colorSwatch;
        private final ImageView editButton;

        public QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            queueNameText = itemView.findViewById(R.id.queue_item_name);
            colorSwatch = itemView.findViewById(R.id.queue_item_color_swatch);
            editButton = itemView.findViewById(R.id.queue_item_edit_button);
        }

        /**
         * Bind queue data to the view holder.
         *
         * @param queue Queue metadata to display
         * @param currentQueueId ID of the currently active queue
         * @param listener Callback for queue selection
         * @param editListener Callback for edit button
         */
        public void bind(QueueMetadata queue,
                         long currentQueueId,
                         OnQueueSelectedListener listener,
                         OnQueueEditRequestListener editListener) {
            queueNameText.setText(queue.getName());
            colorSwatch.setBackgroundColor(queue.getColor());

            // Highlight current queue
            if (queue.getId() == currentQueueId) {
                itemView.setBackgroundColor(itemView.getContext()
                        .getColor(R.color.selected_item_background));
            } else {
                itemView.setBackgroundColor(itemView.getContext()
                        .getColor(android.R.color.transparent));
            }

            // Handle queue selection
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onQueueSelected(queue.getId());
                }
            });

            // Handle edit button
            if (editButton != null) {
                editButton.setOnClickListener(v -> {
                    if (editListener != null) {
                        editListener.onEditQueueRequested(queue.getId());
                    }
                });
            }
        }
    }
}
