# Developer Quickstart: Multiple Queues Database Schema

**Feature**: Multiple Queues Database Layer
**Status**: Planning Complete - Ready for Implementation
**Related Docs**: [spec.md](./spec.md) | [data-model.md](./data-model.md) | [plan.md](./plan.md)

## Quick Reference

### What This Feature Adds

- **New Table**: `QueueMetadata` - stores queue metadata (name, color, sort order, currently playing)
- **Modified Table**: `Queue` - adds `queue_id` column to associate episodes with specific queues
- **Migration**: Database version 3080000 → 3080100
- **API Changes**: All DBReader/DBWriter queue methods gain optional `queueId` parameter

### Key Design Decisions

1. **Table naming**: `QueueMetadata` (not "Queues") for clarity
2. **Active queue**: Stored in SharedPreferences as `PREF_CURRENT_QUEUE_ID`
3. **Currently playing**: Moved from SharedPreferences to QueueMetadata table (per-queue)
4. **Primary key**: Keep existing `Queue.id` as PRIMARY KEY, use UNIQUE constraint on (queue_id, id)
5. **Foreign keys**: Enable `PRAGMA foreign_keys=ON` for data integrity
6. **Indexes**: Composite index on (queue_id, id) for optimal query performance

## Implementation Checklist

### Phase 1: Database Schema (storage/database module)

- [ ] **PodDBAdapter.java**
  - [ ] Add `TABLE_NAME_QUEUE_METADATA` constant
  - [ ] Add `CREATE_TABLE_QUEUE_METADATA` SQL string
  - [ ] Override `onConfigure()` to enable foreign keys
  - [ ] Add column constants for QueueMetadata fields

- [ ] **DBUpgrader.java**
  - [ ] Add case for version 3080100
  - [ ] Execute 001-create-queue-metadata-table.sql
  - [ ] Execute 002-alter-queue-add-queue-id.sql
  - [ ] Execute 003-migrate-data.sql with SharedPreferences migration
  - [ ] Test migration with existing and fresh databases

- [ ] **DBReader.java**
  - [ ] Add `getAllQueues()` - returns List<QueueMetadata>
  - [ ] Add `getQueueMetadataById(long queueId)` - returns QueueMetadata
  - [ ] Modify `getQueue()` to `getQueue(long queueId)` with default to current queue
  - [ ] Add overloaded `getQueue()` for backward compatibility

- [ ] **DBWriter.java**
  - [ ] Add `createQueue(String name, int color)` - returns new queue ID
  - [ ] Add `renameQueue(long queueId, String newName)`
  - [ ] Add `deleteQueue(long queueId)` - moves episodes to inbox, validates not last queue
  - [ ] Add `reorderQueues(List<Long> queueIds)` - updates sort_order
  - [ ] Add `setCurrentlyPlaying(long queueId, long feedMediaId, long feedId)`
  - [ ] Modify `addQueueItem()` to accept optional queueId parameter
  - [ ] Modify `removeQueueItem()` to accept optional queueId parameter
  - [ ] Update all methods to post QueueEvent on changes

### Phase 2: Domain Model (model module)

- [ ] **QueueMetadata.java**
  - [ ] Create domain object with all fields from data-model.md
  - [ ] Add getters/setters
  - [ ] Add `NO_MEDIA_PLAYING = -1` constant
  - [ ] Implement equals/hashCode based on id

- [ ] **QueueMetadataMapper.java** (storage/database/mapper)
  - [ ] Create cursor-based mapper for QueueMetadata
  - [ ] Follow existing mapper patterns (e.g., FeedItemMapper)

### Phase 3: Preferences (storage/preferences module)

- [ ] **QueuePreferences.java** (NEW)
  - [ ] Add `getCurrentQueueId()` - reads PREF_CURRENT_QUEUE_ID, defaults to 1
  - [ ] Add `setCurrentQueueId(long queueId)` - writes to SharedPreferences
  - [ ] Add preference key constants

- [ ] **PlaybackPreferences.java** (MODIFY)
  - [ ] Remove PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID references
  - [ ] Remove PREF_CURRENTLY_PLAYING_FEED_ID references
  - [ ] Update documentation to reference QueueMetadata table

### Phase 4: Events (event module)

- [ ] **QueueEvent.java**
  - [ ] Add queue switching event types
  - [ ] Add queue metadata change event types
  - [ ] Include queue ID and action type in events

### Phase 5: Testing

- [ ] **QueueMigrationTest.java** - test migration scenarios
- [ ] **DBReaderQueueTest.java** - test queue reading operations
- [ ] **DBWriterQueueTest.java** - test queue writing operations
- [ ] **QueueIntegrityTest.java** - test constraints and invariants

## Local Development Setup

### Prerequisites

