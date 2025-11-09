package de.danoeh.antennapod.ui.screen.queue;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.ui.common.QueueColorGradient;
import de.danoeh.antennapod.ui.common.QueueDialogManager;
import de.danoeh.antennapod.ui.common.QueueListAdapter;
import de.danoeh.antennapod.ui.common.QueueViewModel;

/**
 * Fragment for managing queues.
 *
 * <p>Displays a list of all queues with ability to:
 * - Switch to a queue (tap queue name)
 * - Create a new queue (tap + button)
 * - Edit queue properties (Phase 5)
 *
 * <p>This is a dedicated screen, not a dialog overlay.
 */
public class QueueManagementFragment extends Fragment {
    public static final String TAG = "QueueManagementFragment";

    private QueueViewModel queueViewModel;
    private QueueListAdapter queueListAdapter;
    private FloatingActionButton createQueueButton;
    private RecyclerView queueListView;
    private MaterialToolbar toolbar;

    public QueueManagementFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_queue_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel
        queueViewModel = new ViewModelProvider(requireActivity(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication()))
                .get(QueueViewModel.class);

        // Find views
        toolbar = view.findViewById(R.id.toolbar);
        queueListView = view.findViewById(R.id.queue_list);
        createQueueButton = view.findViewById(R.id.queue_create_button);

        // Setup toolbar with back navigation
        toolbar.setNavigationOnClickListener(v -> {
            getParentFragmentManager().popBackStack();
        });

        // Setup RecyclerView with empty list initially (will be populated by observer)
        queueListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        queueListAdapter = new QueueListAdapter(java.util.Collections.emptyList());
        queueListView.setAdapter(queueListAdapter);

        // Setup queue selection callback - switch queue and dismiss
        queueListAdapter.setOnQueueSelectedListener(queueId -> {
            // switchActiveQueue handles all queue switching logic including:
            // - Saving playback state for old queue
            // - Restoring playback state for new queue
            // - Posting QueueEvent.queueSwitched to notify other components
            queueViewModel.switchActiveQueue(queueId);
            // Return to previous screen after selection
            getParentFragmentManager().popBackStack();
        });

        // Setup queue edit callback
        queueListAdapter.setOnQueueEditRequestListener(queueId -> {
            if (queueId > 0) {
                // Get queue metadata and show edit dialog
                QueueMetadata currentQueue = queueViewModel.getCurrentQueue();
                QueueMetadata queue = currentQueue;
                if (queue == null || queue.getId() != queueId) {
                    // Load queue metadata if not current queue
                    queue = de.danoeh.antennapod.storage.database.DBReader
                            .getQueueMetadataById(queueId);
                }
                final QueueMetadata finalQueue = queue;
                if (finalQueue != null) {
                    QueueDialogManager.showRenameQueueDialog(
                            QueueManagementFragment.this,
                            queueId,
                            finalQueue.getName(),
                            finalQueue.getColor(),
                            new QueueDialogManager.QueueNameColorCallback() {
                                @Override
                                public void onConfirm(String newName, int newColor) {
                                    // Update name if changed
                                    if (!newName.equals(finalQueue.getName())) {
                                        queueViewModel.renameQueue(queueId, newName);
                                    }
                                    // Update color if changed
                                    if (newColor != finalQueue.getColor()) {
                                        queueViewModel.changeQueueColor(queueId, newColor);
                                    }
                                }

                                @Override
                                public void onCancel() {
                                    // Dialog cancelled - no action needed
                                }

                                @Override
                                public void onDelete() {
                                    // Delete the queue
                                    queueViewModel.deleteQueue(queueId);
                                }
                            });
                }
            }
        });

        // Setup create queue button
        createQueueButton.setOnClickListener(v -> {
            QueueDialogManager.showCreateQueueDialog(QueueManagementFragment.this,
                    new QueueDialogManager.QueueNameColorCallback() {
                        @Override
                        public void onConfirm(String name, int color) {
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

        // Phase 7: Queue Color Gradient - Apply gradient to title bar
        queueViewModel.getCurrentQueueColor().observe(getViewLifecycleOwner(), color -> {
            if (color != null && toolbar != null) {
                // Check for theme changes (but don't clear cache here to avoid infinite loop)
                queueViewModel.checkThemeChanged();
                GradientDrawable gradient = queueViewModel.getGradientForColor(color);
                QueueColorGradient.applyGradientToToolbar(toolbar, gradient, color);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        recomputeToolbarColors();
    }

    private void recomputeToolbarColors() {
        if (toolbar == null) {
            return;
        }
        QueueViewModel queueViewModel = new ViewModelProvider(requireActivity(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication()))
                .get(QueueViewModel.class);
        Integer currentColor = queueViewModel.getCurrentQueueColor().getValue();
        if (currentColor != null) {
            queueViewModel.clearGradientCache();
            GradientDrawable gradient = queueViewModel.getGradientForColor(currentColor);
            QueueColorGradient.applyGradientToToolbar(toolbar, gradient, currentColor);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Recompute text color when theme changes (e.g., dark to light mode)
        QueueViewModel queueViewModel = new ViewModelProvider(requireActivity(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication()))
                .get(QueueViewModel.class);
        Integer currentColor = queueViewModel.getCurrentQueueColor().getValue();
        if (currentColor != null && toolbar != null) {
            // Clear cache without re-emitting (to avoid infinite loop)
            queueViewModel.clearGradientCache(false);
            GradientDrawable gradient = queueViewModel.getGradientForColor(currentColor);
            QueueColorGradient.applyGradientToToolbar(toolbar, gradient, currentColor);
        }
    }
}
