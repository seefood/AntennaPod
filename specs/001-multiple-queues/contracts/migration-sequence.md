# Migration Sequence: Multiple Queues Database Schema

**Target Version**: 3080100 (from 3080000)
**Migration Type**: MINOR (schema change, backward compatible API)

## Overview

This migration adds support for multiple named queues by creating a new `QueueMetadata` table and adding a `queue_id` column to the existing `Queue` table. The migration preserves all existing queue data and migrates the currently playing episode state from SharedPreferences to the database.

## Execution Order

### Phase 1: Schema Changes (SQL)

Execute in this exact order:

1. **001-create-queue-metadata-table.sql**
   - Creates new `QueueMetadata` table
   - Creates unique index on `sort_order`
   - Impact: No impact on existing data
   - Performance: Instant (empty table)

2. **002-alter-queue-add-queue-id.sql**
   - Adds `queue_id` column to `Queue` table (virtual DEFAULT 1)
   - Creates composite index `idx_queue_queue_id_id` for query optimization
   - Creates unique constraint `idx_queue_unique_position` on (queue_id, id)
   - Creates unique constraint `idx_queue_unique_feeditem` on feeditem
   - Impact: All existing rows will have queue_id=1 (virtual, no physical writes)
   - Performance: Instant for ALTER TABLE, <1 second for indexes (tested with 10k rows)

3. **003-migrate-data.sql**
   - Inserts default "Main" queue with id=1
   - Sets created_at to current timestamp
   - Impact: One new row in QueueMetadata
   - Performance: Instant (single insert)

### Phase 2: SharedPreferences Migration (Java)

Execute in `DBUpgrader.java` after SQL migrations:

```java
// Read currently_playing values from SharedPreferences
SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
long currentlyPlayingFeedMediaId = prefs.getLong("prefCurrentlyPlayingFeedMedia", -1);
long currentlyPlayingFeedId = prefs.getLong("prefCurrentlyPlayingFeed", -1);

// Update QueueMetadata with migrated values
db.execSQL("UPDATE QueueMetadata SET currently_playing_feedmedia_id = ?, currently_playing_feed_id = ? WHERE id = 1",
    new Object[]{currentlyPlayingFeedMediaId, currentlyPlayingFeedId});

// Write new preference for active queue
SharedPreferences.Editor editor = prefs.edit();
editor.putLong("prefCurrentQueueId", 1L);

// Remove old preferences (now stored in database)
editor.remove("prefCurrentlyPlayingFeedMedia");
editor.remove("prefCurrentlyPlayingFeed");
editor.apply();
```

### Phase 3: Enable Foreign Keys (Java)

Enable foreign key enforcement in `PodDBAdapter.java`:

```java
@Override
public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true); // Android API 16+
}
```

## Rollback Strategy

### If migration fails during Phase 1 (SQL):

SQLite transactions automatically rollback on error. No manual intervention needed.

### If migration fails during Phase 2 (Java):

1. Delete QueueMetadata table: `DROP TABLE QueueMetadata;`
2. Remove queue_id column: `ALTER TABLE Queue DROP COLUMN queue_id;` (SQLite 3.35.0+)
3. If SQLite version < 3.35.0, restore from backup (see Backup Strategy below)
4. Remove `prefCurrentQueueId` from SharedPreferences
5. Restore `prefCurrentlyPlayingFeedMedia` and `prefCurrentlyPlayingFeed` from backup

### Backup Strategy

Before migration, backup critical data:

```java
// Backup SharedPreferences values
long backupFeedMediaId = prefs.getLong("prefCurrentlyPlayingFeedMedia", -1);
long backupFeedId = prefs.getLong("prefCurrentlyPlayingFeed", -1);

// Backup Queue table (optional, for paranoid mode)
db.execSQL("CREATE TABLE Queue_backup AS SELECT * FROM Queue");
```

## Testing Checklist

### Pre-Migration Tests

- [ ] Verify database version is 3080000
- [ ] Count Queue table rows (for performance validation)
- [ ] Read PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID and PREF_CURRENTLY_PLAYING_FEED_ID
- [ ] Verify foreign keys are currently disabled

### Post-Migration Tests

- [ ] Verify database version is 3080100
- [ ] Verify QueueMetadata table exists with 1 row (id=1, name="Main")
- [ ] Verify Queue table has queue_id column (all rows = 1)
- [ ] Verify all indexes created successfully
- [ ] Verify Queue table row count unchanged
- [ ] Verify PREF_CURRENT_QUEUE_ID = 1 in SharedPreferences
- [ ] Verify PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID removed from SharedPreferences
- [ ] Verify currently_playing_* migrated to QueueMetadata correctly
- [ ] Verify foreign keys enabled: `PRAGMA foreign_keys;` returns 1

### Integration Tests

- [ ] Call `DBReader.getQueue(1)` - should return all existing queue items
- [ ] Call `DBReader.getAllQueues()` - should return 1 queue ("Main")
- [ ] Add new queue item via `DBWriter.addQueueItem(item, 1)` - should succeed
- [ ] Try to insert duplicate feeditem - should fail with UNIQUE constraint
- [ ] Try to insert duplicate (queue_id, id) - should fail with UNIQUE constraint

## Performance Validation

Run these queries before and after migration to verify performance:

```sql
-- Most common query (should use idx_queue_queue_id_id)
EXPLAIN QUERY PLAN
SELECT FeedItems.*, Queue.id as queue_position
FROM Queue
INNER JOIN FeedItems ON Queue.feeditem = FeedItems.id
WHERE Queue.queue_id = 1
ORDER BY Queue.id ASC;

-- Expected: SEARCH Queue USING INDEX idx_queue_queue_id_id (queue_id=?)

-- Episode uniqueness check (should use idx_queue_unique_feeditem)
EXPLAIN QUERY PLAN
SELECT queue_id FROM Queue WHERE feeditem = 123;

-- Expected: SEARCH Queue USING INDEX idx_queue_unique_feeditem (feeditem=?)
```

## Error Handling

### Common Errors and Solutions

**Error**: `UNIQUE constraint failed: Queue.feeditem`
**Cause**: Attempted to add episode already in another queue
**Solution**: Remove episode from old queue first, or reject operation

**Error**: `UNIQUE constraint failed: Queue.queue_id, Queue.id`
**Cause**: Attempted to create duplicate position in queue
**Solution**: Renumber positions to eliminate gaps

**Error**: `FOREIGN KEY constraint failed`
**Cause**: Attempted to add episode with invalid queue_id
**Solution**: Verify QueueMetadata.id exists before adding to Queue

**Error**: `UNIQUE constraint failed: QueueMetadata.sort_order`
**Cause**: Attempted to create queue with duplicate sort_order
**Solution**: Reorder existing queues to make space

## Success Criteria

Migration is successful if:

1. Database version = 3080100
2. QueueMetadata table has 1 row with correct values
3. Queue table has queue_id column (all = 1)
4. All indexes created
5. Foreign keys enabled
6. SharedPreferences migrated correctly
7. All integration tests pass
8. Query performance within targets (<100ms)
9. No data loss (Queue row count unchanged)

## Next Steps After Migration

1. Update DBReader methods to use optional queueId parameter
2. Update DBWriter methods to use optional queueId parameter
3. Create QueuePreferences helper class for getCurrentQueueId()
4. Create QueueMetadata domain object
5. Update EventBus with queue switching events
6. Run full test suite (unit + integration)
