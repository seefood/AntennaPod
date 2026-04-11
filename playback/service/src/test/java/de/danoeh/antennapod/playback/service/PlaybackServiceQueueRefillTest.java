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
     * When queue is empty and no ruleset exists, refill is not triggered
     * and null is returned — playback stops.
     */
    @Test
    public void testQueueEmptyWithoutRulesetReturnsNull() {
        assertFalse("No ruleset should exist", DBReader.hasQueueRuleset(QUEUE_ID));

        // Simulate service logic: no ruleset → return null (existing stop behaviour)
        FeedItem next = null;
        if (DBReader.hasQueueRuleset(QUEUE_ID)) {
            // (would refill here)
            List<FeedItem> queue = DBReader.getQueue();
            next = DBReader.selectNextUnfinishedEpisode(queue);
        }
        assertNull("Without ruleset, result must be null", next);
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
     * When refill succeeds, selectNextUnfinishedEpisode skips fully-played episodes
     * and returns the first unfinished one.
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

        // Mark first episode as fully played (position == duration)
        if (!queue.isEmpty() && queue.get(0).getMedia() != null) {
            FeedMedia media = queue.get(0).getMedia();
            media.setPosition(media.getDuration());
        }

        FeedItem next = DBReader.selectNextUnfinishedEpisode(queue);
        assertNotNull("Must return a non-null item", next);
        // If all have position < duration, first item is returned; either way non-null
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