```bash
# Verify Android SDK installed
echo $ANDROID_HOME

# Verify Gradle wrapper
./gradlew --version

# Install pre-commit hooks (optional but recommended)
pre-commit install
```

### Running Database Tests

```bash
# Run all database tests
./gradlew :storage:database:testDebugUnitTest

# Run specific test class
./gradlew :storage:database:testDebugUnitTest --tests "de.danoeh.antennapod.storage.database.DBReaderQueueTest"

# Run with logging enabled
./gradlew :storage:database:testDebugUnitTest --info
```

### Testing Migration Locally

#### Test 1: Existing User (has queue data)

```java
@Test
public void testMigration_existingUser_preservesData() {
    // Setup: Create database at version 3080000 with queue items
    PodDBAdapter adapter = PodDBAdapter.getInstance();
    adapter.open();
    SQLiteDatabase db = adapter.db();

    // Insert test queue items
    db.execSQL("INSERT INTO Queue (id, feeditem, feed) VALUES (0, 101, 1)");
    db.execSQL("INSERT INTO Queue (id, feeditem, feed) VALUES (1, 102, 1)");

    // Set SharedPreferences
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit()
        .putLong("prefCurrentlyPlayingFeedMedia", 42)
        .putLong("prefCurrentlyPlayingFeed", 7)
        .apply();

    adapter.close();

    // Trigger migration by opening at new version
    PodDBAdapter.deleteDatabase();
    adapter = PodDBAdapter.getInstance();
    adapter.open();

    // Verify: Queue items preserved with queue_id=1
    List<FeedItem> queue = DBReader.getQueue(1);
    assertEquals(2, queue.size());

    // Verify: QueueMetadata created with migrated values
    QueueMetadata mainQueue = DBReader.getQueueMetadataById(1);
    assertEquals("Main", mainQueue.getName());
    assertEquals(42, mainQueue.getCurrentlyPlayingFeedMediaId());
    assertEquals(7, mainQueue.getCurrentlyPlayingFeedId());

    // Verify: PREF_CURRENT_QUEUE_ID set
    assertEquals(1, QueuePreferences.getCurrentQueueId());

    // Verify: Old preferences removed
    assertFalse(prefs.contains("prefCurrentlyPlayingFeedMedia"));

    adapter.close();
    DBWriter.tearDownTests(); // CRITICAL: Prevents "Illegal connection pointer" errors
}
```

#### Test 2: Fresh Install (no queue data)

```java
@Test
public void testMigration_freshInstall_createsDefaultQueue() {
    PodDBAdapter adapter = PodDBAdapter.getInstance();
    adapter.open();

    // Verify: QueueMetadata has 1 row (Main queue)
    List<QueueMetadata> queues = DBReader.getAllQueues();
    assertEquals(1, queues.size());
    assertEquals("Main", queues.get(0).getName());
    assertEquals(0, queues.get(0).getSortOrder());

    // Verify: Queue table empty but has queue_id column
    List<FeedItem> queue = DBReader.getQueue(1);
    assertEquals(0, queue.size());

    adapter.close();
    DBWriter.tearDownTests();
}
```

### Verifying Backward Compatibility

```java
@Test
public void testBackwardCompatibility_oldMethodSignatures() {
    // Setup: Create queue with items
    DBWriter.createQueue("Test Queue", 0xFFAA3344).get();
    FeedItem item = createTestFeedItem();

    // Test: Call old method signature (no queueId parameter)
    DBWriter.addQueueItem(context, item).get();

    // Verify: Item added to current active queue
    List<FeedItem> queue = DBReader.getQueue(); // Old signature
    assertTrue(queue.contains(item));

    // Cleanup
    DBWriter.clearQueue(context).get();
    DBWriter.tearDownTests();
}
```

## Example Usage (After Implementation)

### Creating a New Queue

```java
// Create queue with name and color
Future<Long> future = DBWriter.createQueue("Workout", 0xFF3344AA);
long queueId = future.get(); // Returns new queue ID

// Queue is automatically created with:
// - sort_order = max(sort_order) + 1
// - created_at = current timestamp
// - currently_playing_* = -1 (no media)
```

### Adding Episodes to Specific Queue

```java
// Add to specific queue
DBWriter.addQueueItem(context, feedItem, queueId);

// Add to current active queue (backward compatible)
DBWriter.addQueueItem(context, feedItem);
```

### Switching Active Queue

```java
// Switch to different queue
QueuePreferences.setCurrentQueueId(queueId);

// Post event for UI to react
EventBus.getDefault().post(new QueueEvent(QueueEvent.Action.SWITCHED, queueId));

// Load new queue
List<FeedItem> newQueue = DBReader.getQueue(queueId);
```

### Deleting a Queue

