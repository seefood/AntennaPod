package de.danoeh.antennapod.ui.common;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

/**
 * Dialog fragment for selecting a destination queue for move/copy operations.
 *
 * <p>Features:
 * - RecyclerView showing all available queues
 * - Filter out source queue for move operations (optional)
 * - Highlight last-used destination
 * - Search functionality (shown when >10 queues)
 * - Create new queue option
 * - Session-based last destination memory
 */
public class QueueSelectionDialog extends androidx.fragment.app.DialogFragment {
    private static final String TAG = "QueueSelectionDialog";
    private static final String ARG_SOURCE_QUEUE_ID = "sourceQueueId";
    private static final String ARG_OPERATION_TYPE = "operationType"; // "move" or "copy"

    private QueueSelectionViewModel viewModel;
    private QueueListAdapter adapter;
    private RecyclerView queueListView;
    private TextInputLayout searchInputLayout;
    private Button createQueueButton;
    private Button cancelButton;

    private Long sourceQueueId;
    private String operationType;
    private OnQueueSelectedListener listener;

    /**
     * Interface for queue selection callbacks.
     */
    public interface OnQueueSelectedListener {
        /**
         * Called when a queue is selected.
         *
         * @param queue Selected queue metadata
         */
        void onQueueSelected(QueueMetadata queue);
    }

    /**
     * Create a new QueueSelectionDialog instance.
     *
     * @param sourceQueueId ID of source queue (to filter out for move operations), or null
     * @param operationType "move" or "copy" - affects whether source queue is filtered
     * @return New dialog instance
     */
    public static QueueSelectionDialog newInstance(@Nullable Long sourceQueueId, String operationType) {
        QueueSelectionDialog dialog = new QueueSelectionDialog();
        Bundle args = new Bundle();
        if (sourceQueueId != null) {
            args.putLong(ARG_SOURCE_QUEUE_ID, sourceQueueId);
        }
        args.putString(ARG_OPERATION_TYPE, operationType);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(ARG_SOURCE_QUEUE_ID)) {
                sourceQueueId = args.getLong(ARG_SOURCE_QUEUE_ID);
            }
            operationType = args.getString(ARG_OPERATION_TYPE, "move");
        }
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View view = getLayoutInflater()
                .inflate(R.layout.dialog_queue_selection, null);

        setupViews(view);
        loadQueues();

        builder.setView(view)
                .setTitle(R.string.queues);

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();

        // Initialize ViewModel
        if (viewModel == null) {
            viewModel = new ViewModelProvider(requireActivity()).get(QueueSelectionViewModel.class);
        }

        // Observe last destination for highlighting
        viewModel.getLastDestination().observe(this, lastDestId -> {
            if (adapter != null && lastDestId != null) {
                adapter.setCurrentQueueId(lastDestId);
            }
        });

        EventBus.getDefault().register(this);
    }

    private void setupViews(View view) {
        queueListView = view.findViewById(R.id.queue_list);
        searchInputLayout = view.findViewById(R.id.search_input_layout);
        createQueueButton = view.findViewById(R.id.create_queue_button);
        cancelButton = view.findViewById(R.id.cancel_button);

        // Setup RecyclerView
        queueListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new QueueListAdapter(java.util.Collections.emptyList());
        queueListView.setAdapter(adapter);

        // Filter out source queue for move operations (will filter in loadQueues)

        // Setup queue selection callback
        adapter.setOnQueueSelectedListener(queueId -> {
            // Get queue metadata
            QueueMetadata queue = DBReader.getQueueMetadataById(queueId);
            if (queue == null) {
                return;
            }

            // Save last destination
            if (viewModel != null) {
                viewModel.setLastDestination(queueId);
            }

            // Notify listener
            if (listener != null) {
                listener.onQueueSelected(queue);
            }
            dismiss();
        });

        // Hide edit button for selection dialog
        adapter.setOnQueueEditRequestListener(null);

        // Search functionality not supported by QueueListAdapter - hide search field
        searchInputLayout.setVisibility(View.GONE);

        // Setup create queue button
        createQueueButton.setOnClickListener(v -> {
            QueueDialogManager.showCreateQueueDialog(this,
                    new QueueDialogManager.QueueNameColorCallback() {
                        @Override
                        public void onConfirm(String name, int color) {
                            // Create queue and auto-select it
                            try {
                                long newQueueId = DBWriter.createQueue(name, color).get();
                                QueueMetadata newQueue = DBReader.getQueueMetadataById(newQueueId);
                                if (newQueue != null && listener != null) {
                                    if (viewModel != null) {
                                        viewModel.setLastDestination(newQueueId);
                                    }
                                    listener.onQueueSelected(newQueue);
                                }
                                dismiss();
                            } catch (Exception e) {
                                Log.e(TAG, "Error creating queue: " + name, e);
                                // Error creating queue - dialog will handle it
                            }
                        }

                        @Override
                        public void onCancel() {
                            // User cancelled queue creation
                        }
                    });
        });

        // Setup cancel button
        cancelButton.setOnClickListener(v -> dismiss());
    }

    private void loadQueues() {
        List<QueueMetadata> queues = DBReader.getAllQueues();

        // Filter out source queue for move operations
        if ("move".equals(operationType) && sourceQueueId != null) {
            queues = new java.util.ArrayList<>(queues);
            java.util.Iterator<QueueMetadata> iterator = queues.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getId() == sourceQueueId) {
                    iterator.remove();
                }
            }
        }

        // Update adapter
        adapter.updateQueueList(queues);

        // Highlight last destination if available
        if (viewModel != null) {
            Long lastDest = viewModel.getLastDestinationValue();
            if (lastDest != null) {
                adapter.setCurrentQueueId(lastDest);
            }
        }
    }

    /**
     * Set the callback for queue selection.
     *
     * @param listener OnQueueSelectedListener callback
     */
    public void setOnQueueSelectedListener(OnQueueSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    /**
     * Subscribe to queue events to refresh list when queues change.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueEvent(QueueEvent event) {
        if (event.action == QueueEvent.Action.QUEUE_CREATED
                || event.action == QueueEvent.Action.QUEUE_DELETED
                || event.action == QueueEvent.Action.QUEUE_RENAMED) {
            loadQueues();
        }
    }
}
