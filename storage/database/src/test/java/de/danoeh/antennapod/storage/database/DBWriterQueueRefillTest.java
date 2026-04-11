package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.RefillResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for DBWriter.refillQueue().
 */
@RunWith(RobolectricTestRunner.class)
public class DBWriterQueueRefillTest {
    private static final long QUEUE_ID = 1L;
    private Context context;

    // Captures REFILLED / OPERATION_FAILED events
    private final AtomicReference<QueueEvent> lastEvent = new AtomicReference<>();
    private final CountDownLatch eventLatch = new CountDownLatch(1);

    @Subscribe
    public void onQueueEvent(QueueEvent event) {
        if (event.action == QueueEvent.Action.REFILLED
                || event.action == QueueEvent.Action.OPERATION_FAILED) {
            lastEvent.set(event);
            eventLatch.countDown();
        }
    }

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
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() throws Exception {
        EventBus.getDefault().unregister(this);
        DBWriter.tearDownTests();
    }

    @Test
    public void testAppendPreservesExistingEpisodesAndAddsNew() throws Exception {
        Feed feed = createAndStoreFeed("http://append.test");
        addItems(feed, 5, FeedItem.UNPLAYED);
        Feed reloaded = DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);

        // Pre-fill queue with first 2 items
        FeedItem pre1 = reloaded.getItems().get(0);
        FeedItem pre2 = reloaded.getItems().get(1);
        setQueue(pre1, pre2);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                3, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        RefillResult result = DBWriter.refillQueue(QUEUE_ID, false).get();

        assertTrue("Refill must succeed", result.isSuccess());
        assertEquals("3 episodes added (remaining unqueued)", 3, result.getEpisodesAdded());
        assertFalse("Queue was not empty before refill", result.wasQueueEmpty());

