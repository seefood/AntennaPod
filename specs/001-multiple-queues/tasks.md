# Implementation Tasks: Multiple Queues Database Schema

**Feature**: Multiple Queues - Database Layer
**Database Version**: 3080000 → 3080100 (MINOR increment)
**Status**: Phase 2 - Ready for Implementation
**Related Docs**: [spec.md](./spec.md) | [data-model.md](./data-model.md) | [plan.md](./plan.md) | [quickstart.md](./quickstart.md)

## Overview

This document breaks down the multiple queues database feature into actionable implementation tasks. The feature adds support for multiple named queues (stored in `QueueMetadata` table) while maintaining backward compatibility with existing code.

**Scope**: Database schema changes, domain models, database access layer, preferences helper, and comprehensive testing.

**Total Tasks**: 42 (T001-T042, organized across 6 phases with subtasks)

**Dependencies**: Tasks organized by module, with clear dependencies shown in each phase.

## Implementation Strategy

This feature has ONE logical unit (database schema change) with no independent user stories. All tasks must be completed to fully implement the feature.

**Approach**:
1. **Phase 1 (Setup)**: Database schema migration infrastructure
2. **Phase 2 (Foundation)**: Domain models and database access layer
3. **Phase 3 (Core)**: Queue operations and migration logic
4. **Phase 4 (Integration)**: Preferences integration and event handling
5. **Phase 5 (Testing)**: Comprehensive test coverage
6. **Phase 6 (Polish)**: Code quality and documentation

---

## Phase 1: Setup - Database Migration Infrastructure

**Goal**: Establish the migration versioning and infrastructure

### Tasks

- [ ] T001 Add database version constants in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/PodDBAdapter.java`
  - Add `DB_VERSION_OLD = 3080000` constant
  - Add `DB_VERSION_NEW = 3080100` constant
  - Document reason for version bump in comment

- [ ] T002 Add table name and SQL string constants in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/PodDBAdapter.java`
  - Add `TABLE_NAME_QUEUE_METADATA = "QueueMetadata"` constant
  - Add `CREATE_TABLE_QUEUE_METADATA` SQL string with full DDL from contracts/001-create-queue-metadata-table.sql
  - Add column name constants (QUEUE_METADATA_ID, QUEUE_METADATA_NAME, QUEUE_METADATA_COLOR, etc.)

- [ ] T003 Enable foreign keys in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/PodDBAdapter.java`
  - Override `onConfigure(SQLiteDatabase db)` method
  - Call `db.setForeignKeyConstraintsEnabled(true)` (Android API 16+)
  - Add JavaDoc explaining why foreign keys are needed

- [ ] T004 Create `DBUpgrader.java` migration method stub in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBUpgrader.java`
  - Create `private static void migrateToVersion3080100(SQLiteDatabase db)` method
  - Add case statement in `onUpgrade()` for versions <= 3080000 and < 3080100
  - Add logging: `Log.d("DBUpgrader", "Upgrading to version 3080100: Multiple Queues")`

---

## Phase 2: Foundation - Domain Models

**Goal**: Create domain objects for type-safe queue operations

### Tasks

- [ ] T005 Create `QueueMetadata.java` domain object in `model/src/main/java/de/danoeh/antennapod/model/feed/QueueMetadata.java`
  - Fields: id (long), name (String), color (int), createdAt (long), sortOrder (int), currentlyPlayingFeedMediaId (long), currentlyPlayingFeedId (long)
  - Add constructor with all fields
  - Add getters and setters for mutable fields (name, color, sortOrder, currentlyPlayingFeedMediaId, currentlyPlayingFeedId)
  - Add `static final long NO_MEDIA_PLAYING = -1` constant
  - Add JavaDoc for each field explaining constraints
  - Implement `equals(Object)` and `hashCode()` based on id field only

- [ ] T006 Create `QueueMetadataMapper.java` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/mapper/QueueMetadataMapper.java`
  - Create static method `QueueMetadata fromCursor(Cursor cursor)` following existing mapper patterns
  - Extract all 7 columns from cursor and populate QueueMetadata object
  - Add cursor column index mappings
  - Follow same pattern as FeedItemMapper.java

---

## Phase 3: Core - Database Access Layer

**Goal**: Implement all queue CRUD operations with full backward compatibility

### 3.1 Database Schema Migration Tasks

- [ ] T007 Implement QueueMetadata table creation in `DBUpgrader.migrateToVersion3080100()` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBUpgrader.java`
  - Execute contracts/001-create-queue-metadata-table.sql DDL
  - Create unique index on sort_order column
  - Add logging for each step

