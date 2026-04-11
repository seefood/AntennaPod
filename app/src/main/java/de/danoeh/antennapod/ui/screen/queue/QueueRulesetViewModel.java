package de.danoeh.antennapod.ui.screen.queue;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * ViewModel for QueueRulesetEditFragment.
 * Loads and exposes RefillRules for a given queue, tracks refill-in-progress state.
 */
public class QueueRulesetViewModel extends AndroidViewModel {

    static class RulesData {
        final List<RefillRule> rules;
        final Map<Long, String> feedTitles;

        RulesData(List<RefillRule> rules, Map<Long, String> feedTitles) {
            this.rules = rules;
            this.feedTitles = feedTitles;
        }
    }

    private final MutableLiveData<RulesData> rulesData =
            new MutableLiveData<>(new RulesData(Collections.emptyList(), Collections.emptyMap()));
    private final MutableLiveData<Boolean> isRefillInProgress = new MutableLiveData<>(false);
    private final CompositeDisposable disposables = new CompositeDisposable();
    private long queueId;
    private long rulesetId = -1;

    public QueueRulesetViewModel(@NonNull Application application) {
        super(application);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        EventBus.getDefault().unregister(this);
        disposables.clear();
    }

    /**
     * Sets the queue ID and triggers an initial rules load.
     */
    public void init(long queueId) {
        this.queueId = queueId;
        loadRules();
    }

    /** LiveData emitting rules and the feedId→title map together as a single update. */
    public LiveData<RulesData> getRulesData() {
        return rulesData;
    }

    /** LiveData emitting true while a refill operation is in-flight. */
    public LiveData<Boolean> getIsRefillInProgress() {
        return isRefillInProgress;
    }

    /**
     * Returns the ruleset ID, or -1 if no ruleset has been created yet.
     */
    public long getRulesetId() {
        return rulesetId;
    }

    /** Reloads rules and feed titles from database on an IO thread. */
    public void loadRules() {
        disposables.add(
                Observable.fromCallable(() -> {
                    de.danoeh.antennapod.model.feed.QueueRuleset ruleset =
                            DBReader.getQueueRuleset(queueId);
                    List<RefillRule> ruleList;
                    if (ruleset == null) {
                        ruleList = Collections.emptyList();
                    } else {
                        rulesetId = ruleset.getId();
                        ruleList = DBReader.getRefillRules(rulesetId);
                    }
                    List<Feed> feeds = DBReader.getFeedList();
                    Map<Long, String> titles = new HashMap<>(feeds.size());
                    for (Feed feed : feeds) {
                        titles.put(feed.getId(), feed.getTitle());
                    }
                    return new Object[]{ruleList, titles};
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> {
                            //noinspection unchecked
                            rulesData.setValue(new RulesData(
                                    (List<RefillRule>) result[0],
                                    (Map<Long, String>) result[1]));
                        }, throwable ->
                            rulesData.setValue(new RulesData(
                                    Collections.emptyList(), Collections.emptyMap()))
                        )
        );
    }

    /**
     * Ensures a ruleset exists for the queue, then invokes the callback with its ID.
     * Runs off main thread; callback is delivered on the DB executor thread.
     */
    public void ensureRulesetAndRun(Runnable callback) {
        disposables.add(
                Observable.fromCallable(() -> {
                    long rsId = DBWriter.createQueueRuleset(queueId).get();
                    rulesetId = rsId;
                    callback.run();
                    return rsId;
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(ignored -> loadRules(), throwable -> { })
        );
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueEvent(QueueEvent event) {
        if (event.action == QueueEvent.Action.REFILLED
                || event.action == QueueEvent.Action.OPERATION_FAILED) {
            if (event.getQueueId() == queueId) {
                isRefillInProgress.setValue(false);
            }
        }
    }

    /** Called by the fragment when a refill operation is submitted. */
    public void onRefillStarted() {
        isRefillInProgress.setValue(true);
    }
}
