# Data Model: Multiple Queues Database Schema

**Feature**: Multiple Queues
**Date**: 2025-11-01
**Research**: [research.md](./research.md)
**Specification**: [spec.md](./spec.md)

## Overview

This document defines the data model for supporting multiple named queues in AntennaPod. The model consists of one new entity (`QueueMetadata`) and modifications to the existing `Queue` entity.

## Entities

### QueueMetadata (NEW)

Stores metadata about each queue.

**Table Name**: `QueueMetadata`

**Columns**:

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique queue identifier |
| `name` | TEXT | NOT NULL | User-defined queue name (e.g., "Workout", "Commute") |
| `color` | INTEGER | NOT NULL | RGB color value for UI (e.g., 0xFFAA3344) |
| `created_at` | INTEGER | NOT NULL | Unix timestamp (milliseconds) when queue was created |
| `sort_order` | INTEGER | NOT NULL, UNIQUE | User-defined display order (0-indexed) |
| `currently_playing_feedmedia_id` | INTEGER | DEFAULT -1 | FeedMedia ID of last-played episode in this queue (-1 = none) |
| `currently_playing_feed_id` | INTEGER | DEFAULT -1 | Feed ID of last-played episode in this queue (-1 = none) |

**Constraints**:
- `name` cannot be empty string (enforced in application)
- `sort_order` must be unique across all queues
- At least one queue must exist at all times (enforced in DBWriter)
- `currently_playing_feedmedia_id` must reference valid FeedMedia.id or be -1 (enforced in application)

**Indexes**:
```sql
CREATE UNIQUE INDEX idx_queue_metadata_sort_order ON QueueMetadata(sort_order);
```

**Example Rows**:
```
id | name     | color      | created_at    | sort_order | currently_playing_feedmedia_id | currently_playing_feed_id
---+----------+------------+---------------+------------+--------------------------------+---------------------------
1  | Main     | -14575885  | 1698854400000 | 0          | 42                             | 7
2  | Workout  | -10027162  | 1698854500000 | 1          | -1                             | -1
3  | Commute  | -16711936  | 1698854600000 | 2          | 123                            | 15
```

**Domain Object** (`model/src/main/java/.../model/feed/QueueMetadata.java`):
```java
package de.danoeh.antennapod.model.feed;

public class QueueMetadata {
    private final long id;
    private String name;
    private int color;
    private final long createdAt;
    private int sortOrder;
    private long currentlyPlayingFeedMediaId;
    private long currentlyPlayingFeedId;

    // Constructors, getters, setters

    public static final long NO_MEDIA_PLAYING = -1;
}
```

---

### Queue (MODIFIED)

Stores episodes in queues with their positions.

**Table Name**: `Queue`

**Columns** (changes marked with ⭐):

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY | Position within specific queue (0, 1, 2...) |
| `feeditem` | INTEGER | NOT NULL | References FeedItems.id |
| `feed` | INTEGER | NOT NULL | References Feeds.id (redundant, kept for compatibility) |
| ⭐ `queue_id` | INTEGER | DEFAULT 1 | References QueueMetadata.id (which queue this item belongs to) |

**Constraints**:
- `(queue_id, id)` must be UNIQUE (enforced via unique constraint, see research.md)
- `feeditem` appears at most once across all queues (enforced in DBWriter)
- Positions within queue must be continuous: 0, 1, 2, 3... with no gaps (enforced in DBWriter)
- `queue_id` must reference valid QueueMetadata.id (foreign key, enabled via PRAGMA)

**Indexes**:
```sql
-- Composite index for primary query pattern
CREATE INDEX idx_queue_queue_id_id ON Queue(queue_id, id);

-- Unique constraint on (queue_id, id) - position within queue
CREATE UNIQUE INDEX idx_queue_unique_position ON Queue(queue_id, id);

-- Index for fast feeditem lookups (no uniqueness - same episode can be in multiple queues)
CREATE INDEX idx_queue_feeditem ON Queue(feeditem);
```

**Migration Path**:

From research.md, we learned that SQLite ALTER TABLE with DEFAULT applies virtually, so:

```sql
-- Step 1: Add column (instant, virtual default)
ALTER TABLE Queue ADD COLUMN queue_id INTEGER DEFAULT 1;

-- Step 2: Create indexes
CREATE INDEX idx_queue_queue_id_id ON Queue(queue_id, id);
CREATE UNIQUE INDEX idx_queue_unique_position ON Queue(queue_id, id);
CREATE INDEX idx_queue_feeditem ON Queue(feeditem);
```

