package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for DBReader.getEpisodesForRule() and DBReader.selectNextUnfinishedEpisode().
 */
@RunWith(RobolectricTestRunner.class)
public class DBReaderQueueRulesetEpisodeQueryTest {
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

    // ---- getEpisodesForRule: FEED source ----

    @Test
    public void testFeedSourceOldestFirst() throws Exception {
        Feed feed = createAndStoreFeed("http://feed1.test", "Feed1", null);
        addItems(feed, 3, FeedItem.UNPLAYED);

        RefillRule rule = feedRule(String.valueOf(feed.getId()),
                RefillRule.SelectionMethod.OLDEST, 2);
        List<FeedItem> result = DBReader.getEpisodesForRule(rule, new HashSet<>());

        assertEquals("Should return 2 items", 2, result.size());
        assertTrue("First item must have older pubDate",
                result.get(0).getPubDate().before(result.get(1).getPubDate())
                        || result.get(0).getPubDate().equals(result.get(1).getPubDate()));
    }

    @Test
    public void testFeedSourceNewestFirst() throws Exception {
        Feed feed = createAndStoreFeed("http://feed2.test", "Feed2", null);
        addItems(feed, 3, FeedItem.UNPLAYED);

        RefillRule rule = feedRule(String.valueOf(feed.getId()),
                RefillRule.SelectionMethod.NEWEST, 2);
        List<FeedItem> result = DBReader.getEpisodesForRule(rule, new HashSet<>());

        assertEquals("Should return 2 items", 2, result.size());
        assertTrue("First item must have newer pubDate",
                result.get(0).getPubDate().after(result.get(1).getPubDate())
                        || result.get(0).getPubDate().equals(result.get(1).getPubDate()));
    }

    @Test
    public void testFeedSourceExcludesPlayedEpisodes() throws Exception {
        Feed feed = createAndStoreFeed("http://feed3.test", "Feed3", null);
        addItems(feed, 2, FeedItem.UNPLAYED);
        addItems(feed, 2, FeedItem.PLAYED);

        RefillRule rule = feedRule(String.valueOf(feed.getId()),
                RefillRule.SelectionMethod.OLDEST, 10);
        List<FeedItem> result = DBReader.getEpisodesForRule(rule, new HashSet<>());

        assertEquals("Only unplayed should be returned", 2, result.size());
        for (FeedItem item : result) {
            assertTrue("All items must be unplayed", item.isNew() || !item.isPlayed());
        }
    }

    @Test
    public void testFeedSourceExcludesQueuedEpisodes() throws Exception {
        Feed feed = createAndStoreFeed("http://feed4.test", "Feed4", null);
        addItems(feed, 3, FeedItem.UNPLAYED);

        // Queue the first episode directly via adapter to avoid PlaybackPreferences dependency
        Feed reloaded = DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
        FeedItem toQueue = reloaded.getItems().get(0);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setQueue(Collections.singletonList(toQueue));
        adapter.close();

        RefillRule rule = feedRule(String.valueOf(feed.getId()),
                RefillRule.SelectionMethod.OLDEST, 10);
        List<FeedItem> result = DBReader.getEpisodesForRule(rule, new HashSet<>());

        assertEquals("Queued item must be excluded", 2, result.size());
        for (FeedItem item : result) {
            assertTrue("Returned item must not be in queue", item.getId() != toQueue.getId());
        }
    }

    @Test
    public void testFeedSourcePartialFulfillment() throws Exception {
        Feed feed = createAndStoreFeed("http://feed5.test", "Feed5", null);
        addItems(feed, 2, FeedItem.UNPLAYED);

        RefillRule rule = feedRule(String.valueOf(feed.getId()),
                RefillRule.SelectionMethod.OLDEST, 10);
        List<FeedItem> result = DBReader.getEpisodesForRule(rule, new HashSet<>());

        assertEquals("Partial: only 2 available, requested 10", 2, result.size());
    }

    @Test
    public void testFeedSourceDeletedFeedReturnsEmpty() {
        RefillRule rule = feedRule("99999",
                RefillRule.SelectionMethod.OLDEST, 5);
        List<FeedItem> result = DBReader.getEpisodesForRule(rule, new HashSet<>());

        assertTrue("Unknown feedId must return empty list", result.isEmpty());
    }

    @Test
    public void testFeedSourceStagedIdsExcluded() throws Exception {
        Feed feed = createAndStoreFeed("http://feed6.test", "Feed6", null);
        addItems(feed, 3, FeedItem.UNPLAYED);

        Feed reloaded = DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
        long stagedId = reloaded.getItems().get(0).getId();
        Set<Long> staged = new HashSet<>();
        staged.add(stagedId);

        RefillRule rule = feedRule(String.valueOf(feed.getId()),
                RefillRule.SelectionMethod.OLDEST, 10);
        List<FeedItem> result = DBReader.getEpisodesForRule(rule, staged);

        assertEquals("stagedId must be excluded", 2, result.size());
        for (FeedItem item : result) {
            assertTrue("Must not contain staged id", item.getId() != stagedId);
        }
    }

    // ---- getEpisodesForRule: INBOX source ----

    @Test
    public void testInboxSourceReturnsNewEpisodes() throws Exception {
        Feed feed = createAndStoreFeed("http://feed7.test", "Feed7", null);
        addItems(feed, 2, FeedItem.NEW);
        addItems(feed, 2, FeedItem.UNPLAYED);
        addItems(feed, 1, FeedItem.PLAYED);

        RefillRule rule = inboxRule(RefillRule.SelectionMethod.OLDEST, 10);
        List<FeedItem> result = DBReader.getEpisodesForRule(rule, new HashSet<>());

        assertEquals("Only NEW episodes should be returned", 2, result.size());
        for (FeedItem item : result) {
            assertTrue("All items must be new", item.isNew());
        }
    }