        List<FeedItem> queue = DBReader.getQueue();
        assertEquals("Queue must have pre-filled + appended = 5", 5, queue.size());
        assertEquals("Pre-filled item 1 must be first", pre1.getId(), queue.get(0).getId());
        assertEquals("Pre-filled item 2 must be second", pre2.getId(), queue.get(1).getId());
    }

    @Test
    public void testClearAndFillReplacesQueue() throws Exception {
        Feed feed = createAndStoreFeed("http://clearfill.test");
        addItems(feed, 5, FeedItem.UNPLAYED);
        Feed reloaded = DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
        setQueue(reloaded.getItems().get(0), reloaded.getItems().get(1));

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.NEWEST,
                3, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        RefillResult result = DBWriter.refillQueue(QUEUE_ID, true).get();

        assertTrue("Refill must succeed", result.isSuccess());
        assertEquals("3 episodes should be in queue", 3, result.getEpisodesAdded());

        List<FeedItem> queue = DBReader.getQueue();
        assertEquals("Queue must contain exactly 3 new items", 3, queue.size());
    }

    @Test
    public void testPartialFulfillment() throws Exception {
        Feed feed = createAndStoreFeed("http://partial.test");
        addItems(feed, 3, FeedItem.UNPLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                10, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        RefillResult result = DBWriter.refillQueue(QUEUE_ID, false).get();

        assertTrue(result.isSuccess());
        assertEquals("Only 3 available, rule requests 10 → 3 added", 3, result.getEpisodesAdded());
        assertEquals("0 rules skipped", 0, result.getRulesSkipped());
    }

    @Test
    public void testDeletedFeedRuleSkipped() throws Exception {
        Feed validFeed = createAndStoreFeed("http://valid.test");
        addItems(validFeed, 3, FeedItem.UNPLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        // Rule for non-existent feed (will be skipped, rulesSkipped++)
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                2, RefillRule.SourceType.FEED, "99999999").get();
        // Rule for valid feed
        DBWriter.createRefillRule(rulesetId, 1, RefillRule.SelectionMethod.OLDEST,
                2, RefillRule.SourceType.FEED, String.valueOf(validFeed.getId())).get();

        RefillResult result = DBWriter.refillQueue(QUEUE_ID, false).get();

        assertTrue(result.isSuccess());
        assertEquals("Valid rule's episodes added", 2, result.getEpisodesAdded());
        assertEquals("Invalid feed rule counted as skipped", 1, result.getRulesSkipped());
    }

    @Test
    public void testNoCrossRuleDuplicates() throws Exception {
        Feed feed = createAndStoreFeed("http://nodup.test");
        addItems(feed, 3, FeedItem.UNPLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        // Two rules for the same feed — should not produce duplicate episodes
        String feedIdStr = String.valueOf(feed.getId());
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                2, RefillRule.SourceType.FEED, feedIdStr).get();
        DBWriter.createRefillRule(rulesetId, 1, RefillRule.SelectionMethod.OLDEST,
                3, RefillRule.SourceType.FEED, feedIdStr).get();

        RefillResult result = DBWriter.refillQueue(QUEUE_ID, false).get();

        assertTrue(result.isSuccess());
        List<FeedItem> queue = DBReader.getQueue();
        // All 3 available items should be in queue (rule 1 takes 2, rule 2 takes remaining 1)
        assertEquals("No duplicates: max 3 unique episodes", 3, queue.size());
        // Verify no duplicate IDs
        long distinctCount = queue.stream().map(FeedItem::getId).distinct().count();
        assertEquals("All queue items must have distinct IDs", 3, distinctCount);
    }

    @Test
    public void testZeroResultNoEpisodesAdded() throws Exception {
        Feed feed = createAndStoreFeed("http://empty.test");
        // Only played items — nothing will be returned
        addItems(feed, 3, FeedItem.PLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                5, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        RefillResult result = DBWriter.refillQueue(QUEUE_ID, false).get();

        assertTrue("Refill succeeds even if 0 episodes found", result.isSuccess());
        assertEquals("0 episodes added", 0, result.getEpisodesAdded());
        assertTrue("Queue must remain empty", DBReader.getQueue().isEmpty());
    }

    @Test
    public void testNoRuleset_refillQueueReturnsEmptyResult() throws Exception {
        // No ruleset for QUEUE_ID → refillQueue should return empty success result
        RefillResult result = DBWriter.refillQueue(QUEUE_ID, false).get();

        assertNotNull(result);
        assertTrue("Should succeed (no-op)", result.isSuccess());
        assertEquals("0 episodes added", 0, result.getEpisodesAdded());
    }

    @Test
    public void testRefilledEventPostedAfterAdapterClose() throws Exception {
        Feed feed = createAndStoreFeed("http://event.test");
        addItems(feed, 2, FeedItem.UNPLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                2, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        DBWriter.refillQueue(QUEUE_ID, false).get();

        boolean received = eventLatch.await(3, TimeUnit.SECONDS);
        assertTrue("REFILLED event must be received", received);

        QueueEvent event = lastEvent.get();
        assertNotNull("Event must not be null", event);
        assertEquals("Action must be REFILLED", QueueEvent.Action.REFILLED, event.action);
        assertEquals("Event must carry queueId", QUEUE_ID, event.getQueueId());
        assertEquals("Event must carry episodesAdded count", 2, event.episodesAdded);
    }

    @Test
    public void testQueueWasEmptyFlagCorrect() throws Exception {
        Feed feed = createAndStoreFeed("http://queueempty.test");
        addItems(feed, 2, FeedItem.UNPLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                2, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        // Queue is empty before append
        RefillResult result = DBWriter.refillQueue(QUEUE_ID, false).get();
        assertTrue("queueWasEmpty must be true when appending to empty queue",
                result.wasQueueEmpty());
    }

    // ---- helpers ----

    private Feed createAndStoreFeed(String url) {
        Feed feed = new Feed(url, null, url);
        feed.setItems(new ArrayList<>());
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        return feed;
    }

    private void addItems(Feed feed, int count, int state) {
        long base = 1000000L * feed.getId();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        for (int i = 0; i < count; i++) {
            FeedItem item = new FeedItem(0, "Item-" + i,
                    "guid-" + feed.getId() + "-" + i,
                    "http://link/" + i, new Date(base + i * 10000L), state, feed);
            FeedMedia media = new FeedMedia(item, "http://media/" + i, 1024, "audio/mp3");
            media.setDuration(60000);
            item.setMedia(media);
            adapter.setSingleFeedItem(item);
            adapter.setMedia(media);
        }
        adapter.close();
    }

    private void setQueue(FeedItem... items) {
        List<FeedItem> list = new ArrayList<>();
        Collections.addAll(list, items);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setQueue(list);
        adapter.close();
    }
}
