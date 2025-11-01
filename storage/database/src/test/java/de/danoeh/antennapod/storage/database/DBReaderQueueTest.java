package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for DBReader queue methods (T029 - Phase 5).
 * Verifies getAllQueues, getQueueMetadataById, getQueue, countQueueItems.
 */
@RunWith(RobolectricTestRunner.class)
public class DBReaderQueueTest {
    private Context context;
    private Feed feed1;
    private Feed feed2;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        // Create test feeds
        feed1 = createFeed(1, "Feed 1");
        feed2 = createFeed(2, "Feed 2");

        // Add items
        for (int i = 0; i < 3; i++) {
            feed1.getItems().add(createFeedItem(feed1, "Feed1Item" + i, "Feed 1 Item " + i));
            feed2.getItems().add(createFeedItem(feed2, "Feed2Item" + i, "Feed 2 Item " + i));
        }

        // Store feeds
        FeedDatabaseWriter.updateFeed(context, feed1, false);
        FeedDatabaseWriter.updateFeed(context, feed2, false);
    }

    @Test
    public void testGetAllQueues_DefaultQueue() {
        // T029: getAllQueues returns all queues sorted by sort_order
        List<QueueMetadata> queues = DBReader.getAllQueues();
        assertNotNull(queues);
        assertEquals(1, queues.size()); // Only default queue created during migration
        assertEquals(1, queues.get(0).getId());
    }

    @Test
    public void testGetAllQueues_MultipleQueues() throws Exception {
        // Create additional queues
        long queue2Id = DBWriter.createQueue("Queue 2", 0xFF0000).get();
        long queue3Id = DBWriter.createQueue("Queue 3", 0x00FF00).get();

        List<QueueMetadata> queues = DBReader.getAllQueues();
        assertEquals(3, queues.size());
        // Should be sorted by sort_order
        assertEquals(1, queues.get(0).getId());
        assertEquals(queue2Id, queues.get(1).getId());
        assertEquals(queue3Id, queues.get(2).getId());
    }

    @Test
    public void testGetQueueMetadataById_Exists() {
        QueueMetadata queue = DBReader.getQueueMetadataById(1);
        assertNotNull(queue);
        assertEquals(1, queue.getId());
    }

    @Test
    public void testGetQueueMetadataById_NotExists() {
        QueueMetadata queue = DBReader.getQueueMetadataById(999);
        assertNull(queue);
    }

    @Test
    public void testGetQueueMetadataById_ReturnsCorrectData() throws Exception {
        long queueId = DBWriter.createQueue("Test Queue", 0xFF0000).get();
        DBWriter.renameQueue(queueId, "Renamed Queue").get();

        QueueMetadata queue = DBReader.getQueueMetadataById(queueId);
        assertNotNull(queue);
        assertEquals("Renamed Queue", queue.getName());
        assertEquals(0xFF0000, queue.getColor());
    }

    @Test
    public void testGetQueue_WithQueueId() throws Exception {
        // Add items to queue
        List<FeedItem> feedItems = DBReader.getFeedItemList(feed1, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        for (int i = 0; i < 2; i++) {
            DBWriter.addQueueItem(context, feedItems.get(i)).get();
        }

        List<FeedItem> queueItems = DBReader.getQueue(1);
        assertEquals(2, queueItems.size());
    }

    @Test
    public void testGetQueue_EmptyQueue() {
        List<FeedItem> queueItems = DBReader.getQueue(1);
        assertNotNull(queueItems);
        assertEquals(0, queueItems.size());
    }

    @Test
    public void testGetQueue_NoArg_UsesActiveQueue() throws Exception {
        // Add items to queue
        List<FeedItem> feedItems = DBReader.getFeedItemList(feed1, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        for (int i = 0; i < 2; i++) {
            DBWriter.addQueueItem(context, feedItems.get(i)).get();
        }

        // Set active queue
        UserPreferences.setCurrentQueueId(1);

        List<FeedItem> queueItems = DBReader.getQueue();
        assertEquals(2, queueItems.size());
    }

    @Test
    public void testCountQueueItems_Empty() {
        int count = DBReader.countQueueItems(1);
        assertEquals(0, count);
    }

    @Test
    public void testCountQueueItems_WithItems() throws Exception {
        List<FeedItem> feedItems = DBReader.getFeedItemList(feed1, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        for (int i = 0; i < 3; i++) {
            DBWriter.addQueueItem(context, feedItems.get(i)).get();
        }

        int count = DBReader.countQueueItems(1);
        assertEquals(3, count);
    }

    @Test
    public void testCountQueueItems_MultipleQueues() throws Exception {
        List<FeedItem> feedItems = DBReader.getFeedItemList(feed1, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        for (int i = 0; i < 2; i++) {
            DBWriter.addQueueItem(context, feedItems.get(i)).get();
        }

        long queue2Id = DBWriter.createQueue("Queue 2", 0xFF0000).get();

        int count1 = DBReader.countQueueItems(1);
        int count2 = DBReader.countQueueItems(queue2Id);

        assertEquals(2, count1);
        assertEquals(0, count2); // Queue 2 is empty
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