- [ ] T008 Implement Queue table schema modification in `DBUpgrader.migrateToVersion3080100()` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBUpgrader.java`
  - Execute ALTER TABLE Queue ADD COLUMN queue_id INTEGER DEFAULT 1
  - Create composite index idx_queue_queue_id_id (queue_id, id)
  - Create unique index idx_queue_unique_position (queue_id, id)
  - Create index idx_queue_feeditem (feeditem) - no uniqueness, episodes can appear in multiple queues
  - Verify all indexes created successfully

- [ ] T009 Implement default queue creation and data migration in `DBUpgrader.migrateToVersion3080100()` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBUpgrader.java`
  - Insert default "Main" queue with id=1, sort_order=0, color=-14575885
  - Set created_at to current timestamp
  - Read PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID and PREF_CURRENTLY_PLAYING_FEED_ID from SharedPreferences
  - Update QueueMetadata.1 with migrated currently_playing values
  - Add logging for all migration steps
  - Add error handling with detailed error messages

### 3.2 DBReader Queue Methods

- [ ] T010 Implement `getAllQueues()` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java`
  - Return `List<QueueMetadata>` sorted by sort_order ASC
  - Query: SELECT * FROM QueueMetadata ORDER BY sort_order ASC
  - Use QueueMetadataMapper.fromCursor() for each row
  - Handle empty result (no queues)

- [ ] T011 Implement `getQueueMetadataById(long queueId)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java`
  - Return `QueueMetadata` for given ID, or null if not found
  - Query: SELECT * FROM QueueMetadata WHERE id = ?
  - Use QueueMetadataMapper.fromCursor()

- [ ] T012 Modify existing `getQueue()` method in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java`
  - Create new overloaded version: `List<FeedItem> getQueue(long queueId)`
  - Query: SELECT FeedItems.* FROM Queue INNER JOIN FeedItems ON Queue.feeditem=FeedItems.id WHERE Queue.queue_id=? ORDER BY Queue.id ASC
  - Use idx_queue_queue_id_id composite index
  - Create old no-arg version `getQueue()` that calls new version with QueuePreferences.getCurrentQueueId()
  - Add JavaDoc explaining default queue parameter

- [ ] T013 Implement `getQueueIdsForFeedItem(long feedItemId)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java`
  - Return `List<Long>` queue IDs (episode can be in multiple queues)
  - Query: SELECT queue_id FROM Queue WHERE feeditem = ? ORDER BY queue_id
  - Uses idx_queue_feeditem index (no uniqueness - may return multiple rows)

- [ ] T014 Implement `countQueueItems(long queueId)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java`
  - Return `int` count of items in given queue
  - Query: SELECT COUNT(*) FROM Queue WHERE queue_id = ?

### 3.3 DBWriter Queue Methods

- [ ] T015 Implement `createQueue(String name, int color)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Return `Future<Long>` with new queue ID
  - Runs on DatabaseExecutor
  - Validate: name not null/empty, color valid
  - Calculate next sort_order = MAX(sort_order) + 1
  - Insert: INSERT INTO QueueMetadata (name, color, created_at, sort_order, currently_playing_feedmedia_id, currently_playing_feed_id) VALUES (...)
  - Post QueueEvent(QueueEvent.Action.QUEUE_CREATED, queueId)
  - Return new queue ID via Future

- [ ] T016 Implement `renameQueue(long queueId, String newName)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Return `Future<Void>`
  - Validate: name not null/empty
  - Update: UPDATE QueueMetadata SET name = ? WHERE id = ?
  - Post QueueEvent(QueueEvent.Action.QUEUE_RENAMED, queueId)

- [ ] T017 Implement `changeQueueColor(long queueId, int color)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Return `Future<Void>`
  - Validate: color is valid ColorInt
  - Update: UPDATE QueueMetadata SET color = ? WHERE id = ?
  - Post QueueEvent(QueueEvent.Action.QUEUE_COLOR_CHANGED, queueId)

- [ ] T018 Implement `deleteQueue(long queueId)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Return `Future<Void>`
  - Validate: queueId != 1 OR count(QueueMetadata) > 1 (cannot delete last queue)
  - If items in queue: DELETE FROM Queue WHERE queue_id = ?
  - Delete: DELETE FROM QueueMetadata WHERE id = ?
  - Post QueueEvent(QueueEvent.Action.QUEUE_DELETED, queueId)
  - Note: Foreign key with ON DELETE CASCADE will handle Queue items automatically

