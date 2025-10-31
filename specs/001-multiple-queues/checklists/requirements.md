# Specification Quality Checklist: Multiple Queues - Database Schema

**Purpose**: Validate database schema design completeness before proceeding to implementation
**Created**: 2025-10-31
**Feature**: [spec.md](../spec.md)

## Schema Design Quality

- [x] Current schema documented accurately
- [x] Proposed schema clearly defined with SQL DDL
- [x] Column types and constraints specified
- [x] Indexes identified (queue_id index on Queue table)
- [x] Data migration strategy defined
- [x] Backward compatibility addressed

## Data Integrity

- [x] Primary keys defined
- [x] Foreign key relationships documented
- [x] Constraints and invariants listed
- [x] Unique constraints specified (sort_order)
- [x] NOT NULL constraints identified (name, color)
- [x] Default values specified where needed

## Migration Safety

- [x] Migration path for existing users defined (includes SharedPreferences migration)
- [x] Migration path for new users defined
- [x] Data preservation guaranteed (no data loss)
- [x] SharedPreferences migration included (PREF_CURRENT_QUEUE_ID, migrate currently_playing fields)
- [x] Version increment specified (3080000 → 3080100 MINOR)

## Operation Completeness

- [x] All CRUD operations defined (Create, Read, Update, Delete)
- [x] Common query patterns documented with SQL
- [x] Edge cases handled (delete last queue, duplicate episodes, etc.)
- [x] Performance considerations addressed (indexes on queue_id)
- [x] Queue switching behavior specified
- [x] Delete queue UI constraints defined (last queue cannot be deleted)

## Refactoring Scope

- [x] Existing methods requiring changes identified (DBReader, DBWriter)
- [x] New methods needed documented
- [x] Default parameter values specified (from PREF_CURRENT_QUEUE_ID in SharedPreferences)
- [x] No breaking changes to existing API signatures (backward compatible)
- [x] PlaybackPreferences refactoring identified (currently_playing fields move to QueueMetadata)

## Success Criteria

- [x] Success criteria are measurable
- [x] Success criteria are database/technical-specific (appropriate for DB design)
- [x] Performance targets specified (<100ms operations)
- [x] Scale requirements defined (50 queues, 1000+ episodes per queue)

## Documentation Quality

- [x] Clear table structure documentation
- [x] Example SQL queries provided
- [x] Constraints explained with rationale
- [x] Out of scope items clearly listed
- [x] Queue switching behavior documented
- [x] Delete queue dialog options specified

## Notes

**Final Design Decisions**:

1. **Table Structure**:
   - New table: `QueueMetadata` (NOT "Queues" - clearer naming)
   - ONE new column in Queue table: `queue_id` (INTEGER DEFAULT 1)
   - Queue.id is position within queue (scoped by queue_id, can have duplicates across queues)

2. **Currently Playing Tracking**:
   - Migrated from SharedPreferences to QueueMetadata table
   - Two fields: `currently_playing_feedmedia_id`, `currently_playing_feed_id`
   - Stored per-queue for independent playback resume
   - Old SharedPreferences keys removed after migration

3. **Active Queue Tracking**:
   - New SharedPreferences key: `PREF_CURRENT_QUEUE_ID`
   - All queue operations default to this queue ID
   - Updated when user switches queues

4. **Method Refactoring**:
   - Existing DBReader/DBWriter methods add optional `queueId` parameter
   - Default to `PREF_CURRENT_QUEUE_ID` when not specified
   - PlaybackPreferences methods read/write QueueMetadata instead of SharedPreferences
   - NO new methods for playback position - existing methods refactored

5. **Queue Deletion**:
   - Last queue CANNOT be deleted (UI prevents this)
   - Confirmation dialog with two options (both preserve episode playback position)
   - Episodes NOT marked as played, positions stay intact

6. **Queue Switching**:
   - Pauses current playback
   - Saves state to old queue
   - Loads new queue
   - Selects last-played episode (PAUSED, user must press play)
   - Posts QueueEvent via EventBus

7. **Database Version**:
   - `3080000` → `3080100` (MINOR increment, not PATCH)

8. **Color Storage**:
   - INTEGER RGB format (Android-compatible)
   - Example: 0xFFFF0000 for red

**Status**: All checklist items complete. Database schema design finalized and ready for implementation planning.