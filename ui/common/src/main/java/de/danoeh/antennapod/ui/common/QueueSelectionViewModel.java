package de.danoeh.antennapod.ui.common;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.LiveData;

/**
 * ViewModel for queue selection dialog.
 * Manages session-based last destination memory.
 *
 * <p>Last destination is cleared when ViewModel is destroyed (app restart).
 */
public class QueueSelectionViewModel extends ViewModel {
    private final MutableLiveData<Long> lastDestinationQueueId = new MutableLiveData<>(null);

    /**
     * Set the last selected destination queue ID.
     * Called when user selects a queue for move/copy operation.
     *
     * @param queueId ID of the selected queue
     */
    public void setLastDestination(long queueId) {
        lastDestinationQueueId.setValue(queueId);
    }

    /**
     * Get the last selected destination queue ID.
     * Returns null if no destination has been selected in this session.
     *
     * @return LiveData with last destination queue ID, or null
     */
    public LiveData<Long> getLastDestination() {
        return lastDestinationQueueId;
    }

    /**
     * Get the current value of last destination (non-LiveData).
     * Useful for synchronous checks.
     *
     * @return Last destination queue ID, or null
     */
    public Long getLastDestinationValue() {
        return lastDestinationQueueId.getValue();
    }
}
