package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.storage.database.LongList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for backward compatibility with pre-multiple-queues code (T032 - Phase 5).
 * Verifies that old code using getQueue() without parameters still works correctly.
 */
@RunWith(RobolectricTestRunner.class)
public class QueueBackwardCompatibilityTest {
    private Context context;
    private Feed feed;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        feed = createFeed(1, "Test Feed");
        for (int i = 0; i < 3; i++) {
            feed.getItems().add(createFeedItem(feed, "item-" + i, "Item " + i));
        }
        FeedDatabaseWriter.updateFeed(context, feed, false);
    }

    @Test
    public void testGetQueue_NoArgReturnsActiveQueue() throws Exception {
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        DBWriter.addQueueItem(context, items.get(0)).get();

        // Old code calls getQueue() without parameters
        List<FeedItem> queueItems = DBReader.getQueue();
        assertNotNull(queueItems);
        assertEquals(1, queueItems.size());
    }

    @Test
    public void testGetQueueIdList_NoArgReturnsActiveQueue() throws Exception {
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        DBWriter.addQueueItem(context, items.get(0)).get();
        DBWriter.addQueueItem(context, items.get(1)).get();

        // Old code calls getQueueIdList() without parameters
        LongList queueIds = DBReader.getQueueIDList();
        assertNotNull(queueIds);
        assertEquals(2, queueIds.size());
    }

    @Test
    public void testAddQueueItemNoArg_UsesActiveQueue() throws Exception {
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);

        // Old code: addQueueItem(Context, FeedItem...)
        DBWriter.addQueueItem(context, items.get(0), items.get(1)).get();

        List<FeedItem> queueItems = DBReader.getQueue();
        assertEquals(2, queueItems.size());
    }

    @Test
    public void testActiveQueueDefaultsToQueue1() {
        // When no active queue is set, it should default to queue 1
        long activeQueueId = UserPreferences.getCurrentQueueId();
        assertEquals(1, activeQueueId);
    }

    // Helper methods
    private Feed createFeed(long feedId, String title) {
        Feed feed = new Feed("http://example.com/feed" + feedId, title, null);
        feed.setLink("http://example.com");
        feed.setItems(new ArrayList<>());
        return feed;
    }

    private FeedItem createFeedItem(Feed feed, String identifier, String title) {
        FeedItem item = new FeedItem();
        item.setFeed(feed);
        item.setItemIdentifier(identifier);
        item.setTitle(title);
        item.setLink("http://example.com/" + identifier);

        FeedMedia media = new FeedMedia(item, "http://example.com/media/" + identifier, 0, "audio/mpeg");
        item.setMedia(media);

        return item;
    }
}
