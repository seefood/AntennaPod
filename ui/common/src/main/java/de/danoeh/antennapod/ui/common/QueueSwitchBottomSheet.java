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

import de.danoeh.antennapod.R;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.event.QueueEvent;

/**
 * Bottom sheet dialog for queue switching and management.
 *
 * Displays list of all available queues with ability to:
 * - Switch to a queue (tap queue name)
 * - Edit queue properties (tap edit button)
 * - Create new queue (tap + button)
 *
 * Integrates with QueueViewModel to manage state and persist queue operations.
 */
public class QueueSwitchBottomSheet extends BottomSheetDialogFragment {
    private static final String TAG = "QueueSwitchBottomSheet";

    private QueueViewModel queueViewModel;
    private QueueListAdapter queueListAdapter;
    private FloatingActionButton createQueueButton;
    private RecyclerView queueListView;

    /**
     * Interface for queue edit request callbacks.
     */
    public interface OnQueueEditListener {
        /**
         * Called when user requests to edit a queue.
         *
         * @param queueId ID of the queue to edit
         */
        void onEditQueueRequested(long queueId);
    }

    private OnQueueEditListener editListener;

    public QueueSwitchBottomSheet() {
        // Required empty public constructor
    }

    /**
     * Set callback for queue edit requests.
     *
     * @param listener OnQueueEditListener callback
     */
    public void setOnQueueEditListener(OnQueueEditListener listener) {
        this.editListener = listener;
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

        // Setup RecyclerView
        queueListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        queueListAdapter = new QueueListAdapter(queueViewModel.getQueueListLiveData().getValue() != null
                ? queueViewModel.getQueueListLiveData().getValue()
                : java.util.Collections.emptyList());
        queueListView.setAdapter(queueListAdapter);

        // Setup queue selection callback
        queueListAdapter.setOnQueueSelectedListener(queueId -> {
            queueViewModel.switchActiveQueue(queueId);
            dismiss();
        });

        // Setup queue edit callback
        queueListAdapter.setOnQueueEditRequestListener(queueId -> {
            if (editListener != null) {
                editListener.onEditQueueRequested(queueId);
            }
        });

        // Setup create queue button
        createQueueButton.setOnClickListener(v -> {
            if (editListener != null) {
                // Pass special value -1 to indicate create mode (not editing existing queue)
                editListener.onEditQueueRequested(-1);
            }
        });

        // Observe queue list changes
        queueViewModel.getQueueListLiveData().observe(getViewLifecycleOwner(), queueList -> {
            queueListAdapter.updateQueueList(queueList);
        });

        // Observe current queue changes
        queueViewModel.getCurrentQueueIdLiveData().observe(getViewLifecycleOwner(), currentQueueId -> {
            queueListAdapter.setCurrentQueueId(currentQueueId);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    /**
     * Subscribe to queue events for updates.
     * Called when any queue operation occurs (create, delete, etc.).
     *
     * @param event QueueEvent posted by database layer
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueEvent(QueueEvent event) {
        // Any queue operation triggers a refresh via ViewModel
        // ViewModel handles data loading and LiveData updates
    }
}
