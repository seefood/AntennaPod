package de.danoeh.antennapod.storage.database;

import android.content.Context;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Verifies that concurrent calls to queue-mutation methods (T046, T049) do not
 * corrupt queue state.
 *
 * <p>DBWriter serialises all writes through a single-threaded ExecutorService
 * ("DatabaseExecutor"). Submitting many concurrent {@code moveQueueItem} calls
 * from multiple threads must result in a queue that still contains the original
 * set of item IDs — no duplicates, no losses.
 *
 * <p>T049: also asserts that {@code moveQueueItem} does not change the set of
 * items in the queue (it only reorders, never adds/removes).
 */
@RunWith(RobolectricTestRunner.class)
public class AsyncOperationAtomicityTest {
    private static final int QUEUE_SIZE = 10;
    private static final int CONCURRENT_MOVES = 100;

    private Context context;
    private List<Long> originalItemIds;

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

        originalItemIds = populateQueue(QUEUE_SIZE);
    }

    @After
    public void tearDown() throws Exception {
        DBWriter.tearDownTests();
    }

    /**
     * T046: Submit {@value #CONCURRENT_MOVES} concurrent moveQueueItem futures from a thread pool.
     * After all complete, the queue must still hold the same {@value #QUEUE_SIZE} distinct
     * item IDs that were there originally — no duplicates, no losses.
     */
    @Test
    public void testConcurrentMoveQueueItemPreservesQueueIntegrity() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);

        // Each caller thread submits one moveQueueItem and returns the inner DB Future.
        List<Future<Future<?>>> callerFutures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_MOVES; i++) {
            final int idx = i;
            callerFutures.add(callers.submit(() -> {
                startGate.await(); // burst all at once
                int from = idx % QUEUE_SIZE;
                int to = (idx + 1) % QUEUE_SIZE;
                return DBWriter.moveQueueItem(from, to, false);
            }));
        }

        // Release all caller threads simultaneously.
        startGate.countDown();

        // Collect and wait for every DB task.
        for (Future<Future<?>> cf : callerFutures) {
            Future<?> dbFuture = cf.get(10, TimeUnit.SECONDS);
            if (dbFuture != null) {
                dbFuture.get(10, TimeUnit.SECONDS);
            }
        }

        callers.shutdown();

        // Assert queue integrity: same count, same set of IDs.
        List<FeedItem> queue = DBReader.getQueue();
        assertEquals("Queue size must not change after concurrent moves",
                QUEUE_SIZE, queue.size());

        Set<Long> remainingIds = new HashSet<>();
        for (FeedItem item : queue) {
            remainingIds.add(item.getId());
        }
        assertEquals("No item IDs must be duplicated or lost after concurrent moves",
                new HashSet<>(originalItemIds), remainingIds);
    }

    /**
     * T049: moveQueueItem is a pure reorder — it must never add or remove items.
     * Verified by checking queue size and ID set are unchanged after repeated moves.
     */
    @Test
    public void testMoveQueueItemOnlyReordersNeverAddsOrRemoves() throws Exception {
        // Shuttle the first item back and forth many times.
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(DBWriter.moveQueueItem(0, QUEUE_SIZE - 1, false));
            futures.add(DBWriter.moveQueueItem(QUEUE_SIZE - 1, 0, false));
        }
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }

        List<FeedItem> queue = DBReader.getQueue();
        assertEquals("Queue size must be unchanged after moves", QUEUE_SIZE, queue.size());

        Set<Long> remainingIds = new HashSet<>();
        for (FeedItem item : queue) {
            remainingIds.add(item.getId());
        }
        assertEquals("Queue item IDs must be unchanged after moves",
                new HashSet<>(originalItemIds), remainingIds);
    }

    // ---- helpers ----

    /**
     * Creates a feed with {@code count} items, enqueues all of them, and returns
     * their IDs in queue order.
     */
    private List<Long> populateQueue(int count) throws Exception {
        Feed feed = new Feed("http://atomicity-test.example", null, "Atomicity Test Feed");
        feed.setItems(new ArrayList<>());
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        List<FeedItem> items = new ArrayList<>();
        adapter = PodDBAdapter.getInstance();
        adapter.open();
        for (int i = 0; i < count; i++) {
            FeedItem item = new FeedItem(0, "Item-" + i,
                    "guid-atomicity-" + i,
                    "http://link/" + i, new Date(i * 10000L),
                    FeedItem.UNPLAYED, feed);
            adapter.setSingleFeedItem(item);
            items.add(item);
        }
        adapter.close();

        // Populate the queue directly via adapter (avoids PlaybackPreferences dependency).
        adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setQueue(items);
        adapter.close();

        List<Long> ids = new ArrayList<>();
        for (FeedItem item : DBReader.getQueue()) {
            ids.add(item.getId());
        }
        assertEquals("Setup: queue must contain exactly count items", count, ids.size());
        return ids;
    }
}