- [ ] T019 Implement `reorderQueues(List<Long> queueIds)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Return `Future<Void>`
  - Validate: list contains all existing queue IDs
  - For each ID in list: UPDATE QueueMetadata SET sort_order = ? WHERE id = ?
  - Post QueueEvent(QueueEvent.Action.QUEUES_REORDERED, -1)

### 3.4 Backward Compatibility Tasks

- [ ] T020 Modify existing `addQueueItem(FeedItem item)` method signature in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Create new overloaded version: `addQueueItem(FeedItem item, long queueId)`
  - No uniqueness check needed - same episode can be in multiple queues simultaneously
  - Calculate next id = MAX(id) + 1 for given queue_id
  - Insert: INSERT INTO Queue (id, feeditem, feed, queue_id) VALUES (?, ?, ?, ?)
  - Update QueueMetadata.currently_playing_feedmedia_id if first item
  - Post QueueEvent
  - Keep old method signature: `addQueueItem(FeedItem item)` calls new version with QueuePreferences.getCurrentQueueId()

- [ ] T042 Implement `addQueueItemAt(itemId, index, queueId)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Create new overloaded version: `addQueueItemAt(long itemId, int index, long queueId)`
  - Preserve existing behavior: insert episode at specific position (0-indexed)
  - No uniqueness check needed - same episode can be in multiple queues simultaneously
  - Get current queue items: SELECT * FROM Queue WHERE queue_id = ? ORDER BY id ASC
  - Shift positions: for all items at position >= index, increment id by 1
  - Insert at position: INSERT INTO Queue (id, feeditem, feed, queue_id) VALUES (?, ?, ?, ?)
  - Update QueueMetadata.currently_playing_feedmedia_id if first item
  - Post QueueEvent with correct position
  - Keep old method signature: `addQueueItemAt(long itemId, int index)` calls new version with QueuePreferences.getCurrentQueueId()

- [ ] T021 Modify existing `removeQueueItem(FeedItem item)` method signature in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Create new overloaded version: `removeQueueItem(FeedItem item, long queueId)`
  - Find current position: SELECT id FROM Queue WHERE feeditem = ? AND queue_id = ?
  - Delete: DELETE FROM Queue WHERE feeditem = ? AND queue_id = ?
  - Renumber subsequent positions (shift down by 1)
  - Keep old method signature: `removeQueueItem(FeedItem item)` calls new version with QueuePreferences.getCurrentQueueId()

- [ ] T022 Implement `moveQueueItem(FeedItem item, long toQueueId)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Return `Future<Void>`
  - Source queue is always current active queue: `fromQueueId = QueuePreferences.getCurrentQueueId()`
  - Remove from current queue (calls removeQueueItem(item, fromQueueId))
  - Add to destination queue (calls addQueueItem(item, toQueueId))
  - Post QueueEvent(QueueEvent.Action.QUEUE_ITEM_MOVED, toQueueId)

- [ ] T023 Implement `setCurrentlyPlaying(long queueId, long feedMediaId, long feedId)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Return `Future<Void>`
  - Update: UPDATE QueueMetadata SET currently_playing_feedmedia_id = ?, currently_playing_feed_id = ? WHERE id = ?
  - Post QueueEvent

---

## Phase 4: Integration - Preferences and Events

**Goal**: Connect queue system to SharedPreferences and EventBus

### 4.1 Preferences Helper

- [ ] T024 Create `QueuePreferences.java` in `storage/preferences/src/main/java/de/danoeh/antennapod/storage/preferences/QueuePreferences.java`
  - Add `PREF_CURRENT_QUEUE_ID` constant
  - Implement `getCurrentQueueId()` - reads from SharedPreferences, defaults to 1
  - Implement `setCurrentQueueId(long queueId)` - writes to SharedPreferences
  - Add JavaDoc

- [ ] T025 Modify `PlaybackPreferences.java` in `storage/preferences/src/main/java/de/danoeh/antennapod/storage/preferences/PlaybackPreferences.java`
  - Remove references to PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID (now in QueueMetadata)
  - Remove references to PREF_CURRENTLY_PLAYING_FEED_ID (now in QueueMetadata)
  - Update JavaDoc to reference QueueMetadata table instead
  - Keep method signatures but update implementation to use QueueMetadata queries

### 4.2 Event Handling

- [ ] T026 Enhance `QueueEvent.java` in `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`
  - Add action types: QUEUE_CREATED, QUEUE_RENAMED, QUEUE_COLOR_CHANGED, QUEUE_DELETED, QUEUES_REORDERED, QUEUE_ITEM_MOVED, QUEUE_SWITCHED, CURRENTLY_PLAYING_UPDATED
  - Add queueId field (long)
  - Add action field (enum Action)
  - Update constructor to accept queueId and action
  - Add JavaDoc for each action type

