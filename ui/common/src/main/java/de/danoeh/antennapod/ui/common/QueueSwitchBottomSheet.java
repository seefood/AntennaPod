package de.danoeh.antennapod.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.QueueMetadata;

/**
 * Bottom sheet dialog for queue switching and management.
 *
 * <p>Displays list of all available queues with ability to:
 * - Switch to a queue (tap queue name)
 * - Edit queue properties (tap edit button)
 * - Create new queue (tap + button)
 *
 * <p>Integrates with QueueViewModel to manage state and persist queue operations.
 */
public class QueueSwitchBottomSheet extends BottomSheetDialogFragment {
    private static final String TAG = "QueueSwitchBottomSheet";

    private QueueViewModel queueViewModel;
    private QueueListAdapter queueListAdapter;
    private FloatingActionButton createQueueButton;
    private RecyclerView queueListView;

    public QueueSwitchBottomSheet() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.queue_switch_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel
        queueViewModel = new ViewModelProvider(requireActivity()).get(QueueViewModel.class);

        // Find views
        queueListView = view.findViewById(R.id.queue_list);
        createQueueButton = view.findViewById(R.id.queue_create_button);

        // Setup RecyclerView with empty list initially (will be populated by observer)
        queueListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        queueListAdapter = new QueueListAdapter(java.util.Collections.emptyList());
        queueListView.setAdapter(queueListAdapter);

        // Setup queue selection callback
        queueListAdapter.setOnQueueSelectedListener(queueId -> {
            queueViewModel.switchActiveQueue(queueId);
            dismiss();
        });

        // Setup queue edit callback - show rename/delete menu
        queueListAdapter.setOnQueueEditRequestListener(queueId -> {
            handleQueueEdit(queueId);
        });

        // Setup create queue button
        createQueueButton.setOnClickListener(v -> {
            // Show queue creation dialog
            QueueDialogManager.showCreateQueueDialog(QueueSwitchBottomSheet.this,
                    new QueueDialogManager.QueueNameColorCallback() {
                        @Override
                        public void onConfirm(String name, int color) {
                            // Create the queue via ViewModel
                            queueViewModel.createQueue(name, color);
                        }

                        @Override
                        public void onCancel() {
                            // Dialog was cancelled
                        }
                    });
        });

        // Observe queue list changes - adapter will be updated when data loads
        queueViewModel.getQueueListLiveData().observe(getViewLifecycleOwner(), queueList -> {
            if (queueList != null) {
                queueListAdapter.updateQueueList(queueList);
            }
        });

        // Observe current queue changes to highlight active queue
        queueViewModel.getCurrentQueueIdLiveData().observe(getViewLifecycleOwner(), currentQueueId -> {
            if (currentQueueId != null) {
                queueListAdapter.setCurrentQueueId(currentQueueId);
            }
        });
    }

    /**
     * Handle queue edit requests by showing rename/delete dialogs.
     *
     * @param queueId ID of queue to edit
     */
    private void handleQueueEdit(long queueId) {
        // Get queue from ViewModel to get current name and color
        List<QueueMetadata> queueList = queueViewModel.getQueueListLiveData().getValue();
        if (queueList == null) {
            return;
        }

        QueueMetadata queue = null;
        for (QueueMetadata q : queueList) {
            if (q.getId() == queueId) {
                queue = q;
                break;
            }
        }

        if (queue == null) {
            return;
        }

        final QueueMetadata currentQueue = queue;

        // Show edit dialog with integrated delete button
        QueueDialogManager.showRenameQueueDialog(QueueSwitchBottomSheet.this,
                queueId,
                currentQueue.getName(),
                currentQueue.getColor(),
                new QueueDialogManager.QueueNameColorCallback() {
                    @Override
                    public void onConfirm(String name, int color) {
                        // Update queue with new name and color
                        if (!name.equals(currentQueue.getName())) {
                            queueViewModel.renameQueue(queueId, name);
                        }
                        if (color != currentQueue.getColor()) {
                            queueViewModel.changeQueueColor(queueId, color);
                        }
                    }

                    @Override
                    public void onCancel() {
                        // Dialog cancelled - no action needed
                    }

                    @Override
                    public void onDelete() {
                        // Delete the queue via integrated delete button
                        queueViewModel.deleteQueue(queueId);
                        dismiss();
                    }
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        // Register for queue events to detect external queue changes
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        // Unregister to prevent memory leaks when fragment is not visible
        EventBus.getDefault().unregister(this);
    }

    /**
     * Subscribe to queue events for updates.
     * Called when any queue operation occurs (create, delete, etc.).
     * This ensures the UI reflects changes made from other parts of the app.
     *
     * @param event QueueEvent posted by database layer
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueEvent(QueueEvent event) {
        // Queue operation occurred - ViewModel will handle the data refresh
        // and observers will update the UI automatically via LiveData
        // This subscription ensures we're ready to receive events during onStart/onStop
    }
}
