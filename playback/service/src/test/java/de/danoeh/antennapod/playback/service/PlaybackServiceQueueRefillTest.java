package de.danoeh.antennapod.playback.service;

import android.content.Context;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the auto-refill logic triggered at queue end in playback services.
 *
 * <p>Rather than instantiating the full service (which requires an emulator),
 * these tests verify the underlying DB interaction sequence:
 * hasQueueRuleset → refillQueue → selectNextUnfinishedEpisode.
 * Both PlaybackService.getNextInQueue() and Media3PlaybackService.startNextInQueue()
 * use identical logic, so verifying the DB layer is sufficient.
 */
@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceQueueRefillTest {
    private static final long QUEUE_ID = 1L;
    private Context context;

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
    }

    @After
    public void tearDown() throws Exception {
        DBWriter.tearDownTests();
    }

    /**
     * When queue is empty and a ruleset exists, refill should add episodes
     * and selectNextUnfinishedEpisode should return the first one.
     */
    @Test
    public void testQueueEmptyWithRulesetTriggersRefill() throws Exception {
        Feed feed = createAndStoreFeed("http://refill-test.example");
        addItems(feed, 3, FeedItem.UNPLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                3, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        // Simulate what playback service does: getNextInQueue returns null → check ruleset
        assertTrue("Ruleset must exist", DBReader.hasQueueRuleset(QUEUE_ID));

        DBWriter.refillQueue(QUEUE_ID, false).get();

        List<FeedItem> queue = DBReader.getQueue();
        assertFalse("Queue must not be empty after refill", queue.isEmpty());

        FeedItem next = DBReader.selectNextUnfinishedEpisode(queue);
        assertNotNull("selectNextUnfinishedEpisode must return non-null", next);
    }

    /**
     * When no ruleset exists, hasQueueRuleset returns false — the service skips
     * refill and stops playback.
     */
    @Test
    public void testQueueEmptyWithoutRulesetReturnsNull() {
        assertFalse("No ruleset should exist", DBReader.hasQueueRuleset(QUEUE_ID));
        // selectNextUnfinishedEpisode on an empty queue must return null.
        assertNull("Empty queue must yield null", DBReader.selectNextUnfinishedEpisode(DBReader.getQueue()));
    }

    /**
     * When refill yields zero new episodes (all played), selectNextUnfinishedEpisode
     * returns null — playback stops.
     */
    @Test
    public void testRefillYieldsZeroEpisodesReturnsNull() throws Exception {
        Feed feed = createAndStoreFeed("http://all-played.example");
        addItems(feed, 2, FeedItem.PLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                5, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        DBWriter.refillQueue(QUEUE_ID, false).get();

        List<FeedItem> queue = DBReader.getQueue();
        assertTrue("Queue must be empty when all episodes played", queue.isEmpty());

        FeedItem next = DBReader.selectNextUnfinishedEpisode(queue);
        assertNull("selectNextUnfinishedEpisode must return null for empty queue", next);
    }

    /**
     * When refill succeeds, selectNextUnfinishedEpisode skips a fully-played first
     * episode and returns the next unfinished one.
     */
    @Test
    public void testRefillSelectsFirstUnfinishedEpisode() throws Exception {
        Feed feed = createAndStoreFeed("http://unfinished.example");
        addItems(feed, 3, FeedItem.UNPLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                3, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        DBWriter.refillQueue(QUEUE_ID, false).get();

        List<FeedItem> queue = DBReader.getQueue();
        assertFalse("Queue must not be empty after refill", queue.isEmpty());
        assertTrue("Queue must have at least 2 items to test skipping", queue.size() >= 2);

        // Mark first episode as fully played (position == duration).
        FeedMedia firstMedia = queue.get(0).getMedia();
        assertNotNull("First item must have media", firstMedia);
        firstMedia.setPosition(firstMedia.getDuration());

        FeedItem next = DBReader.selectNextUnfinishedEpisode(queue);
        assertNotNull("Must return a non-null item", next);
        // The fully-played item must be skipped; the second item must be returned.
        assertEquals("Must skip fully-played first episode and return second",
                queue.get(1).getId(), next.getId());
    }

    /**
     * When all episodes in the queue are fully played, selectNextUnfinishedEpisode
     * falls back to the first item (not null) — documented fallback behaviour.
     */
    @Test
    public void testAllFullyPlayedFallsBackToFirstItem() throws Exception {
        Feed feed = createAndStoreFeed("http://all-finished.example");
        addItems(feed, 2, FeedItem.UNPLAYED);

        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                2, RefillRule.SourceType.FEED, String.valueOf(feed.getId())).get();

        DBWriter.refillQueue(QUEUE_ID, false).get();

        List<FeedItem> queue = DBReader.getQueue();
        assertFalse("Queue must not be empty", queue.isEmpty());

        // Mark all episodes as fully played.
        for (FeedItem item : queue) {
            FeedMedia media = item.getMedia();
            if (media != null) {
                media.setPosition(media.getDuration());
            }
        }

        FeedItem next = DBReader.selectNextUnfinishedEpisode(queue);
        // Fallback: returns queue.get(0) rather than null.
        assertNotNull("Fallback must return first item, not null", next);
        assertEquals("Fallback must be queue.get(0)", queue.get(0).getId(), next.getId());
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
}
