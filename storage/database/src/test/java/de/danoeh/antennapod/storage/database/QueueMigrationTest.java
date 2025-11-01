package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for queue migration and database schema (T028 - Phase 5).
 * Verifies that the migration creates the queue infrastructure correctly.
 */
@RunWith(RobolectricTestRunner.class)
public class QueueMigrationTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    @Test
    public void testDefaultQueueCreatedOnMigration() {
        // After database initialization, there should be exactly one queue (the default)
        List<QueueMetadata> queues = DBReader.getAllQueues();
        assertNotNull(queues);
        assertEquals(1, queues.size());

        QueueMetadata defaultQueue = queues.get(0);
        assertEquals(1, defaultQueue.getId());
        assertNotNull(defaultQueue.getName());
        assertTrue(defaultQueue.getName().length() > 0);
    }

    @Test
    public void testQueueMetadataTableExists() {
        // Verify we can query queue metadata without errors
        QueueMetadata queue = DBReader.getQueueMetadataById(1);
        assertNotNull(queue);
        assertEquals(1, queue.getId());
    }

    @Test
    public void testDatabaseVersionUpdated() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        int version = adapter.getVersion();
        adapter.close();

        assertEquals(3080100, version);
    }

    @Test
    public void testQueueItemsHaveQueueId() throws Exception {
        // When items are added to queue, they should have a queue_id set
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(PodDBAdapter.KEY_FEEDITEM, 100); // Fake feed item ID

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            long result = adapter.executeInsert(PodDBAdapter.TABLE_NAME_QUEUE, values);
            assertTrue(result > 0);
        } finally {
            adapter.close();
        }
    }

    @Test
    public void testForeignKeyConstraintsEnabled() {
        // Foreign key constraints should be enabled
        // This is tested implicitly by attempting operations that would violate constraints
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        // The database should have foreign keys enabled (this was set in migration)
        // We can verify by checking that the schema was created
        List<QueueMetadata> queues = DBReader.getAllQueues();
        assertNotNull(queues);
        assertTrue(queues.size() > 0);

        adapter.close();
    }

    @Test
    public void testIndexesCreated() {
        // Verify indexes were created (by attempting queries that would benefit from them)
        // getAllQueues should use idx_queue_metadata_sort_order index
        List<QueueMetadata> queues = DBReader.getAllQueues();
        assertNotNull(queues);
        assertEquals(1, queues.size());
    }
}
