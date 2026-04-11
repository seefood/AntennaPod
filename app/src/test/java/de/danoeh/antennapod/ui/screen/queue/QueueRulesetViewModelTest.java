package de.danoeh.antennapod.ui.screen.queue;

import android.content.Context;
import android.os.Looper;

import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for QueueRulesetViewModel.
 */
@RunWith(RobolectricTestRunner.class)
public class QueueRulesetViewModelTest {
    private static final long QUEUE_ID = 1L;
    private Context context;
    private QueueRulesetViewModel viewModel;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PodDBAdapter.init(context);
        DBWriter.tearDownTests();
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        viewModel = new QueueRulesetViewModel(RuntimeEnvironment.getApplication());
    }

    @After
    public void tearDown() throws Exception {
        viewModel.onCleared();
        DBWriter.tearDownTests();
    }

    @Test
    public void testRulesLiveDataEmitsAfterCreateRefillRule() throws Exception {
        viewModel.init(QUEUE_ID);

        // Ensure ruleset exists first
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<RefillRule>> emittedRules = new AtomicReference<>();

        // Observe on main thread (Robolectric)
        viewModel.getRules().observeForever(rules -> {
            if (rules != null && !rules.isEmpty()) {
                emittedRules.set(rules);
                latch.countDown();
            }
        });

        // Create a rule, then trigger reload
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                3, RefillRule.SourceType.INBOX, null).get();
        viewModel.loadRules();

        // Pump the main looper, let the IO thread complete, then pump again.
        // Under Robolectric, await() acts as a sleep; LiveData is delivered on the second pump.
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        latch.await(5, TimeUnit.SECONDS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        List<RefillRule> rules = viewModel.getRules().getValue();
        assertNotNull("rules LiveData must emit a non-null value", rules);
        assertEquals("One rule should be in the list", 1, rules.size());
        assertEquals("Rule selection method must match", RefillRule.SelectionMethod.OLDEST,
                rules.get(0).getSelectionMethod());
    }

    @Test
    public void testIsRefillInProgressTogglesOnNotify() {
        viewModel.init(QUEUE_ID);

        assertFalse("Initially not in progress",
                Boolean.TRUE.equals(viewModel.getIsRefillInProgress().getValue()));

        viewModel.onRefillStarted();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertTrue("Should be true after onRefillStarted()",
                Boolean.TRUE.equals(viewModel.getIsRefillInProgress().getValue()));

        // Post a REFILLED event for our queueId
        EventBus.getDefault().post(QueueEvent.refilled(QUEUE_ID, 2));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertFalse("Should be false after REFILLED event",
                Boolean.TRUE.equals(viewModel.getIsRefillInProgress().getValue()));
    }

    @Test
    public void testOperationFailedEventAlsoClearsRefillInProgress() {
        viewModel.init(QUEUE_ID);

        viewModel.onRefillStarted();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertTrue(Boolean.TRUE.equals(viewModel.getIsRefillInProgress().getValue()));

        EventBus.getDefault().post(QueueEvent.operationFailed(QUEUE_ID, "test error"));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertFalse("OPERATION_FAILED must also clear isRefillInProgress",
                Boolean.TRUE.equals(viewModel.getIsRefillInProgress().getValue()));
    }

    @Test
    public void testLoadRulesDeliversResultOnMainThread() throws Exception {
        viewModel.init(QUEUE_ID);
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.NEWEST,
                5, RefillRule.SourceType.INBOX, null).get();

        AtomicBoolean observedOnMain = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        // Filter out the initial empty emission; wait for the DB-loaded non-empty result.
        viewModel.getRules().observeForever(rules -> {
            if (rules != null && !rules.isEmpty()) {
                observedOnMain.set(Looper.myLooper() == Looper.getMainLooper());
                latch.countDown();
            }
        });

        viewModel.loadRules();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        latch.await(5, TimeUnit.SECONDS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertTrue("LiveData observer must be invoked on the main thread", observedOnMain.get());
    }
}
