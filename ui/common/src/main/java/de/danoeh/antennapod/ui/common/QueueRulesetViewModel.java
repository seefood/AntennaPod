package de.danoeh.antennapod.ui.common;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.QueueRuleset;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * ViewModel for managing queue ruleset state and operations.
 *
 * <p>Handles loading and managing queue rulesets and refill rules.
 * Exposes ruleset data via LiveData for reactive UI updates.
 * All database operations are asynchronous via DBWriter (single-threaded executor).
 */
public class QueueRulesetViewModel extends AndroidViewModel {
    private static final String TAG = "QueueRulesetViewModel";

    private final MutableLiveData<QueueRuleset> rulesetLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<RefillRule>> rulesLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessageLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRefilling = new MutableLiveData<>(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("QueueRulesetViewModel-DB");
        return t;
    });

    private long currentQueueId = -1;

    public QueueRulesetViewModel(@NonNull Application application) {
        super(application);
        EventBus.getDefault().register(this);
        // Load ruleset data on background thread to avoid I/O on main thread
        executor.submit(() -> {
            loadRulesetData();
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
     * Load ruleset data from database for the current queue.
     * Called once on ViewModel creation and after queue-related events.
     * Must be called on background thread to avoid I/O on main thread.
     */
    private void loadRulesetData() {
        long queueId = UserPreferences.getCurrentQueueId();
        currentQueueId = queueId;

        QueueRuleset ruleset = DBReader.getQueueRuleset(queueId);
        postToMainThread(() -> rulesetLiveData.setValue(ruleset));

        if (ruleset != null) {
            List<RefillRule> rules = DBReader.getRefillRules(ruleset.getId());
            postToMainThread(() -> rulesLiveData.setValue(rules));
        } else {
            postToMainThread(() -> rulesLiveData.setValue(java.util.Collections.emptyList()));
        }
    }

    /**
     * Get the current ruleset LiveData.
     *
     * @return LiveData for the current queue's ruleset
     */
    public LiveData<QueueRuleset> getRuleset() {
        return rulesetLiveData;
    }

    /**
     * Get the current ruleset synchronously (returns cached value).
     *
     * @return Current ruleset or null if not loaded
     */
    public QueueRuleset getRulesetValue() {
        return rulesetLiveData.getValue();
    }

    /**
     * Get the refill rules LiveData.
     *
     * @return LiveData for the list of refill rules
     */
    public LiveData<List<RefillRule>> getRules() {
        return rulesLiveData;
    }

    /**
     * Get the refill rules synchronously (returns cached value).
     *
     * @return List of refill rules or empty list if not loaded
     */
    public List<RefillRule> getRulesValue() {
        List<RefillRule> rules = rulesLiveData.getValue();
        return rules != null ? rules : java.util.Collections.emptyList();
    }

    /**
     * Get the current queue ID.
     *
     * @return Current queue ID
     */
    public long getCurrentQueueId() {
        return currentQueueId;
    }

    /**
     * Get error message LiveData.
     *
     * @return LiveData for error messages
     */
    public LiveData<String> getErrorMessage() {
        return errorMessageLiveData;
    }

    /**
     * Get refilling state LiveData.
     *
     * @return LiveData indicating if refill is in progress
     */
    public LiveData<Boolean> getIsRefilling() {
        return isRefilling;
    }

    /**
     * Set refilling state (used to block/unblock edits during refill).
     *
     * @param refilling true if refill is in progress, false otherwise
     */
    public void setIsRefilling(boolean refilling) {
        isRefilling.postValue(refilling);
    }

    /**
     * Refresh ruleset data from database.
     * Called when ruleset changes occur.
     */
    public void refreshRuleset() {
        executor.submit(() -> {
            loadRulesetData();
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueEvent(QueueEvent event) {
        Log.d(TAG, "onQueueEvent() called with: " + "event = [" + event + "]");
        // Refresh ruleset data when queue switches or ruleset changes
        if (event.action == QueueEvent.Action.QUEUE_SWITCHED) {
            refreshRuleset();
        }
        // Detect refill start/completion from QueueEvent actions (FR-036)
        if (event.action == QueueEvent.Action.REFILLED) {
            // Refill completed - re-enable edits
            setIsRefilling(false);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        EventBus.getDefault().unregister(this);
        executor.shutdown();
    }
}