- [ ] T027 Update `DBWriter` event posting in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Ensure all queue operations post QueueEvent with correct action and queueId
  - Verify all new methods (createQueue, renameQueue, etc.) post events
  - Use `EventBus.getDefault().post(event)`

---

## Phase 5: Testing

**Goal**: Comprehensive test coverage for all queue operations

### 5.1 Migration Tests

- [ ] T028 Create `QueueMigrationTest.java` in `storage/database/src/test/java/de/danoeh/antennapod/storage/database/`
  - Test migration of existing users (preserves queue data with queue_id=1)
  - Test migration of fresh installs (creates default "Main" queue)
  - Test SharedPreferences migration (currently_playing_* values copied to QueueMetadata)
  - Test database version updated to 3080100
  - Test indexes created
  - Verify foreign keys enabled
  - All tests: call `DBWriter.tearDownTests()` in teardown

### 5.2 DBReader Tests

- [ ] T029 Create `DBReaderQueueTest.java` in `storage/database/src/test/java/de/danoeh/antennapod/storage/database/`
  - Test `getAllQueues()` returns all queues sorted by sort_order
  - Test `getQueueMetadataById()` returns correct queue or null
  - Test `getQueue(queueId)` returns all items in specific queue
  - Test `getQueue()` (no-arg) returns items from current queue
  - Test `getQueueIdForFeedItem()` returns correct queue ID or -1
  - Test `countQueueItems()` returns correct count
  - All tests setup with multiple queues and multiple items

### 5.3 DBWriter Tests

- [ ] T030 Create `DBWriterQueueTest.java` in `storage/database/src/test/java/de/danoeh/antennapod/storage/database/`
  - Test `createQueue()` creates queue with correct properties
  - Test `renameQueue()` updates name correctly
  - Test `setQueueColor()` updates color correctly
  - Test `deleteQueue()` removes queue and items (with foreign key)
  - Test `deleteQueue()` rejects deletion of last queue
  - Test `reorderQueues()` updates sort_order correctly
  - Test `addQueueItem()` adds item to correct queue
  - Test `removeQueueItem()` removes item and renumbers positions
  - Test `moveQueueItem()` moves item between queues
  - Test `setCurrentlyPlaying()` updates QueueMetadata correctly

### 5.4 Constraint and Integrity Tests

- [ ] T031 Create `QueueIntegrityTest.java` in `storage/database/src/test/java/de/danoeh/antennapod/storage/database/`
  - Test UNIQUE constraint on (queue_id, id) prevents duplicate positions
  - Test UNIQUE constraint on feeditem prevents duplicate episodes
  - Test UNIQUE constraint on sort_order prevents duplicate order values
  - Test foreign key constraint prevents invalid queue_id references
  - Test position continuity after item removal (no gaps)
  - Test at-least-one-queue invariant (cannot delete last queue)

### 5.5 Backward Compatibility Tests

- [ ] T032 Create `QueueBackwardCompatibilityTest.java` in `storage/database/src/test/java/de/danoeh/antennapod/storage/database/`
  - Test old `addQueueItem(item)` calls new version with current queue
  - Test old `getQueue()` returns current queue
  - Test method overloading works correctly
  - Test default queueId parameter resolves to current queue

### 5.6 Performance Tests

- [ ] T033 Create `QueuePerformanceTest.java` in `storage/database/src/test/java/de/danoeh/antennapod/storage/database/`
  - Test `getQueue(queueId)` with 1000 items completes in <100ms
  - Test `addQueueItem()` completes in <100ms
  - Test `reorderQueues()` with 50 queues completes in <100ms
  - Test index usage (EXPLAIN QUERY PLAN validation)

---

## Phase 6: Polish - Code Quality and Documentation

**Goal**: Ensure production-ready code quality and comprehensive documentation

### 6.1 Code Quality Tasks

- [ ] T034 Run Checkstyle on all modified/new files in `storage/database/` and `model/` modules
  - Command: `./gradlew checkstyle`
  - Fix all violations
  - Verify zero violations before proceeding

- [ ] T035 Run SpotBugs on modified database code
  - Command: `./gradlew spotbugsPlayDebug spotbugsDebug`
  - Analyze for concurrency issues (DBWriter executor, EventBus threading)
  - Fix all medium/high priority findings

- [ ] T036 Run Android Lint on database and preference changes
  - Command: `./gradlew :storage:database:lintDebug :storage:preferences:lintDebug`
  - Fix all errors (warnings as errors enabled)
  - Verify SQL injection prevention

