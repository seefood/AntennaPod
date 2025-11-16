package de.danoeh.antennapod.ui.common;

import android.app.Application;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * ViewModel for managing queue state and operations.
 *
 * <p>Handles queue switching, creation, renaming, color changes, and deletion.
 * Exposes queue data via LiveData for reactive UI updates.
 * All database operations are asynchronous via DBWriter (single-threaded executor).
 */
public class QueueViewModel extends AndroidViewModel {
    private static final String TAG = "QueueViewModel";

    private final MutableLiveData<List<QueueMetadata>> queueListLiveData = new MutableLiveData<>();
    private final MutableLiveData<Long> currentQueueIdLiveData = new MutableLiveData<>();
    private final MutableLiveData<QueueMetadata> currentQueueLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessageLiveData = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("QueueViewModel-DB");
        return t;
    });

    // Phase 7: Queue Color Gradient - Gradient caching for performance
    private final Map<Integer, GradientDrawable> gradientCache = new HashMap<>();
    private final MutableLiveData<Integer> currentQueueColor = new MutableLiveData<>();
    private int lastUiMode = -1; // Track current UI mode for theme change detection

    public QueueViewModel(@NonNull Application application) {
        super(application);
        EventBus.getDefault().register(this);
        lastUiMode = getApplication().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        // Load queue data on background thread to avoid I/O on main thread
        executor.submit(() -> {
            loadQueueData();
        });
    }

    /**
     * Post a runnable to the main thread.
     * Used to ensure UI updates happen on main thread after database operations.
     *
     * @param runnable Code to run on main thread
     */
    private void postToMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    /**
     * Load initial queue data from database.
     * Called once on ViewModel creation and after queue-related events.
     * Also emits the current queue color for gradient rendering.
     * Must be called on background thread to avoid I/O on main thread.
     */
    private void loadQueueData() {
        long currentQueueId = UserPreferences.getCurrentQueueId();
        postToMainThread(() -> currentQueueIdLiveData.setValue(currentQueueId));

        List<QueueMetadata> allQueues = DBReader.getAllQueues();
        postToMainThread(() -> queueListLiveData.setValue(allQueues));

        QueueMetadata currentQueue = DBReader.getQueueMetadataById(currentQueueId);
        postToMainThread(() -> {
            currentQueueLiveData.setValue(currentQueue);
            // Phase 7: Emit queue color for gradient rendering
            if (currentQueue != null) {
                currentQueueColor.setValue(currentQueue.getColor());
            }
        });
    }

    /**
     * @return LiveData with the list of all queues, ordered by creation date (oldest first)
     */
    public LiveData<List<QueueMetadata>> getQueueListLiveData() {
        return queueListLiveData;
    }

    /**
     * @return LiveData with the ID of the currently active queue
     */
    public LiveData<Long> getCurrentQueueIdLiveData() {
        return currentQueueIdLiveData;
    }

    /**
     * @return LiveData with the QueueMetadata of the currently active queue
     */
    public LiveData<QueueMetadata> getCurrentQueueLiveData() {
        return currentQueueLiveData;
    }

    /**
     * @return LiveData for error messages
     */
    public LiveData<String> getErrorMessageLiveData() {
        return errorMessageLiveData;
    }

    /**
     * Get the ID of the currently active queue.
     *
     * @return Current queue ID from UserPreferences
     */
    public long getCurrentQueueId() {
        Long queueId = currentQueueIdLiveData.getValue();
        return queueId != null ? queueId : UserPreferences.getCurrentQueueId();
    }

    /**
     * Get the currently active queue metadata.
     *
     * @return Current QueueMetadata or null if not found
     */
    public QueueMetadata getCurrentQueue() {
        return currentQueueLiveData.getValue();
    }

    /**
     * Get LiveData for the current queue color.
     * Emits the ARGB color value of the currently active queue.
     * Used by fragments to observe and apply color gradients.
     *
     * <p>Example usage in fragments:
     * <pre>{@code
     * viewModel.getCurrentQueueColor().observe(getViewLifecycleOwner(), color -> {
     *     if (color != null && toolbar != null) {
     *         GradientDrawable gradient = viewModel.getGradientForColor(color);
     *         toolbar.setBackground(gradient);
     *         int textColor = QueueColorGradient.computeTextColor(color);
     *         toolbar.setTitleTextColor(textColor);
     *         toolbar.setNavigationIconTint(textColor);
     *         if (toolbar.getMenu() != null) {
     *             for (int i = 0; i < toolbar.getMenu().size(); i++) {
     *                 android.view.MenuItem item = toolbar.getMenu().getItem(i);
     *                 if (item.getIcon() != null) {
     *                     item.getIcon().setTint(textColor);
     *                 }
     *             }
     *         }
     *     }
     * });
     * }</pre>
     *
     * @return LiveData emitting ARGB color when queue switches or color changes
     * @since Phase 7: Queue Color Gradient
     */
    public LiveData<Integer> getCurrentQueueColor() {
        return currentQueueColor;
    }

    /**
     * Get a cached gradient drawable for the given queue color.
     * Creates and caches the gradient on first access, returns cached version on subsequent calls.
     *
     * <p>The gradient fades from the queue color (with scrim applied for light colors)
     * to transparent, using TOP_BOTTOM orientation.
     *
     * <p>Cache is automatically cleared on theme changes to ensure gradients match the current theme.
     *
     * @param color ARGB queue color value
     * @return Cached or newly created GradientDrawable for this color
     * @since Phase 7: Queue Color Gradient
     */
    public GradientDrawable getGradientForColor(@ColorInt int color) {
        // Check cache first for performance
        if (gradientCache.containsKey(color)) {
            return gradientCache.get(color);
        }

        // Cache miss: create gradient and store in cache
        GradientDrawable gradient = QueueColorGradient.createGradientDrawable(
                color, android.graphics.Color.TRANSPARENT);
        gradientCache.put(color, gradient);
        return gradient;
    }

    /**
     * Clear the gradient drawable cache.
     * Should be called on theme changes to ensure gradients are regenerated with correct theming.
     *
     * <p>After clearing the cache, the current queue color is re-emitted to trigger
     * fragments to request new gradients.
     *
     * @param reEmitColor If true, re-emits the current color to trigger observers. Set to false
     *                    when called from within an observer to avoid infinite loops.
     * @since Phase 7: Queue Color Gradient
     */
    public void clearGradientCache(boolean reEmitColor) {
        gradientCache.clear();
        Log.d(TAG, "Gradient cache cleared (theme change or manual clear)");

        // Re-emit current queue color to trigger gradient redraw (only if not in observer)
        if (reEmitColor) {
            Integer currentColor = currentQueueColor.getValue();
            if (currentColor != null) {
                currentQueueColor.setValue(currentColor);
            }
        }
    }

    /**
     * Clear the gradient drawable cache and re-emit color.
     * Convenience method that calls clearGradientCache(true).
     *
     * @since Phase 7: Queue Color Gradient
     */
    public void clearGradientCache() {
        clearGradientCache(true);
    }

    /**
     * Handle configuration changes, particularly theme changes.
     * Clears gradient cache if UI mode (dark/light theme) has changed.
     *
     * <p>Fragments should call this method in their color observation callback
     * to ensure gradients are regenerated when theme changes.
     *
     * @return true if theme changed, false otherwise
     * @since Phase 7: Queue Color Gradient
     */
    public boolean checkThemeChanged() {
        int currentUiMode = getApplication().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;

        if (lastUiMode != -1 && lastUiMode != currentUiMode) {
            Log.d(TAG, "Theme changed detected (UI mode: " + lastUiMode + " -> " + currentUiMode
                    + "), clearing gradient cache");
            // Re-emit color when theme changes (not in observer, so safe)
            clearGradientCache(true);
            lastUiMode = currentUiMode;
            return true;
        }

        lastUiMode = currentUiMode;
        return false;
    }

    /**
     * Switch to a different queue.
     * Saves the current playback state of the old queue, then switches to the new queue.
     * Updates UserPreferences and broadcasts QueueEvent.QUEUE_SWITCHED.
     *
     * <p><b>CRITICAL: All operations are serialized to avoid race conditions.</b>
     * The entire queue switch (save old state → fetch new state → update preferences →
     * restore playback state → post event) happens atomically on a background thread.
     * This prevents the old episode from being added to the new queue.
     *
     * @param queueId ID of queue to switch to
     */
    public void switchActiveQueue(long queueId) {
        long oldQueueId = getCurrentQueueId();

        if (oldQueueId == queueId) {
            Log.d(TAG, "Already on queue " + queueId + ", skipping switch");
            return;
        }

        // CRITICAL: Execute ENTIRE queue switch operation on background thread
        // to avoid race conditions between UserPreferences and PlaybackPreferences.
        // All operations MUST complete serially before the event is posted.
        executor.submit(() -> {
            try {
                // Step 1: Save current playback state for OLD queue (SYNCHRONOUS)
                long currentFeedMediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
                Log.d(TAG, "Step 1: Saving playback state for queue " + oldQueueId
                        + ": feedMediaId=" + currentFeedMediaId);
                DBWriter.updateQueuePlaybackState(oldQueueId, currentFeedMediaId).get();

                // Step 2: Fetch NEW queue metadata and playback state (SYNCHRONOUS)
                QueueMetadata newQueue = DBReader.getQueueMetadataById(queueId);
                final FeedMedia newMedia;
                if (newQueue != null && newQueue.getCurrentlyPlayingFeedMediaId() >= 0) {
                    long savedFeedMediaId = newQueue.getCurrentlyPlayingFeedMediaId();
                    Log.d(TAG, "Step 2: Loading playback state for queue " + queueId
                            + ": feedMediaId=" + savedFeedMediaId);
                    newMedia = DBReader.getFeedMedia(savedFeedMediaId);
                } else {
                    Log.d(TAG, "Step 2: No playback state for queue " + queueId);
                    newMedia = null;
                }

                // Step 3: ATOMIC UPDATE - All state changes happen together on main thread
                // CRITICAL: UserPreferences AND PlaybackPreferences MUST be updated BEFORE
                // posting QueueEvent. This prevents the race condition where code sees the
                // new queue ID but the old episode is still "currently playing".
                postToMainThread(() -> {
                    // 3a: Update active queue ID
                    UserPreferences.setCurrentQueueId(queueId);
                    currentQueueIdLiveData.setValue(queueId);
                    Log.d(TAG, "Step 3a: Updated active queue ID to " + queueId);

                    // 3b: Restore playback state for NEW queue
                    PlaybackPreferences.writeMediaPlaying(newMedia);
                    if (newMedia != null) {
                        Log.d(TAG, "Step 3b: Restored PlaybackPreferences to queue " + queueId
                                + " episode: " + newMedia.getEpisodeTitle());
                    } else {
                        Log.d(TAG, "Step 3b: Cleared PlaybackPreferences for queue " + queueId
                                + " (no playback history)");
                    }

                    // 3c: Update current queue metadata
                    currentQueueLiveData.setValue(newQueue);

                    // Step 4: NOW post event (after ALL state is consistent)
                    Log.d(TAG, "Step 4: Posting QUEUE_SWITCHED event for queue " + queueId);
                    EventBus.getDefault().post(QueueEvent.queueSwitched(queueId));
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to switch to queue " + queueId, e);
                postToMainThread(() -> {
                    errorMessageLiveData.setValue("Failed to switch queue. Please try again.");
                });
            }
        });
    }

    /**
     * Create a new queue with the given name and color.
     * Validates that name is not empty.
     * Auto-switches to the newly created queue.
     * Broadcasts QueueEvent.QUEUE_CREATED.
     *
     * @param name Queue name (cannot be empty)
     * @param color RGB color value
     */
    public void createQueue(@NonNull String name, @ColorInt int color) {
        if (name == null || name.trim().isEmpty()) {
            errorMessageLiveData.setValue("Queue name cannot be empty");
            return;
        }

        executor.submit(() -> {
            try {
                Log.d(TAG, "Creating queue: " + name);
                Long queueId = DBWriter.createQueue(name, color).get();
                postToMainThread(() -> {
                    Log.d(TAG, "Queue created with ID: " + queueId);
                    // Auto-switch to newly created queue
                    if (queueId != null) {
                        switchActiveQueue(queueId);
                    }
                    // Reload queue data on background thread
                    executor.submit(() -> loadQueueData());
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to create queue", e);
                postToMainThread(() -> {
                    errorMessageLiveData.setValue("Unable to create queue. Please try again.");
                });
            }
        });
    }

    /**
     * Rename an existing queue.
     * Validates that new name is not empty.
     * Updates currentQueue if the queue being renamed is currently active.
     * Broadcasts QueueEvent.QUEUE_RENAMED.
     *
     * @param queueId ID of queue to rename
     * @param newName New queue name (cannot be empty)
     */
    public void renameQueue(long queueId, @NonNull String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            errorMessageLiveData.setValue("Queue name cannot be empty");
            return;
        }

        executor.submit(() -> {
            try {
                Log.d(TAG, "Renaming queue " + queueId + " to: " + newName);
                DBWriter.renameQueue(queueId, newName).get();
                postToMainThread(() -> {
                    Log.d(TAG, "Queue renamed successfully");
                    // Update currentQueue if it was the one being renamed (on background thread)
                    if (queueId == getCurrentQueueId()) {
                        executor.submit(() -> {
                            QueueMetadata updated = DBReader.getQueueMetadataById(queueId);
                            postToMainThread(() -> currentQueueLiveData.setValue(updated));
                        });
                    }
                    // Reload queue data on background thread
                    executor.submit(() -> loadQueueData());
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to rename queue", e);
                postToMainThread(() -> {
                    errorMessageLiveData.setValue("Unable to rename queue. Please try again.");
                });
            }
        });
    }

    /**
     * Change the color of an existing queue.
     * Updates currentQueue if the queue being changed is currently active.
     * Broadcasts QueueEvent.QUEUE_COLOR_CHANGED.
     *
     * @param queueId ID of queue to change
     * @param color New RGB color value
     */
    public void changeQueueColor(long queueId, @ColorInt int color) {
        executor.submit(() -> {
            try {
                Log.d(TAG, "Changing color for queue " + queueId);
                DBWriter.changeQueueColor(queueId, color).get();
                postToMainThread(() -> {
                    Log.d(TAG, "Queue color changed successfully");
                    // Update currentQueue if it was the one being changed (on background thread)
                    if (queueId == getCurrentQueueId()) {
                        executor.submit(() -> {
                            QueueMetadata updated = DBReader.getQueueMetadataById(queueId);
                            postToMainThread(() -> currentQueueLiveData.setValue(updated));
                        });
                    }
                    // Reload queue data on background thread
                    executor.submit(() -> loadQueueData());
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to change queue color", e);
                postToMainThread(() -> {
                    errorMessageLiveData.setValue("Unable to change queue color. Please try again.");
                });
            }
        });
    }

    /**
     * Delete a queue.
     * Only allows deletion if there is more than one queue.
     * If deleting the currently active queue, auto-switches to another queue.
     * Broadcasts QueueEvent.QUEUE_DELETED.
     *
     * @param queueId ID of queue to delete
     */
    public void deleteQueue(long queueId) {
        List<QueueMetadata> allQueues = queueListLiveData.getValue();
        if (allQueues == null || allQueues.size() <= 1) {
            errorMessageLiveData.setValue("Cannot delete the last queue");
            return;
        }

        boolean isCurrentQueue = (queueId == getCurrentQueueId());

        executor.submit(() -> {
            try {
                Log.d(TAG, "Deleting queue " + queueId);
                DBWriter.deleteQueue(queueId).get();
                postToMainThread(() -> {
                    Log.d(TAG, "Queue deleted successfully");
                    // If we deleted the current queue, switch to another (on background thread)
                    if (isCurrentQueue) {
                        executor.submit(() -> {
                            List<QueueMetadata> remaining = DBReader.getAllQueues();
                            postToMainThread(() -> {
                                if (remaining != null && !remaining.isEmpty()) {
                                    switchActiveQueue(remaining.get(0).getId());
                                }
                            });
                        });
                    }
                    // Reload queue data on background thread
                    executor.submit(() -> loadQueueData());
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete queue", e);
                postToMainThread(() -> {
                    errorMessageLiveData.setValue("Unable to delete queue. Please try again.");
                });
            }
        });
    }

    /**
     * Subscribe to QueueEvent updates.
     * Called when any queue-related event occurs (create, rename, delete, etc.).
     * Reloads queue data from database to keep UI in sync.
     *
     * @param event QueueEvent posted by DBWriter or other components
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueEvent(QueueEvent event) {
        // Reload queue data on any queue-related event (on background thread)
        if (event.action == QueueEvent.Action.QUEUE_CREATED
                || event.action == QueueEvent.Action.QUEUE_RENAMED
                || event.action == QueueEvent.Action.QUEUE_COLOR_CHANGED
                || event.action == QueueEvent.Action.QUEUE_DELETED
                || event.action == QueueEvent.Action.QUEUE_SWITCHED) {
            executor.submit(() -> loadQueueData());
        } else if (event.action == QueueEvent.Action.CURRENTLY_PLAYING_UPDATED) {
            // Update the metadata for the queue whose playback state changed (on background thread)
            long currentQueueId = getCurrentQueueId();
            if (event.queueId == currentQueueId) {
                Log.d(TAG, "Updating currently playing for queue " + event.queueId);
                executor.submit(() -> {
                    QueueMetadata updated = DBReader.getQueueMetadataById(currentQueueId);
                    postToMainThread(() -> currentQueueLiveData.setValue(updated));
                });
            }
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        EventBus.getDefault().unregister(this);
        executor.shutdown();
    }
}
