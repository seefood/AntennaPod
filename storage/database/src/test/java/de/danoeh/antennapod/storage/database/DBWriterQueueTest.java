package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for DBWriter queue methods (T030 - Phase 5).
 * Verifies createQueue, renameQueue, changeQueueColor, deleteQueue, reorderQueues, setCurrentlyPlaying.
 */
@RunWith(RobolectricTestRunner.class)
public class DBWriterQueueTest {
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
        for (int i = 0; i < 5; i++) {
            feed.getItems().add(createFeedItem(feed, "item-" + i, "Item " + i));
        }
        FeedDatabaseWriter.updateFeed(context, feed, false);
    }

    @Test
    public void testCreateQueue_Success() throws Exception {
        long queueId = DBWriter.createQueue("New Queue", 0xFF0000).get();

        assertTrue(queueId > 0);
        QueueMetadata queue = DBReader.getQueueMetadataById(queueId);
        assertNotNull(queue);
        assertEquals("New Queue", queue.getName());
        assertEquals(0xFF0000, queue.getColor());
    }

    @Test
    public void testCreateQueue_InvalidName_ThrowsException() throws Exception {
        try {
            DBWriter.createQueue("", 0xFF0000).get();
            assertTrue("Should throw IllegalArgumentException", false);
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testCreateQueue_SortOrderIncrementing() throws Exception {
        long queue1 = DBWriter.createQueue("Queue 1", 0xFF0000).get();
        long queue2 = DBWriter.createQueue("Queue 2", 0x00FF00).get();
        long queue3 = DBWriter.createQueue("Queue 3", 0x0000FF).get();

        QueueMetadata q1 = DBReader.getQueueMetadataById(queue1);
        QueueMetadata q2 = DBReader.getQueueMetadataById(queue2);
        QueueMetadata q3 = DBReader.getQueueMetadataById(queue3);

        assertEquals(1, q1.getSortOrder());
        assertEquals(2, q2.getSortOrder());
        assertEquals(3, q3.getSortOrder());
    }

    @Test
    public void testRenameQueue_Success() throws Exception {
        long queueId = DBWriter.createQueue("Original Name", 0xFF0000).get();
        DBWriter.renameQueue(queueId, "New Name").get();

        QueueMetadata queue = DBReader.getQueueMetadataById(queueId);
        assertEquals("New Name", queue.getName());
    }

    @Test
    public void testChangeQueueColor_Success() throws Exception {
        long queueId = DBWriter.createQueue("Test Queue", 0xFF0000).get();
        DBWriter.changeQueueColor(queueId, 0x00FF00).get();

        QueueMetadata queue = DBReader.getQueueMetadataById(queueId);
        assertEquals(0x00FF00, queue.getColor());
    }

    @Test
    public void testDeleteQueue_Success() throws Exception {
        long queueId = DBWriter.createQueue("Queue to Delete", 0xFF0000).get();
        DBWriter.deleteQueue(queueId).get();

        QueueMetadata queue = DBReader.getQueueMetadataById(queueId);
        assertNull(queue);
    }

    @Test
    public void testDeleteQueue_LastQueueFails() throws Exception {
        try {
            DBWriter.deleteQueue(1).get(); // Default queue
            assertTrue("Should throw IllegalStateException", false);
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void testDeleteQueue_DeletesItems() throws Exception {
        long queueId = DBWriter.createQueue("Queue with Items", 0xFF0000).get();

        List<FeedItem> items = DBReader.getFeedItemList(feed);
        for (int i = 0; i < 2; i++) {
            DBWriter.addQueueItem(context, items.get(i)).get();
        }

        int countBefore = DBReader.countQueueItems(queueId);
        assertEquals(2, countBefore);

        DBWriter.deleteQueue(queueId).get();

        int countAfter = DBReader.countQueueItems(queueId);
        assertEquals(0, countAfter);
    }

    @Test
    public void testReorderQueues_Success() throws Exception {
        long queue1 = DBWriter.createQueue("Queue 1", 0xFF0000).get();
        long queue2 = DBWriter.createQueue("Queue 2", 0x00FF00).get();
        long queue3 = DBWriter.createQueue("Queue 3", 0x0000FF).get();

        // Reorder: queue3, queue1, queue2
        DBWriter.reorderQueues(Arrays.asList(queue3, queue1, queue2)).get();

        List<QueueMetadata> queues = DBReader.getAllQueues();
        assertEquals(queue3, queues.get(3).getId()); // Index 3 because default queue at 0
        assertEquals(queue1, queues.get(4).getId());
        assertEquals(queue2, queues.get(5).getId());
    }

    @Test
    public void testSetCurrentlyPlaying_Success() throws Exception {
        long queueId = DBWriter.createQueue("Test Queue", 0xFF0000).get();
        List<FeedItem> items = DBReader.getFeedItemList(feed);
        FeedItem item = items.get(0);
        FeedMedia media = item.getMedia();

        DBWriter.setCurrentlyPlaying(queueId, media.getId(), item.getFeed().getId()).get();

        QueueMetadata queue = DBReader.getQueueMetadataById(queueId);
        assertEquals(media.getId(), queue.getCurrentlyPlayingFeedMediaId());
        assertEquals(item.getFeed().getId(), queue.getCurrentlyPlayingFeedId());
    }

    @Test
    public void testSetCurrentlyPlaying_ClearByUsingNoMedia() throws Exception {
        long queueId = DBWriter.createQueue("Test Queue", 0xFF0000).get();
        List<FeedItem> items = DBReader.getFeedItemList(feed);
        FeedItem item = items.get(0);

        // Set playing
        DBWriter.setCurrentlyPlaying(queueId, item.getMedia().getId(), item.getFeed().getId()).get();
        QueueMetadata queue1 = DBReader.getQueueMetadataById(queueId);
        assertFalse(queue1.isEpisodePlaying() == false); // Should be playing

        // Clear playing
        DBWriter.setCurrentlyPlaying(queueId, QueueMetadata.NO_MEDIA_PLAYING, QueueMetadata.NO_MEDIA_PLAYING).get();
        QueueMetadata queue2 = DBReader.getQueueMetadataById(queueId);
        assertFalse(queue2.isEpisodePlaying());
    }

    @Test
    public void testAddQueueItem_AddsToActiveQueue() throws Exception {
        UserPreferences.setCurrentQueueId(1);
        List<FeedItem> items = DBReader.getFeedItemList(feed);

        DBWriter.addQueueItem(context, items.get(0)).get();

        List<FeedItem> queueItems = DBReader.getQueue();
        assertEquals(1, queueItems.size());
        assertEquals(items.get(0).getId(), queueItems.get(0).getId());
    }

    // Helper methods
    @Nullable
    private QueueMetadata assertNull(QueueMetadata queue) {
        // Helper to match test expectations
        if (queue == null) return null;
        throw new AssertionError("Expected null but was " + queue);
    }

    private Feed createFeed(long feedId, String title) {
        Feed feed = new Feed();
        feed.setDownloadUrl("http://example.com/feed" + feedId);
        feed.setTitle(title);
        feed.setLink("http://example.com");
        feed.setDescription("Test Description");
        return feed;
    }

    private FeedItem createFeedItem(Feed feed, String identifier, String title) {
        FeedItem item = new FeedItem();
        item.setFeed(feed);
        item.setItemIdentifier(identifier);
        item.setTitle(title);
        item.setLink("http://example.com/" + identifier);
        item.setPubDate(System.currentTimeMillis());

        FeedMedia media = new FeedMedia();
        media.setItem(item);
        media.setDownloadUrl("http://example.com/media/" + identifier);
        item.setMedia(media);

        return item;
    }
}