**Example Rows** (before and after):

Before migration:
```
id | feeditem | feed
---+----------+-----
0  | 101      | 1
1  | 102      | 1
2  | 103      | 2
```

After migration (same data, queue_id added virtually):
```
id | feeditem | feed | queue_id
---+----------+------+---------
0  | 101      | 1    | 1        (virtual default)
1  | 102      | 1    | 1        (virtual default)
2  | 103      | 2    | 1        (virtual default)
```

After user creates second queue and adds episodes:
```
id | feeditem | feed | queue_id
---+----------+------+---------
0  | 101      | 1    | 1
1  | 102      | 1    | 1
2  | 103      | 2    | 1
0  | 104      | 3    | 2        (same id=0, different queue_id)
1  | 105      | 3    | 2
```

---

## Relationships

```
QueueMetadata (1) ←──────── (N) Queue
    │                          │
    │                          │
    │                          └─→ FeedItems (N:1)
    │                                  │
    │                                  └─→ Feeds (N:1)
    │
    └─→ currently_playing_feedmedia_id → FeedMedia (N:1, optional)
```

**Relationship Details**:

1. **QueueMetadata → Queue** (One-to-Many)
   - One queue can contain many episodes
   - `Queue.queue_id` → `QueueMetadata.id`
   - Enforced: Foreign key (PRAGMA foreign_keys=ON)
   - On delete: Cascade (deleting queue removes all its episodes from Queue table)

2. **Queue → FeedItems** (Many-to-One)
   - Each queue entry references one episode
   - `Queue.feeditem` → `FeedItems.id`
   - Enforced: Application logic (not foreign key, to allow deleted episodes)

3. **QueueMetadata → FeedMedia** (Many-to-One, Optional)
   - `currently_playing_feedmedia_id` = -1 means no episode playing in this queue
   - If set, references the last-played episode for quick resume
   - Enforced: Application logic

---

## State Transitions

### QueueMetadata Lifecycle

```
┌─────────┐
│ Created │  (User creates new queue)
└────┬────┘
     │
     ↓
┌─────────┐  ← → ┌──────────┐
│  Active │ ───→ │ Modified │  (User renames, changes color, reorders)
└────┬────┘      └──────────┘
     │
     ↓
┌─────────┐
│ Deleted │  (User deletes queue, episodes move to inbox)
└─────────┘

Constraints:
- At least one queue must exist (cannot delete last queue)
- sort_order must remain unique (handled by reorder logic)
```

### Queue Item Lifecycle

```
┌─────────┐
│ Not in  │
│  Queue  │  (Episode in feed/inbox)
└────┬────┘
     │
     ↓ (User adds to queue)
┌─────────┐
│ In Queue│  (Has position in specific queue)
└────┬────┘
     │
     ├─→ (User plays) → ┌───────────────┐
     │                  │ Currently      │ → QueueMetadata.currently_playing_*
     │                  │ Playing        │
     │                  └────────────────┘
     │
     ├─→ (User moves) → ┌───────────────┐
     │                  │ Different Queue│ (Remove from old, add to new)
     │                  └────────────────┘
     │
     └─→ (User removes or queue deleted) → Back to "Not in Queue"

Constraints:
- Position (id) must be continuous within queue_id
- Each feeditem appears at most once across all queues
```

---

## Validation Rules

### Application-Level Validations (DBWriter)

1. **Queue Name Validation**:
   - Not null
   - Not empty string
   - Length: 1-50 characters (practical limit)

2. **Queue Color Validation**:
   - Valid Android ColorInt
   - Not null
   - Format: 0xAARRGGBB (8-digit hex)

3. **sort_order Uniqueness**:
   - No two queues share same sort_order
   - Handled by reorder operation (shifts other queues)

4. **At Least One Queue**:
   - `deleteQueue()` must check count
   - If last queue, prevent deletion or auto-create replacement

5. **Episode Uniqueness**:
   - Before adding episode to queue, check if already in another queue
   - If yes, remove from old queue first (or reject operation)

6. **Position Continuity**:
   - When removing episode from middle of queue, renumber subsequent positions
   - Example: Remove id=2 → shift id=3→2, id=4→3, etc.

7. **currently_playing_* Bounds**:
   - If currently_playing_feedmedia_id != -1, verify FeedMedia exists
   - If FeedMedia deleted, reset to -1

### Database-Level Validations (SQL)

