# Feature Specification: Multiple Queues - Database Schema

**Feature Branch**: `multiqueue/001-planning`
**Created**: 2025-10-31
**Status**: Draft - Database Design Phase
**Input**: Extend database schema to support multiple named queues

## Overview

This specification defines the database schema changes required to support multiple queues in AntennaPod. Currently, the `Queue` table stores a single ordered list of episodes. This design will add a new `QueueMetadata` table to support multiple named queues while keeping the existing `Queue` table structure mostly unchanged.

## Clarifications

### Session 2025-11-01

- Q: Should `addQueueItemAt()` (insert at specific position) be supported in multi-queue schema? → A: **Yes, preserve existing behavior**. Current code has `addQueueItemAt(context, itemId, index)` method. New multi-queue schema must support this with optional queueId parameter: `addQueueItemAt(context, itemId, index, queueId)` defaulting to current active queue.

- Q: Method naming for color change - `changeQueueColor()` vs `setQueueColor()`? → A: **Use `changeQueueColor()`** to match existing AntennaPod conventions (similar to `changeFeedFilter`, `changePlaybackSpeed`). Follow project naming patterns for consistency.

- Q: `moveEpisodeToQueue()` - require explicit source queue or infer from active queue? → A: **Infer from active queue**. Use 2-parameter signature: `moveQueueItem(FeedItem item, long toQueueId)`. The source queue is always the currently active queue (PREF_CURRENT_QUEUE_ID from SharedPreferences). Note: Same episode can exist in multiple queues simultaneously (no uniqueness constraint across all queues).

- Q: Episode uniqueness - can same episode appear in multiple queues? → A: **Yes, no uniqueness constraint**. Same episode (feeditem) can exist in multiple queues simultaneously. Only constraint is `(queue_id, id)` uniqueness for positions within a single queue. Remove `idx_queue_unique_feeditem` constraint; use regular index `idx_queue_feeditem` for lookups only.

## Current Database Schema

### Existing Queue Table (TABLE_NAME_QUEUE)

```sql
CREATE TABLE Queue (
    id INTEGER PRIMARY KEY,
    feeditem INTEGER,
    feed INTEGER
)
```

**Current behavior**:
- Single queue containing episode IDs
- `id` serves as position/order in the queue (0, 1, 2, 3...)
- `feeditem` references FeedItems.id
- `feed` references Feeds.id (redundant, can be derived from feeditem)

### Currently Playing Episode Tracking

The currently playing episode is stored in **SharedPreferences** (not database):

- `PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID` - The FeedMedia ID of currently playing episode
- `PREF_CURRENTLY_PLAYING_FEED_ID` - The Feed ID
- `PREF_CURRENT_PLAYER_STATUS` - Playing/paused/other status

See: `PlaybackPreferences.java` lines 26-46

## Proposed Database Schema

### New QueueMetadata Table (TABLE_NAME_QUEUE_METADATA)

Stores metadata about each queue.

```sql
CREATE TABLE QueueMetadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    color INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    currently_playing_feedmedia_id INTEGER DEFAULT -1,
    currently_playing_feed_id INTEGER DEFAULT -1
)
```

**Columns**:
- `id`: Unique queue identifier (auto-increment)
- `name`: User-defined queue name (e.g., "Workout", "Commute")
- `color`: RGB color value for queue visual identification (INTEGER, e.g., 0xFFAA3344)
- `created_at`: Unix timestamp when queue was created
- `sort_order`: User-defined order for displaying queues in UI (0-indexed)
- `currently_playing_feedmedia_id`: FeedMedia ID of currently playing episode in this queue, or -1 if none (migrated from SharedPreferences)
- `currently_playing_feed_id`: Feed ID of currently playing episode in this queue, or -1 if none (migrated from SharedPreferences)

