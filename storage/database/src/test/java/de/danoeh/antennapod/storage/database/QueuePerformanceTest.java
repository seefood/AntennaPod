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
import static org.junit.Assert.assertTrue;

/**
 * Performance tests for queue operations (T033 - Phase 5).
 * Verifies that queue operations complete within acceptable time limits.
 * Target: < 100ms for individual operations, < 500ms for bulk operations.
 */
@RunWith(RobolectricTestRunner.class)
public class QueuePerformanceTest {
    private Context context;
    private Feed feed;
    private static final long OPERATION_TIMEOUT_MS = 100;
    private static final long BULK_OPERATION_TIMEOUT_MS = 500;

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
        for (int i = 0; i < 50; i++) {
            feed.getItems().add(createFeedItem(feed, "item-" + i, "Item " + i));
        }
        FeedDatabaseWriter.updateFeed(context, feed, false);
    }

    @Test
    public void testCreateQueuePerformance() throws Exception {
        long startTime = System.currentTimeMillis();
        DBWriter.createQueue("Performance Test Queue", 0xFF0000).get();
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        assertTrue("createQueue should complete within " + OPERATION_TIMEOUT_MS + "ms (took " + duration + "ms)",
                duration < OPERATION_TIMEOUT_MS);
    }

    @Test
    public void testGetAllQueuesPerformance() {
        long startTime = System.currentTimeMillis();
        List<QueueMetadata> queues = DBReader.getAllQueues();
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        assertTrue("getAllQueues should complete within " + OPERATION_TIMEOUT_MS + "ms (took " + duration + "ms)",
                duration < OPERATION_TIMEOUT_MS);
    }

    @Test
    public void testGetQueuePerformance() throws Exception {
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        for (int i = 0; i < 10; i++) {
            DBWriter.addQueueItem(context, items.get(i)).get();
        }

        long startTime = System.currentTimeMillis();
        List<FeedItem> queueItems = DBReader.getQueue(1);
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        assertTrue("getQueue should complete within " + OPERATION_TIMEOUT_MS + "ms (took " + duration + "ms)",
                duration < OPERATION_TIMEOUT_MS);
        assertEquals(10, queueItems.size());
    }

    @Test
    public void testAddQueueItemPerformance() throws Exception {
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);

        long startTime = System.currentTimeMillis();
        DBWriter.addQueueItem(context, items.get(0)).get();
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        assertTrue("addQueueItem should complete within " + OPERATION_TIMEOUT_MS + "ms (took " + duration + "ms)",
                duration < OPERATION_TIMEOUT_MS);
    }

    @Test
    public void testBulkAddQueueItemsPerformance() throws Exception {
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            DBWriter.addQueueItem(context, items.get(i)).get();
        }
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        assertTrue("Adding 20 items should complete within " + BULK_OPERATION_TIMEOUT_MS + "ms (took " + duration + "ms)",
                duration < BULK_OPERATION_TIMEOUT_MS);
    }

    @Test
    public void testCountQueueItemsPerformance() throws Exception {
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        for (int i = 0; i < 10; i++) {
            DBWriter.addQueueItem(context, items.get(i)).get();
        }

        long startTime = System.currentTimeMillis();
        int count = DBReader.countQueueItems(1);
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        assertTrue("countQueueItems should complete within " + OPERATION_TIMEOUT_MS + "ms (took " + duration + "ms)",
                duration < OPERATION_TIMEOUT_MS);
        assertEquals(10, count);
    }

    @Test
    public void testReorderQueuesPerformance() throws Exception {
        // Create multiple queues
        List<Long> queueIds = new ArrayList<>();
        queueIds.add(1L); // Default queue
        for (int i = 0; i < 5; i++) {
            queueIds.add(DBWriter.createQueue("Queue " + i, 0xFF0000).get());
        }

        long startTime = System.currentTimeMillis();
        DBWriter.reorderQueues(queueIds).get();
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        assertTrue("reorderQueues should complete within " + OPERATION_TIMEOUT_MS + "ms (took " + duration + "ms)",
                duration < OPERATION_TIMEOUT_MS);
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
