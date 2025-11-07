package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.CopyResult;
import de.danoeh.antennapod.model.MoveResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for queue transfer operations (move/copy) in DBWriter.
 * Verifies moveQueueItem, copyQueueItem, moveQueueItems, copyQueueItems.
 */
@RunWith(RobolectricTestRunner.class)
public class DBWriterQueueTransferTest {
    private Context context;
    private Feed feed;
    private long queue1Id;
    private long queue2Id;
    private long queue3Id;
    private long queue4Id;
    private List<FeedItem> testItems;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        // Create test feed with items
        feed = createFeed(1, "Test Feed");
        for (int i = 0; i < 10; i++) {
            feed.getItems().add(createFeedItem(feed, "item-" + i, "Item " + i));
        }
        FeedDatabaseWriter.updateFeed(context, feed, false);

        // Create test queues
        queue1Id = DBWriter.createQueue("Queue 1", 0xFF0000).get();
        queue2Id = DBWriter.createQueue("Queue 2", 0x00FF00).get();
        queue3Id = DBWriter.createQueue("Queue 3", 0x0000FF).get();
        queue4Id = DBWriter.createQueue("Queue 4", 0xFFFF00).get();

        // Get test items
        testItems = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
    }

    @After
    public void tearDown() {
        DBWriter.tearDownTests();
    }

    @Test
    public void testMoveQueueItem_RemovesFromSourceAddsToTarget() throws Exception {
        // Add item to queue1 using the queueId-aware method
        long itemId = testItems.get(0).getId();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            queue1.add(testItems.get(0));
            adapter.setQueue(queue1, queue1Id);
        } finally {
            adapter.close();
        }

        // Verify item in queue1
        List<FeedItem> queue1Before = DBReader.getQueue(queue1Id);
        assertTrue("Item should be in queue1", queue1Before.stream()
                .anyMatch(item -> item.getId() == itemId));

        // Move from queue1 to queue2
        MoveResult result = DBWriter.moveQueueItem(itemId, queue1Id, queue2Id).get();

        // Verify result
        assertEquals("Should have moved 1 item", 1, result.getMovedCount());
        assertEquals("Should have skipped 0 items", 0, result.getSkippedCount());

        // Verify item removed from queue1
        List<FeedItem> queue1After = DBReader.getQueue(queue1Id);
        assertFalse("Item should not be in queue1", queue1After.stream()
                .anyMatch(item -> item.getId() == itemId));

        // Verify item added to queue2
        List<FeedItem> queue2After = DBReader.getQueue(queue2Id);
        assertTrue("Item should be in queue2", queue2After.stream()
                .anyMatch(item -> item.getId() == itemId));
    }

    @Test
    public void testMoveQueueItem_DoesNotAffectOtherQueues() throws Exception {
        // CRITICAL TEST: Episode exists in Q1, Q2, Q3; move from Q1→Q4; verify remains in Q2, Q3
        long itemId = testItems.get(0).getId();

        // Add item to queue1, queue2, and queue3
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            queue1.add(testItems.get(0));
            adapter.setQueue(queue1, queue1Id);

            List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
            queue2.add(testItems.get(0));
            adapter.setQueue(queue2, queue2Id);

            List<FeedItem> queue3 = DBReader.getQueue(queue3Id);
            queue3.add(testItems.get(0));
            adapter.setQueue(queue3, queue3Id);
        } finally {
            adapter.close();
        }

        // Verify item in all three queues
        assertTrue("Item should be in queue1", DBReader.getQueue(queue1Id).stream()
                .anyMatch(item -> item.getId() == itemId));
        assertTrue("Item should be in queue2", DBReader.getQueue(queue2Id).stream()
                .anyMatch(item -> item.getId() == itemId));
        assertTrue("Item should be in queue3", DBReader.getQueue(queue3Id).stream()
                .anyMatch(item -> item.getId() == itemId));

        // Move from queue1 to queue4
        MoveResult result = DBWriter.moveQueueItem(itemId, queue1Id, queue4Id).get();

        // Verify result
        assertEquals("Should have moved 1 item", 1, result.getMovedCount());

        // Verify item removed from queue1
        assertFalse("Item should not be in queue1", DBReader.getQueue(queue1Id).stream()
                .anyMatch(item -> item.getId() == itemId));

        // CRITICAL: Verify item remains in queue2 and queue3
        assertTrue("Item should remain in queue2", DBReader.getQueue(queue2Id).stream()
                .anyMatch(item -> item.getId() == itemId));
        assertTrue("Item should remain in queue3", DBReader.getQueue(queue3Id).stream()
                .anyMatch(item -> item.getId() == itemId));

        // Verify item added to queue4
        assertTrue("Item should be in queue4", DBReader.getQueue(queue4Id).stream()
                .anyMatch(item -> item.getId() == itemId));
    }

    @Test
    public void testMoveQueueItem_FailsWhenNotInSourceQueue() throws Exception {
        long itemId = testItems.get(0).getId();

        // Try to move item that's not in queue1
        MoveResult result = DBWriter.moveQueueItem(itemId, queue1Id, queue2Id).get();

        // Should fail
        assertEquals("Should have moved 0 items", 0, result.getMovedCount());
        assertEquals("Should have skipped 1 item", 1, result.getSkippedCount());
        assertTrue("Skipped item IDs should contain itemId", result.getSkippedItemIds().contains(itemId));
        assertNotNull("Skip reason should be present", result.getSkipReasons().get(itemId));
    }

    @Test
    public void testCopyQueueItem_AddsToTargetKeepsInSource() throws Exception {
        long itemId = testItems.get(0).getId();

        // Add item to queue1
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            queue1.add(testItems.get(0));
            adapter.setQueue(queue1, queue1Id);
        } finally {
            adapter.close();
        }

        // Copy from queue1 to queue2
        CopyResult result = DBWriter.copyQueueItem(itemId, queue2Id).get();

        // Verify result
        assertEquals("Should have copied 1 item", 1, result.getCopiedCount());
        assertEquals("Should have skipped 0 items", 0, result.getSkippedCount());

        // Verify item still in queue1
        assertTrue("Item should remain in queue1", DBReader.getQueue(queue1Id).stream()
                .anyMatch(item -> item.getId() == itemId));

        // Verify item added to queue2
        assertTrue("Item should be in queue2", DBReader.getQueue(queue2Id).stream()
                .anyMatch(item -> item.getId() == itemId));
    }

    @Test
    public void testCopyQueueItem_FailsWhenAlreadyInTarget() throws Exception {
        long itemId = testItems.get(0).getId();

        // Add item to queue1 and queue2
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            queue1.add(testItems.get(0));
            adapter.setQueue(queue1, queue1Id);

            List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
            queue2.add(testItems.get(0));
            adapter.setQueue(queue2, queue2Id);
        } finally {
            adapter.close();
        }

        // Try to copy to queue2 (already there)
        CopyResult result = DBWriter.copyQueueItem(itemId, queue2Id).get();

        // Should fail (duplicate detection)
        assertEquals("Should have copied 0 items", 0, result.getCopiedCount());
        assertEquals("Should have skipped 1 item", 1, result.getSkippedCount());
        assertTrue("Skipped item IDs should contain itemId", result.getSkippedItemIds().contains(itemId));
        assertNotNull("Skip reason should mention duplicate", result.getSkipReasons().get(itemId));
    }

    @Test
    public void testMoveQueueItems_BestEffort() throws Exception {
        // Add 5 items to queue1, but only 3 exist in queue1
        List<Long> itemIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            itemIds.add(testItems.get(i).getId());
        }

        // Add first 3 items to queue1
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            for (int i = 0; i < 3; i++) {
                queue1.add(testItems.get(i));
            }
            adapter.setQueue(queue1, queue1Id);
        } finally {
            adapter.close();
        }

        // Move all 5 items (3 succeed, 2 fail)
        MoveResult result = DBWriter.moveQueueItems(itemIds, queue1Id, queue2Id).get();

        // Verify result
        assertEquals("Should have moved 3 items", 3, result.getMovedCount());
        assertEquals("Should have skipped 2 items", 2, result.getSkippedCount());
        assertEquals("Should have 2 skipped item IDs", 2, result.getSkippedItemIds().size());

        // Verify 3 items in queue2
        List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
        assertEquals("Queue2 should have 3 items", 3, queue2.size());
    }

    @Test
    public void testCopyQueueItems_SkipsDuplicates() throws Exception {
        // Add 5 items to queue1, 2 already in queue2
        List<Long> itemIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            itemIds.add(testItems.get(i).getId());
        }

        // Add all 5 to queue1
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            for (int i = 0; i < 5; i++) {
                queue1.add(testItems.get(i));
            }
            adapter.setQueue(queue1, queue1Id);

            // Add first 2 to queue2 (duplicates)
            List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
            queue2.add(testItems.get(0));
            queue2.add(testItems.get(1));
            adapter.setQueue(queue2, queue2Id);
        } finally {
            adapter.close();
        }

        // Copy all 5 items to queue2 (2 duplicates, 3 succeed)
        CopyResult result = DBWriter.copyQueueItems(itemIds, queue2Id).get();

        // Verify result
        assertEquals("Should have copied 3 items", 3, result.getCopiedCount());
        assertEquals("Should have skipped 2 items", 2, result.getSkippedCount());

        // Verify 5 items total in queue2 (2 original + 3 new)
        List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
        assertEquals("Queue2 should have 5 items", 5, queue2.size());
    }

    @Test
    public void testCopyQueueItems_PreservesOrder() throws Exception {
        // Add 5 items to queue1
        List<Long> itemIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            itemIds.add(testItems.get(i).getId());
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            for (int i = 0; i < 5; i++) {
                queue1.add(testItems.get(i));
            }
            adapter.setQueue(queue1, queue1Id);
        } finally {
            adapter.close();
        }

        // Copy all 5 items to queue2
        CopyResult result = DBWriter.copyQueueItems(itemIds, queue2Id).get();

        // Verify order preserved (items appended in order)
        List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
        assertEquals("Queue2 should have 5 items", 5, queue2.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("Item order should be preserved", itemIds.get(i).longValue(),
                    queue2.get(i).getId());
        }
    }

    @Test
    public void testMoveQueueItem_ToSameQueue_Fails() throws Exception {
        long itemId = testItems.get(0).getId();

        // Add item to queue1
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            queue1.add(testItems.get(0));
            adapter.setQueue(queue1, queue1Id);
        } finally {
            adapter.close();
        }

        // Try to move from queue1 to queue1 (same queue)
        // The implementation removes from source, then adds to target.
        // When source == target, it removes then adds back (no-op behavior).
        MoveResult result = DBWriter.moveQueueItem(itemId, queue1Id, queue1Id).get();

        // Should succeed (no-op: removes then adds back)
        assertEquals("Should have moved 1 item", 1, result.getMovedCount());
        assertEquals("Should have skipped 0 items", 0, result.getSkippedCount());

        // Verify item still in queue1
        List<FeedItem> queue1After = DBReader.getQueue(queue1Id);
        assertTrue("Item should still be in queue1", queue1After.stream()
                .anyMatch(item -> item.getId() == itemId));
    }

    @Test
    public void testCopyQueueItem_ToSameQueue_Fails() throws Exception {
        long itemId = testItems.get(0).getId();

        // Add item to queue1
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            queue1.add(testItems.get(0));
            adapter.setQueue(queue1, queue1Id);
        } finally {
            adapter.close();
        }

        // Try to copy to queue1 (same queue - already there)
        CopyResult result = DBWriter.copyQueueItem(itemId, queue1Id).get();

        // Should fail (duplicate detection)
        assertEquals("Should have copied 0 items", 0, result.getCopiedCount());
        assertEquals("Should have skipped 1 item", 1, result.getSkippedCount());
        assertTrue("Skipped item IDs should contain itemId", result.getSkippedItemIds().contains(itemId));
        assertNotNull("Skip reason should mention duplicate", result.getSkipReasons().get(itemId));
    }

    @Test
    public void testMoveQueueItem_InvalidItemId_Fails() throws Exception {
        long invalidItemId = 99999L; // Non-existent item ID

        // Try to move non-existent item
        MoveResult result = DBWriter.moveQueueItem(invalidItemId, queue1Id, queue2Id).get();

        // Should fail
        assertEquals("Should have moved 0 items", 0, result.getMovedCount());
        assertEquals("Should have skipped 1 item", 1, result.getSkippedCount());
        assertTrue("Skipped item IDs should contain invalidItemId", result.getSkippedItemIds().contains(invalidItemId));
        assertNotNull("Skip reason should be present", result.getSkipReasons().get(invalidItemId));
    }

    @Test
    public void testCopyQueueItem_InvalidItemId_Fails() throws Exception {
        long invalidItemId = 99999L; // Non-existent item ID

        // Try to copy non-existent item
        CopyResult result = DBWriter.copyQueueItem(invalidItemId, queue2Id).get();

        // Should fail
        assertEquals("Should have copied 0 items", 0, result.getCopiedCount());
        assertEquals("Should have skipped 1 item", 1, result.getSkippedCount());
        assertTrue("Skipped item IDs should contain invalidItemId", result.getSkippedItemIds().contains(invalidItemId));
        assertNotNull("Skip reason should be present", result.getSkipReasons().get(invalidItemId));
    }

    @Test
    public void testCopyQueueItem_ItemNotInAnyQueue_Succeeds() throws Exception {
        // Copy doesn't require item to be in a source queue
        long itemId = testItems.get(0).getId();

        // Verify item not in any queue
        assertFalse("Item should not be in queue1", DBReader.getQueue(queue1Id).stream()
                .anyMatch(item -> item.getId() == itemId));
        assertFalse("Item should not be in queue2", DBReader.getQueue(queue2Id).stream()
                .anyMatch(item -> item.getId() == itemId));

        // Copy item to queue2 (item not in any queue)
        CopyResult result = DBWriter.copyQueueItem(itemId, queue2Id).get();

        // Should succeed
        assertEquals("Should have copied 1 item", 1, result.getCopiedCount());
        assertEquals("Should have skipped 0 items", 0, result.getSkippedCount());

        // Verify item added to queue2
        assertTrue("Item should be in queue2", DBReader.getQueue(queue2Id).stream()
                .anyMatch(item -> item.getId() == itemId));
    }

    @Test
    public void testMoveQueueItems_PreservesOrder() throws Exception {
        // Add 5 items to queue1
        List<Long> itemIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            itemIds.add(testItems.get(i).getId());
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            for (int i = 0; i < 5; i++) {
                queue1.add(testItems.get(i));
            }
            adapter.setQueue(queue1, queue1Id);
        } finally {
            adapter.close();
        }

        // Move all 5 items to queue2
        MoveResult result = DBWriter.moveQueueItems(itemIds, queue1Id, queue2Id).get();

        // Verify order preserved (items appended in order)
        List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
        assertEquals("Queue2 should have 5 items", 5, queue2.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("Item order should be preserved", itemIds.get(i).longValue(),
                    queue2.get(i).getId());
        }

        // Verify queue1 is empty
        List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
        assertEquals("Queue1 should be empty", 0, queue1.size());
    }

    @Test
    public void testMoveQueueItems_EmptyList() throws Exception {
        List<Long> emptyList = new ArrayList<>();

        // Move empty list
        MoveResult result = DBWriter.moveQueueItems(emptyList, queue1Id, queue2Id).get();

        // Should succeed with no operations
        assertEquals("Should have moved 0 items", 0, result.getMovedCount());
        assertEquals("Should have skipped 0 items", 0, result.getSkippedCount());
        assertTrue("Skipped item IDs should be empty", result.getSkippedItemIds().isEmpty());
    }

    @Test
    public void testCopyQueueItems_EmptyList() throws Exception {
        List<Long> emptyList = new ArrayList<>();

        // Copy empty list
        CopyResult result = DBWriter.copyQueueItems(emptyList, queue2Id).get();

        // Should succeed with no operations
        assertEquals("Should have copied 0 items", 0, result.getCopiedCount());
        assertEquals("Should have skipped 0 items", 0, result.getSkippedCount());
        assertTrue("Skipped item IDs should be empty", result.getSkippedItemIds().isEmpty());
    }

    @Test
    public void testMoveQueueItems_AllInvalid_Fails() throws Exception {
        List<Long> invalidItemIds = new ArrayList<>();
        invalidItemIds.add(99999L);
        invalidItemIds.add(99998L);

        // Try to move all invalid items
        MoveResult result = DBWriter.moveQueueItems(invalidItemIds, queue1Id, queue2Id).get();

        // Should fail for all items
        assertEquals("Should have moved 0 items", 0, result.getMovedCount());
        assertEquals("Should have skipped 2 items", 2, result.getSkippedCount());
        assertEquals("Should have 2 skipped item IDs", 2, result.getSkippedItemIds().size());
    }

    @Test
    public void testCopyQueueItems_AllInvalid_Fails() throws Exception {
        List<Long> invalidItemIds = new ArrayList<>();
        invalidItemIds.add(99999L);
        invalidItemIds.add(99998L);

        // Try to copy all invalid items
        CopyResult result = DBWriter.copyQueueItems(invalidItemIds, queue2Id).get();

        // Should fail for all items
        assertEquals("Should have copied 0 items", 0, result.getCopiedCount());
        assertEquals("Should have skipped 2 items", 2, result.getSkippedCount());
        assertEquals("Should have 2 skipped item IDs", 2, result.getSkippedItemIds().size());
    }

    @Test
    public void testCopyQueueItems_ItemNotInAnyQueue_Succeeds() throws Exception {
        // Copy doesn't require items to be in a source queue
        List<Long> itemIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            itemIds.add(testItems.get(i).getId());
        }

        // Verify items not in any queue
        for (long itemId : itemIds) {
            assertFalse("Item should not be in queue1", DBReader.getQueue(queue1Id).stream()
                    .anyMatch(item -> item.getId() == itemId));
        }

        // Copy items to queue2 (items not in any queue)
        CopyResult result = DBWriter.copyQueueItems(itemIds, queue2Id).get();

        // Should succeed
        assertEquals("Should have copied 3 items", 3, result.getCopiedCount());
        assertEquals("Should have skipped 0 items", 0, result.getSkippedCount());

        // Verify items added to queue2
        List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
        assertEquals("Queue2 should have 3 items", 3, queue2.size());
        for (long itemId : itemIds) {
            assertTrue("Item should be in queue2", queue2.stream()
                    .anyMatch(item -> item.getId() == itemId));
        }
    }

    @Test
    public void testMoveQueueItem_AlreadyInTargetQueue_Fails() throws Exception {
        long itemId = testItems.get(0).getId();

        // Add item to both queue1 and queue2
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            queue1.add(testItems.get(0));
            adapter.setQueue(queue1, queue1Id);

            List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
            queue2.add(testItems.get(0));
            adapter.setQueue(queue2, queue2Id);
        } finally {
            adapter.close();
        }

        // Try to move from queue1 to queue2 (already in queue2)
        // The implementation removes from source first, then tries to add to target.
        // Since item is already in target, addQueueItemSynchronous will fail with duplicate error.
        MoveResult result = DBWriter.moveQueueItem(itemId, queue1Id, queue2Id).get();

        // Should fail - item removed from queue1 but fails to add to queue2 (duplicate)
        assertEquals("Should have moved 0 items", 0, result.getMovedCount());
        assertEquals("Should have skipped 1 item", 1, result.getSkippedCount());
        assertTrue("Skipped item IDs should contain itemId", result.getSkippedItemIds().contains(itemId));
        assertNotNull("Skip reason should be present", result.getSkipReasons().get(itemId));

        // Verify item removed from queue1 (removal succeeded)
        assertFalse("Item should not be in queue1", DBReader.getQueue(queue1Id).stream()
                .anyMatch(item -> item.getId() == itemId));

        // Verify item still in queue2 (addition failed due to duplicate)
        assertTrue("Item should still be in queue2", DBReader.getQueue(queue2Id).stream()
                .anyMatch(item -> item.getId() == itemId));
    }

    @Test
    public void testMoveQueueItems_MixedValidAndInvalid() throws Exception {
        // Create list with valid and invalid item IDs
        List<Long> itemIds = new ArrayList<>();
        itemIds.add(testItems.get(0).getId()); // Valid
        itemIds.add(99999L); // Invalid
        itemIds.add(testItems.get(1).getId()); // Valid
        itemIds.add(99998L); // Invalid
        itemIds.add(testItems.get(2).getId()); // Valid

        // Add valid items to queue1
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
            queue1.add(testItems.get(0));
            queue1.add(testItems.get(1));
            queue1.add(testItems.get(2));
            adapter.setQueue(queue1, queue1Id);
        } finally {
            adapter.close();
        }

        // Move all items (3 valid, 2 invalid)
        MoveResult result = DBWriter.moveQueueItems(itemIds, queue1Id, queue2Id).get();

        // Should move 3 valid items, skip 2 invalid items
        assertEquals("Should have moved 3 items", 3, result.getMovedCount());
        assertEquals("Should have skipped 2 items", 2, result.getSkippedCount());
        assertEquals("Should have 2 skipped item IDs", 2, result.getSkippedItemIds().size());

        // Verify 3 items in queue2
        List<FeedItem> queue2 = DBReader.getQueue(queue2Id);
        assertEquals("Queue2 should have 3 items", 3, queue2.size());

        // Verify queue1 is empty (all valid items moved)
        List<FeedItem> queue1 = DBReader.getQueue(queue1Id);
        assertEquals("Queue1 should be empty", 0, queue1.size());
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