**Constraints**:
- `name` MUST NOT be NULL
- `name` MUST NOT be empty string
- `color` MUST NOT be NULL (default color can be provided if user doesn't choose)
- `sort_order` values MUST be unique across all queues
- At least one queue MUST exist at all times

**Why store currently_playing_feedmedia_id and currently_playing_feed_id here?**
- Per-queue playback tracking allows resuming playback when switching between queues
- These values are migrated from SharedPreferences (PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, PREF_CURRENTLY_PLAYING_FEED_ID)
- Each queue remembers which episode was last playing, independent of other queues
- When user switches queues, playback pauses and the new queue's last-played episode is selected (but paused)

### SharedPreferences Addition

Add new preference to track active queue:

```java
private static final String PREF_CURRENT_QUEUE_ID = "de.danoeh.antennapod.preferences.currentQueueId";
```

- Stores which queue is currently active/selected
- Defaults to 1 (the default queue created during migration)
- Updated when user switches between queues

### Modified Queue Table (TABLE_NAME_QUEUE)

**Add ONE column** to existing table:

```sql
-- Add column to existing table (migration)
ALTER TABLE Queue ADD COLUMN queue_id INTEGER DEFAULT 1;

-- Add index for performance
CREATE INDEX Queue_queue_id ON Queue(queue_id);
```

**Updated structure**:
- `id`: Position within specific queue (0, 1, 2, 3... within each queue_id)
- `feeditem`: References FeedItems.id
- `feed`: References Feeds.id (kept for compatibility)
- `queue_id`: **NEW** - References QueueMetadata.id (which queue this item belongs to)

**Important**: The `id` column behavior changes:
- OLD: `id` is unique globally (single queue, values 0-N)
- NEW: `id` represents position within a specific queue (scoped by `queue_id`)
- Multiple rows can have same `id` value as long as `queue_id` differs
- Example:
  - Queue 1: items with id=0, id=1, id=2
  - Queue 2: items with id=0, id=1, id=2
  - This is VALID because queue_id differs

## Data Migration Strategy

### Migration for Existing Users

When upgrading from single queue to multiple queues:

1. **Migrate currently playing info from SharedPreferences to QueueMetadata**:
   ```java
   long currentlyPlayingFeedMediaId = prefs.getLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, -1);
   long currentlyPlayingFeedId = prefs.getLong(PREF_CURRENTLY_PLAYING_FEED_ID, -1);
   ```

2. **Create default queue in QueueMetadata**:
   ```sql
   INSERT INTO QueueMetadata (id, name, color, created_at, sort_order, currently_playing_feedmedia_id, currently_playing_feed_id)
   VALUES (1, 'Main', <default_color>, <current_timestamp>, 0, ?, ?);
   -- Use migrated values from step 1
   ```

3. **Update all existing Queue entries to belong to default queue**:
   ```sql
   UPDATE Queue SET queue_id = 1;
   ```

4. **Set default active queue in SharedPreferences**:
   ```java
   prefs.edit().putLong(PREF_CURRENT_QUEUE_ID, 1).apply();
   ```

5. **Remove old currently playing preferences from SharedPreferences**:
   ```java
   prefs.edit()
       .remove(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID)
       .remove(PREF_CURRENTLY_PLAYING_FEED_ID)
       .apply();
   ```

6. **Preserve existing queue order**: No changes to `Queue.id` values needed (they remain 0, 1, 2, 3...)

### Migration for New Users

Fresh installs:

1. Create default queue in QueueMetadata on first app launch
2. Set `PREF_CURRENT_QUEUE_ID = 1` in SharedPreferences
3. All new queue operations default to `queue_id = 1`

## Database Operations

### Create New Queue

```sql
-- Get next sort_order
SELECT COALESCE(MAX(sort_order) + 1, 0) FROM QueueMetadata;

-- Insert new queue
INSERT INTO QueueMetadata (name, color, created_at, sort_order, currently_playing_position)
VALUES (?, ?, ?, ?, -1);
```

### Rename Queue

```sql
UPDATE QueueMetadata SET name = ? WHERE id = ?;
```

### Change Queue Color

```sql
UPDATE QueueMetadata SET color = ? WHERE id = ?;
```

### Delete Queue

**UI Constraints**:
- Last remaining queue CANNOT be deleted (delete button hidden when only one queue exists)
- When deleting a queue with episodes, show confirmation dialog with options:
  - **Option A**: Move episodes to inbox (episodes remain in feed, removed from all queues)
  - **Option B**: Permanently remove episodes from queue (lose queue organization)

**Either way** (both options):
- Episode playback position is preserved (NOT reset)
- Episodes are NOT marked as played
- Last stopped position stays unchanged

```sql
-- Option A: Move to inbox (recommended)
DELETE FROM Queue WHERE queue_id = ?;

-- Option B: Same SQL (just different user understanding)
DELETE FROM Queue WHERE queue_id = ?;

-- Delete the queue metadata
DELETE FROM QueueMetadata WHERE id = ?;

-- If this was the active queue, switch to another queue
-- Update PREF_CURRENT_QUEUE_ID to a remaining queue
```

### Get All Queues

```sql
SELECT id, name, color, created_at, sort_order, currently_playing_position,
       (SELECT COUNT(*) FROM Queue WHERE queue_id = QueueMetadata.id) as episode_count
FROM QueueMetadata
ORDER BY sort_order ASC;
```

### Get Episodes for Specific Queue

```sql
SELECT FeedItems.*, Queue.id as queue_position
FROM Queue
INNER JOIN FeedItems ON Queue.feeditem = FeedItems.id
WHERE Queue.queue_id = ?
ORDER BY Queue.id ASC;
```

### Add Episode to Queue

```sql
-- Get next position in target queue
SELECT COALESCE(MAX(id) + 1, 0) FROM Queue WHERE queue_id = ?;

-- Insert episode
INSERT INTO Queue (id, feeditem, feed, queue_id)
VALUES (?, ?, ?, ?);
```

### Move Episode Between Queues

```sql
-- Check if episode exists in another queue
SELECT queue_id, id FROM Queue WHERE feeditem = ?;

-- Remove from old queue
DELETE FROM Queue WHERE feeditem = ?;

-- Get next position in new queue
SELECT COALESCE(MAX(id) + 1, 0) FROM Queue WHERE queue_id = ?;

-- Insert into new queue
INSERT INTO Queue (id, feeditem, feed, queue_id)
VALUES (?, ?, ?, ?);
```

### Update Currently Playing Episode in Queue

```sql
-- When user plays an episode, update that queue's currently playing episode
UPDATE QueueMetadata
SET currently_playing_feedmedia_id = ?, currently_playing_feed_id = ?
WHERE id = ?;
```

### Switch Active Queue

**Behavior when user selects a different queue**:

1. **Pause current playback**:
   - If something is playing, pause it
   - Save current position to QueueMetadata for the old queue

2. **Update active queue in SharedPreferences**:
   ```java
   prefs.edit().putLong(PREF_CURRENT_QUEUE_ID, newQueueId).apply();
   ```

3. **Load new queue in UI**:
   - Display episodes from new queue
   - Read `currently_playing_feedmedia_id` and `currently_playing_feed_id` from new queue's QueueMetadata

4. **Select last-played episode (paused state)**:
   - If `currently_playing_feedmedia_id != -1`, select that episode
   - Highlight it in the queue list
   - Episode is selected but playback remains PAUSED
   - User must press play to resume

5. **Post QueueEvent** via EventBus to notify UI components

## Database Constraints & Invariants

### Mandatory Constraints

1. **At least one queue exists**: System MUST maintain at least one queue in QueueMetadata
2. **Queue names are non-empty**: `QueueMetadata.name` cannot be NULL or empty string
3. **Colors are valid**: `QueueMetadata.color` cannot be NULL
4. **Unique sort orders**: No two queues share the same `sort_order` value
5. **No duplicate positions within queue**: Each `(queue_id, id)` pair MUST be unique (no duplicate positions within a single queue)
6. **Position continuity**: Within each `queue_id`, positions (Queue.id) MUST be continuous (0,1,2,3... with no gaps)
7. **Episodes can appear in multiple queues**: Same `feeditem` CAN appear in multiple queues (no uniqueness constraint across queues)

### Data Integrity Rules

1. **Cascade delete prevention**: Deleting a queue with episodes MUST be explicitly handled (move episodes to inbox)
2. **Foreign key integrity**: `Queue.queue_id` MUST reference valid `QueueMetadata.id`
3. **Feed consistency**: `Queue.feed` MUST match the feed of referenced `Queue.feeditem`
4. **Currently playing bounds**: `QueueMetadata.currently_playing_position` must be either -1 (no position) or a valid Queue.id for that queue_id
5. **Active queue exists**: `PREF_CURRENT_QUEUE_ID` in SharedPreferences must reference a valid QueueMetadata.id

## Backward Compatibility

### Reading Data

- Existing code that reads from `Queue` table will continue to work
- To maintain compatibility, filter by `queue_id = 1` to access default queue
- `DBReader` methods will need optional `queueId` parameter (defaulting to current queue from SharedPreferences)

### Writing Data

- Existing code that writes to `Queue` must be updated to include `queue_id`
- Default to current queue from `PREF_CURRENT_QUEUE_ID` in SharedPreferences
- `DBWriter` methods will need optional `queueId` parameter

### UI Compatibility

- Single queue UI remains functional by always operating on current queue
- Multi-queue UI features can be rolled out incrementally

## Database Version

- Current version: `3080000` (from PodDBAdapter.VERSION)
- Proposed new version: `3080100` (MINOR increment for schema addition)
- Migration will be handled in `DBUpgrader` class

## Key Refactoring Areas

The following existing methods will need minor refactoring to support multiple queues:

### DBReader Methods (add optional queueId parameter)

**Methods to refactor**:
- `getQueue()` → `getQueue(queueId)` with default queueId from SharedPreferences
- `getQueueIDList()` → `getQueueIDList(queueId)` with default queueId from SharedPreferences

**Existing calls continue to work**: Methods use current queue from SharedPreferences when queueId not specified

### DBWriter Methods (add optional queueId parameter)

**Methods to refactor**:
- `addQueueItem(item)` → `addQueueItem(item, queueId)` with default queueId from SharedPreferences
- `addQueueItemAt(item, index)` → `addQueueItemAt(item, index, queueId)` with default queueId from SharedPreferences
- `removeQueueItem(item)` → `removeQueueItem(item, queueId)` with default queueId from SharedPreferences

**Existing calls continue to work**: Methods use current queue from SharedPreferences when queueId not specified

### PlaybackPreferences Methods (location change - NO new methods)

**Currently playing position tracking** - These existing methods need refactoring to read/write from QueueMetadata table instead of SharedPreferences:

**Current location**: SharedPreferences (needs to be removed)
**New location**: QueueMetadata.currently_playing_position

**Methods to refactor** (change data source, keep same signature):
- Position tracking is currently implicit in queue order
- After refactoring: When playback starts/resumes, update QueueMetadata.currently_playing_position for the active queue
- When switching queues: Read currently_playing_position from QueueMetadata to know where to resume

**Implementation approach**:
- In `PlaybackService` or related playback code, when an episode starts playing from a queue:
  - Determine which queue it belongs to
  - Update `QueueMetadata.currently_playing_position` to the episode's position in that queue
- When user switches to a different queue:
  - Read `currently_playing_position` from QueueMetadata for that queue
  - Optionally highlight or auto-scroll to that position

### New Methods Needed

**DBReader**:
- `getAllQueues()` - Returns list of all queues with metadata (name, color, episode count, currently_playing_position)
- `getQueueMetadataById(queueId)` - Returns single queue metadata including name, color, currently_playing_position

**DBWriter**:
- `createQueue(name, color)` - Creates new queue with user-defined name and color
- `renameQueue(queueId, newName)` - Renames existing queue
- `changeQueueColor(queueId, color)` - Changes queue color
- `deleteQueue(queueId)` - Deletes queue and handles episodes (move to inbox), updates SharedPreferences if this was current queue
- `moveEpisodeToQueue(episodeId, targetQueueId)` - Moves episode between queues

**SharedPreferences helpers** (in PlaybackPreferences or new QueuePreferences):
- `getCurrentQueueId()` - Returns current queue ID from SharedPreferences
- `setCurrentQueueId(queueId)` - Updates current queue ID in SharedPreferences

## Success Criteria

- **SC-001**: Database migration completes without data loss for 100% of users
- **SC-002**: New schema supports up to 50 queues with 1000+ episodes per queue without performance degradation
- **SC-003**: All queue operations (create, read, update, delete) complete in <100ms
- **SC-004**: Existing single-queue functionality continues to work unchanged after migration
- **SC-005**: Database integrity constraints prevent invalid states (duplicate episodes, missing queues)
- **SC-006**: Queue colors render correctly in UI (INTEGER color format compatible with Android)
- **SC-007**: Per-queue playback position tracking works correctly when switching between queues
- **SC-008**: Current queue persists across app restarts (via SharedPreferences)

## Notes on Color Storage

Colors stored as INTEGER (RGB format):
- Example: Red = 0xFFFF0000 (stored as -65536 in SQLite)
- Android can convert: `Color.parseColor("#FF0000")` → INTEGER
- Or use ColorInt annotation for type safety

Default color suggestions:
- Blue: 0xFF2196F3
- Green: 0xFF4CAF50
- Orange: 0xFFFF9800
- Purple: 0xFF9C27B0
- Red: 0xFFF44336

## Out of Scope (For This Phase)

- UI implementation for multiple queues
- Queue switching UI/dialogs
- Episode assignment UI/dialogs
- Queue-specific preferences or playback settings
- Queue synchronization across devices via gpodder.net
- Smart queue features (auto-assignment based on metadata)
- Queue sharing or export/import
- Queue templates or presets