```sql
-- UNIQUE constraint on (queue_id, id)
CREATE UNIQUE INDEX idx_queue_unique_position ON Queue(queue_id, id);

-- UNIQUE constraint on sort_order
CREATE UNIQUE INDEX idx_queue_metadata_sort_order ON QueueMetadata(sort_order);

-- Index on feeditem for fast lookups (no uniqueness - episodes can appear in multiple queues)
CREATE INDEX idx_queue_feeditem ON Queue(feeditem);

-- Foreign key enforcement (requires PRAGMA foreign_keys=ON)
-- Conceptual: Queue.queue_id REFERENCES QueueMetadata(id) ON DELETE CASCADE
```

---

## Performance Considerations

### Query Patterns

**Most Common Query** (Get episodes for specific queue):
```sql
SELECT FeedItems.*, Queue.id as queue_position
FROM Queue
INNER JOIN FeedItems ON Queue.feeditem = FeedItems.id
WHERE Queue.queue_id = ?
ORDER BY Queue.id ASC;
```
Optimized by: `idx_queue_queue_id_id` composite index (covers WHERE and ORDER BY)

**Second Most Common** (Check if episode in any queue):
```sql
SELECT queue_id FROM Queue WHERE feeditem = ?;
```
Optimized by: `idx_queue_feeditem` index (returns 0-N rows, no uniqueness)

**Queue Metadata Queries**:
```sql
SELECT * FROM QueueMetadata ORDER BY sort_order ASC;
```
Optimized by: `idx_queue_metadata_sort_order` index

### Write Performance

- Queue additions/removals: O(1) for insert/delete, O(n) for position renumbering
- Queue creation: O(1)
- Queue deletion: O(k) where k = episodes in that queue
- Reorder queues: O(m) where m = number of queues affected

### Scale Targets (from spec.md)

- Support: 50 queues, 1000+ episodes per queue
- Operations: <100ms for all CRUD operations
- Migration: <1 second for typical user (tested with 10k queue items)

---

## Migration Impact

### Data Migration

1. **Existing Queue table**: Add `queue_id` column with DEFAULT 1 (virtual, instant)
2. **Create QueueMetadata**: Insert one row (id=1, name="Main")
3. **Migrate SharedPreferences**: Copy currently_playing_* to QueueMetadata row
4. **Add PREF_CURRENT_QUEUE_ID**: Set to 1 in SharedPreferences
5. **Remove old preferences**: Delete PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, PREF_CURRENTLY_PLAYING_FEED_ID

### Backward Compatibility

**Method Signatures** (all methods get optional `queueId` parameter, default to current queue):

Before:
```java
List<FeedItem> getQueue()
void addQueueItem(FeedItem item)
void removeQueueItem(FeedItem item)
```

After:
```java
List<FeedItem> getQueue(long queueId)  // defaults to getCurrentQueueId() if -1
void addQueueItem(FeedItem item, long queueId)  // defaults to getCurrentQueueId() if -1
void removeQueueItem(FeedItem item, long queueId)  // defaults to getCurrentQueueId() if -1
```

**Overloaded versions for compatibility**:
```java
List<FeedItem> getQueue() {
    return getQueue(QueuePreferences.getCurrentQueueId());
}
```

---

## Testing Requirements

### Unit Tests

1. **QueueMetadata CRUD**:
   - Create queue with valid/invalid data
   - Rename queue
   - Delete queue (with/without episodes)
   - Reorder queues

2. **Queue Operations**:
   - Add episode to specific queue
   - Remove episode from queue
   - Move episode between queues
   - Verify position continuity after removal

3. **Migration**:
   - Existing users: verify queue_id=1, data preserved
   - Fresh installs: verify default queue created
   - SharedPreferences migration: verify currently_playing_* copied

4. **Constraints**:
   - Verify (queue_id, id) uniqueness
   - Verify feeditem uniqueness across queues
   - Verify sort_order uniqueness
   - Verify at-least-one-queue invariant

### Integration Tests

1. **Foreign keys**: Enable PRAGMA, verify cascade delete
2. **Backward compatibility**: Call old method signatures, verify default queue used
3. **EventBus**: Verify QueueEvent posted on operations
4. **Performance**: Measure operations with 1000 episodes, verify <100ms

---

## Future Extensibility

Fields reserved for future features (not in this phase):

- `QueueMetadata.auto_enqueue_rules` (JSON) - Smart queue auto-assignment
- `QueueMetadata.playback_speed` (REAL) - Per-queue playback speed override
- `QueueMetadata.sync_enabled` (INTEGER) - Enable gpodder.net sync for this queue

These can be added via future ALTER TABLE operations without breaking existing code.