- [ ] T037 Update JavaDoc for all new/modified public methods
  - Add @param and @return annotations
  - Document exceptions thrown
  - Add examples for complex methods (e.g., moveQueueItem)
  - Ensure 100% JavaDoc coverage for new classes

- [ ] T038 Run all tests and verify coverage
  - Command: `./gradlew :storage:database:testDebugUnitTest`
  - Verify all new methods have unit test coverage
  - Check line coverage for new code (target: >80%)

### 6.2 Documentation Tasks

- [ ] T039 Update `CLAUDE.md` with queue operation examples
  - Add "Modifying Queue (Multiple Queues v3080100+)" section
  - Include code examples for all new operations
  - Add performance characteristics and constraints

- [ ] T040 Create migration troubleshooting guide in `quickstart.md`
  - Document common migration errors and solutions
  - Add step-by-step migration validation checklist
  - Add rollback procedures if needed

- [ ] T041 Update `README.md` (if exists) with database layer changes
  - Document new tables and columns
  - Note database version 3080100
  - Link to detailed specification

---

## Dependencies

### Critical Path (Blocking Order)

1. **T001-T004**: Database version constants and migration infrastructure (blocks all DB work)
2. **T005-T006**: Domain models (blocks DBReader/DBWriter implementations)
3. **T007-T009**: Schema migration (blocks all data access)
4. **T010-T027**: Database access layer (T028+ depend on this)
5. **T028-T033**: Testing (validates T010-T027)
6. **T034-T041**: Polish (final quality gate)

### Parallelizable Work

- **T005-T006** can run in parallel (both create new files)
- **T010-T023** can be worked on in parallel by different developers (each implements different methods)
- **T028-T033** can be worked on in parallel (different test classes)
- **T034-T041** can be worked on in parallel (different quality aspects)

### Module Boundaries

- **storage/database/**: T001-T004, T007-T027, T028-T033, T034-T036, T038
- **model/**: T005-T006, T034, T037
- **storage/preferences/**: T024-T025, T034
- **event/**: T026-T027
- **Documentation**: T039-T041

---

## Success Criteria

All tasks must be completed for the feature to be production-ready:

- [ ] All 41 tasks completed
- [ ] All 5 test classes created with comprehensive coverage
- [ ] All code quality checks pass (Checkstyle, SpotBugs, Lint)
- [ ] All tests pass (unit + integration)
- [ ] Performance benchmarks met (<100ms for queue operations)
- [ ] Database integrity constraints verified
- [ ] Backward compatibility validated
- [ ] Migration tested with existing and fresh databases
- [ ] Constitution compliance verified (Code Quality First principle)
- [ ] Pull request ready for review

---

## Testing Strategy

### Test Execution Order

1. **Unit Tests First**: T028-T033 (each class isolated)
2. **Integration Tests**: Full test suite against real database
3. **Performance Tests**: T033 with target data volumes
4. **Quality Gates**: T034-T036 (all must pass before merge)

### Test Environment

- Use Robolectric 4.14 for Android-dependent tests
- Test against SQLite database (same as production)
- Enable foreign keys in tests: `db.setForeignKeyConstraintsEnabled(true)`
- Always call `DBWriter.tearDownTests()` in test teardown

### Test Data Setup

- Create multiple queues (5-10) with varying sizes
- Use realistic data: podcast episodes, feeds
- Test edge cases: empty queues, single item, 1000+ items
- Test constraint violations (expect exceptions)

---

## Implementation Effort

| Phase | Duration | Dependencies | Parallelization |
|-------|----------|--------------|-----------------|
| Setup (T001-T004) | ~2 hours | None | Sequential |
| Foundation (T005-T006) | ~2 hours | Phase 1 | Parallel |
| Core (T007-T023) | ~12 hours | Phases 1-2 | Moderate (by method) |
| Integration (T024-T027) | ~3 hours | Phase 3 | Parallel |
| Testing (T028-T033) | ~8 hours | Phase 3 | Parallel |
| Polish (T034-T041) | ~4 hours | All phases | Parallel |
| **Total** | **~31 hours** | - | - |

---

## Notes

- This is a database-only feature (no UI changes in this phase)
- All existing code using queue methods will continue to work without modification
- Queue operations are serialized through single-threaded DatabaseExecutor
- EventBus is used for change notifications (pattern: DB write → event post → UI update)
- Performance is critical: target <100ms for all operations with 1000+ items

---

## Next Steps After Completion

1. **Code Review**: Full review of all database changes against Constitution
2. **Integration**: Prepare for Phase 2 (UI layer) with queue switcher and management screens
3. **Migration Testing**: Real-world testing with actual user databases
4. **Release**: Coordinate database migration with app release to ensure no data loss