    // ---- getEpisodesForRule: TAG source ----

    @Test
    public void testTagSourceFiltersCorrectly() throws Exception {
        Set<String> tags = Collections.singleton("work");
        Feed tagged = createAndStoreFeed("http://tagged.test", "Tagged", tags);
        Feed untagged = createAndStoreFeed("http://untagged.test", "Untagged", null);
        addItems(tagged, 2, FeedItem.UNPLAYED);
        addItems(untagged, 2, FeedItem.UNPLAYED);

        RefillRule rule = tagRule("work", RefillRule.SelectionMethod.OLDEST, 10);
        List<FeedItem> result = DBReader.getEpisodesForRule(rule, new HashSet<>());

        assertEquals("Only tagged feed's episodes", 2, result.size());
        for (FeedItem item : result) {
            assertEquals("All items must come from tagged feed",
                    tagged.getId(), item.getFeed().getId());
        }
    }

    // ---- selectNextUnfinishedEpisode ----

    @Test
    public void testSelectNextUnfinishedReturnsFirstUnfinished() {
        List<FeedItem> items = new ArrayList<>();
        items.add(makeItemWithMedia(1, 1000, 1000)); // 100% played
        items.add(makeItemWithMedia(2, 500, 1000));  // 50% - first unfinished
        items.add(makeItemWithMedia(3, 200, 1000));  // 20%

        FeedItem result = DBReader.selectNextUnfinishedEpisode(items);
        assertNotNull(result);
        assertEquals("Should return first unfinished episode", 2, result.getId());
    }

    @Test
    public void testSelectNextUnfinishedFallsBackToFirstWhenAllPlayed() {
        List<FeedItem> items = new ArrayList<>();
        items.add(makeItemWithMedia(1, 1000, 1000)); // 100% played
        items.add(makeItemWithMedia(2, 1000, 1000)); // 100% played

        FeedItem result = DBReader.selectNextUnfinishedEpisode(items);
        assertNotNull(result);
        assertEquals("Should fall back to first item", 1, result.getId());
    }

    @Test
    public void testSelectNextUnfinishedReturnsNullForEmptyList() {
        assertNull("Empty list must return null",
                DBReader.selectNextUnfinishedEpisode(new ArrayList<>()));
    }

    @Test
    public void testSelectNextUnfinishedReturnsFirstUnfinishedAtPosition0() {
        List<FeedItem> items = new ArrayList<>();
        items.add(makeItemWithMedia(1, 0, 1000));    // position 0 = unfinished
        items.add(makeItemWithMedia(2, 1000, 1000)); // 100% played

        FeedItem result = DBReader.selectNextUnfinishedEpisode(items);
        assertNotNull(result);
        assertEquals("Item with position 0 is unfinished (< duration)", 1, result.getId());
    }

    // ---- helpers ----

    private Feed createAndStoreFeed(String url, String title, Set<String> tags) {
        Feed feed = new Feed(url, null, title);
        feed.setItems(new ArrayList<>());
        if (tags != null && !tags.isEmpty()) {
            FeedPreferences prefs = new FeedPreferences(0,
                    FeedPreferences.AutoDownloadSetting.GLOBAL,
                    FeedPreferences.AutoDeleteAction.GLOBAL,
                    VolumeAdaptionSetting.OFF,
                    FeedPreferences.NewEpisodesAction.GLOBAL,
                    null, null);
            prefs.getTags().addAll(tags);
            feed.setPreferences(prefs);
        }
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        assertTrue("Feed must be persisted with id > 0", feed.getId() != 0);
        return feed;
    }

    private void addItems(Feed feed, int count, int state) {
        long base = 1000000L * feed.getId();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        for (int i = 0; i < count; i++) {
            FeedItem item = new FeedItem(0, "Item-" + feed.getId() + "-" + i,
                    "guid-" + feed.getId() + "-" + System.nanoTime(),
                    "http://link/" + i, new Date(base + i * 10000L), state, feed);
            FeedMedia media = new FeedMedia(item, "http://media/" + i, 1024, "audio/mp3");
            media.setDuration(60000);
            item.setMedia(media);
            adapter.setSingleFeedItem(item);
            adapter.setMedia(media);
        }
        adapter.close();
    }

    private FeedItem makeItemWithMedia(long id, int position, int duration) {
        FeedItem item = new FeedItem();
        item.setId(id);
        FeedMedia media = new FeedMedia(item, "url", 0, "audio/mp3");
        media.setPosition(position);
        media.setDuration(duration);
        item.setMedia(media);
        return item;
    }

    private RefillRule feedRule(String feedId, RefillRule.SelectionMethod method, int count) {
        return new RefillRule(0L, 0L, 0, method, count,
                RefillRule.SourceType.FEED, feedId, 0L, 0L);
    }

    private RefillRule inboxRule(RefillRule.SelectionMethod method, int count) {
        return new RefillRule(0L, 0L, 0, method, count,
                RefillRule.SourceType.INBOX, null, 0L, 0L);
    }

    private RefillRule tagRule(String tag, RefillRule.SelectionMethod method, int count) {
        return new RefillRule(0L, 0L, 0, method, count,
                RefillRule.SourceType.TAG, tag, 0L, 0L);
    }
}