```java
// Delete queue (episodes move to inbox)
DBWriter.deleteQueue(context, queueId).get();

// Note: Last queue cannot be deleted (enforced in DBWriter)
if (DBReader.getAllQueues().size() == 1) {
    // Show error: "Cannot delete last queue"
    return;
}
```

## Performance Testing

### Query Performance Test

```java
@Test
public void testQueryPerformance_1000Episodes() {
    // Setup: Create queue with 1000 episodes
    long queueId = DBWriter.createQueue("Performance Test", 0xFFAA3344).get();
    for (int i = 0; i < 1000; i++) {
        FeedItem item = createTestFeedItem();
        DBWriter.addQueueItem(context, item, queueId).get();
    }

    // Test: Measure query time
    long startTime = System.currentTimeMillis();
    List<FeedItem> queue = DBReader.getQueue(queueId);
    long duration = System.currentTimeMillis() - startTime;

    // Verify: Query completes in <100ms
    assertTrue("Query took " + duration + "ms, expected <100ms", duration < 100);
    assertEquals(1000, queue.size());

    // Cleanup
    DBWriter.deleteQueue(context, queueId).get();
    DBWriter.tearDownTests();
}
```

### Verify Index Usage

```sql
-- Run in adb shell sqlite3
EXPLAIN QUERY PLAN
SELECT FeedItems.*, Queue.id as queue_position
FROM Queue
INNER JOIN FeedItems ON Queue.feeditem = FeedItems.id
WHERE Queue.queue_id = 1
ORDER BY Queue.id ASC;

-- Expected output:
-- SEARCH Queue USING INDEX idx_queue_queue_id_id (queue_id=?)
-- SEARCH FeedItems USING INTEGER PRIMARY KEY (rowid=?)
```

## Debugging Tips

### Common Issues

**Issue**: "Illegal connection pointer" errors in tests
**Solution**: Always call `DBWriter.tearDownTests()` in `@After` method

**Issue**: Migration fails silently
**Solution**: Enable SQL logging: `adb shell setprop log.tag.SQLiteDatabase DEBUG`

**Issue**: Foreign key constraint violations
**Solution**: Verify `PRAGMA foreign_keys=ON` in `PodDBAdapter.onConfigure()`

**Issue**: Query using wrong index
**Solution**: Run `EXPLAIN QUERY PLAN` and verify composite index used

### Enabling Debug Logging

```java
// In DBUpgrader.java, add logging
@Override
public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.d("DBUpgrader", "Upgrading from " + oldVersion + " to " + newVersion);

    if (oldVersion < 3080100) {
        Log.d("DBUpgrader", "Applying migration 3080100: Multiple queues");
        // ... migration code
        Log.d("DBUpgrader", "Migration 3080100 complete");
    }
}
```

### Inspecting Database During Tests

```java
// Dump QueueMetadata table
Cursor cursor = db.rawQuery("SELECT * FROM QueueMetadata", null);
while (cursor.moveToNext()) {
    Log.d("Test", "Queue: id=" + cursor.getLong(0) +
                  ", name=" + cursor.getString(1) +
                  ", color=" + cursor.getInt(2));
}
cursor.close();
```

## Review Checklist Before Submitting PR

- [ ] All new code passes Checkstyle (zero violations)
- [ ] All new code passes SpotBugs (zero medium/high bugs)
- [ ] All new code passes Android Lint (zero errors)
- [ ] Unit tests written for all new DBReader/DBWriter methods
- [ ] Migration tests pass (existing user + fresh install scenarios)
- [ ] Backward compatibility tests pass
- [ ] Performance tests pass (all operations <100ms)
- [ ] Database constraints verified (foreign keys, unique indexes)
- [ ] EventBus events posted on all queue operations
- [ ] JavaDoc added to all public methods
- [ ] Constitution principles verified (all ✅ PASS)
- [ ] `DBWriter.tearDownTests()` called in all test teardowns

## Next Steps After Database Layer Complete

Once this database layer is implemented and tested:

1. **UI Layer** (Phase 2 - separate spec):
   - Queue switcher bottom sheet
   - Queue management screen (create/rename/delete/reorder)
   - Visual indicators for active queue

2. **Playback Integration** (Phase 3 - separate spec):
   - Queue switching pauses playback
   - Resume last-played episode when switching back
   - Update currently_playing_* in QueueMetadata

3. **Advanced Features** (Future):
   - Smart queue auto-assignment rules
   - Per-queue playback speed overrides
   - Queue synchronization via gpodder.net

## Getting Help

- **Codebase questions**: See [CLAUDE.md](../../../CLAUDE.md) for architecture overview
- **Testing issues**: See existing tests in `storage/database/src/test/`
- **Constitution violations**: See [.specify/memory/constitution.md](../../../.specify/memory/constitution.md)
- **Spec clarifications**: See [spec.md](./spec.md) and [data-model.md](./data-model.md)
