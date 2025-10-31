# Implementation Plan: Multiple Queues - Database Schema

**Branch**: `multiqueue/001-planning` | **Date**: 2025-10-31 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-multiple-queues/spec.md`

## Summary

Extend AntennaPod's database schema to support multiple named queues instead of the current single queue. This is a database-only change that adds a new `QueueMetadata` table and modifies the existing `Queue` table with a `queue_id` column. The currently playing episode tracking will be migrated from SharedPreferences to the database (per-queue). All existing DBReader/DBWriter methods will be refactored to accept an optional `queueId` parameter that defaults to the current active queue stored in SharedPreferences.

**Technical Approach**: SQLite schema migration via DBUpgrader, backward-compatible method signatures with optional parameters, EventBus for queue change notifications.

## Technical Context

**Language/Version**: Java 17 (source/target compatibility), Android SDK 21-35
**Primary Dependencies**: SQLite (Android built-in), EventBus 3.3.1
**Storage**: SQLite database via `PodDBAdapter`, SharedPreferences for active queue tracking
**Testing**: JUnit 4.13, Robolectric 4.14 for Android-dependent code, DBWriter.tearDownTests() required
**Target Platform**: Android 5.0+ (API 21-35), supports 10+ years of Android versions
**Project Type**: Android multi-module application (37 modules)
**Performance Goals**: All queue operations <100ms, support 50 queues with 1000+ episodes each
**Constraints**: Zero data loss during migration, backward compatible with existing code, single-threaded write executor
**Scale/Scope**: Database schema change only, no UI implementation

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I: Code Quality First ✅

**Status**: PASS (planning phase, will enforce during implementation)

- Checkstyle: Will apply to new/modified Java files
- SpotBugs: Will analyze database code for concurrency issues
- Android Lint: Will check for SQL injection, resource leaks
- XML Formatting: N/A (no UI changes in this phase)

**Action**: Run quality gates before committing implementation code.

### Principle II: Test Coverage for New Features ✅

**Status**: PASS (test strategy defined)

**Required Tests**:
- Unit tests for DBReader/DBWriter new methods (getAllQueues, createQueue, deleteQueue, etc.)
- Unit tests for migration logic (existing users, fresh installs)
- Unit tests for queue operations (add/remove episodes, switch queues)
- Integration tests for SharedPreferences↔database synchronization
- Integration tests for backward compatibility (existing methods with default queueId)
- Database integrity tests (constraints, foreign keys, position continuity)

**Test Setup**: All database tests MUST call `DBWriter.tearDownTests()` in teardown to prevent "Illegal connection pointer" errors.

### Principle III: Modular Architecture Preservation ✅

**Status**: PASS

**Module Impact**:
- `storage/database/` - Primary changes (PodDBAdapter, DBReader, DBWriter, DBUpgrader)
- `storage/preferences/` - Add QueuePreferences helper methods
- `model/` - Potentially add Queue domain object (if needed, pure Java, no Android deps)
- `event/` - May add/modify QueueEvent for queue switching notifications

**No new modules required**. Changes isolated to storage layer. UI layer remains unchanged in this phase.

### Principle IV: Event-Driven Communication ✅

**Status**: PASS

**EventBus Usage**:
- `QueueEvent` will be posted when:
  - Queue created/renamed/deleted
  - Queue reordered
  - Active queue switched
  - Episodes moved between queues
- Pattern: `DBWriter` posts events after successful write operations
- Thread mode: `ThreadMode.MAIN` for UI subscribers

**Compliance**: Follows existing EventBus patterns used by other DB operations.

### Principle V: Database Integrity ✅

**Status**: PASS

**Compliance**:
- All schema changes via `DBUpgrader` (version 3080000 → 3080100)
- All writes via `DBWriter` static methods (single-threaded executor)
- All reads via `DBReader` static methods
- No raw SQL outside `PodDBAdapter`, `DBReader`, `DBWriter`
- Migration logic ensures no data loss

**Critical Table**: Queue table modified (add column), new QueueMetadata table created.

### Overall Constitution Compliance: ✅ PASS

All principles satisfied. No violations require justification.

## Project Structure

### Documentation (this feature)

```text
specs/001-multiple-queues/
├── spec.md               # Feature specification (COMPLETE)
├── plan.md               # This file (IN PROGRESS)
├── research.md           # Phase 0 output (TO BE CREATED)
├── data-model.md         # Phase 1 output (TO BE CREATED)
├── quickstart.md         # Phase 1 output (TO BE CREATED)
├── contracts/            # Phase 1 output (TO BE CREATED - SQL migrations)
└── checklists/
    └── requirements.md   # Validation checklist (COMPLETE)
```

### Source Code (repository root)

```text
# Android multi-module structure (existing)
storage/database/src/main/java/de/danoeh/antennapod/storage/database/
├── PodDBAdapter.java           # Add TABLE_NAME_QUEUE_METADATA, CREATE_TABLE_QUEUE_METADATA
├── DBReader.java               # Add getAllQueues(), getQueueMetadataById(), modify getQueue() signature
├── DBWriter.java               # Add createQueue(), deleteQueue(), etc., modify addQueueItem() signature
├── DBUpgrader.java             # Add migration for version 3080100
└── mapper/
    └── QueueMetadataMapper.java # NEW - cursor-based mapper for QueueMetadata

storage/preferences/src/main/java/de/danoeh/antennapod/storage/preferences/
├── PlaybackPreferences.java    # Modify to read/write QueueMetadata instead of SharedPreferences
└── QueuePreferences.java       # NEW - helper methods for getCurrentQueueId(), setCurrentQueueId()

model/src/main/java/de/danoeh/antennapod/model/feed/
└── QueueMetadata.java          # NEW - domain object (id, name, color, created_at, sort_order, currently_playing_*)

event/src/main/java/de/danoeh/antennapod/event/
└── QueueEvent.java             # MODIFIED - add queue switching event types

storage/database/src/test/java/de/danoeh/antennapod/storage/database/
├── DBReaderQueueTest.java      # NEW - test queue reading operations
├── DBWriterQueueTest.java      # NEW - test queue writing operations
├── QueueMigrationTest.java     # NEW - test migration logic
└── QueueIntegrityTest.java     # NEW - test constraints and invariants
```

**Structure Decision**: Using existing Android multi-module structure. Changes isolated to `storage/database/`, `storage/preferences/`, `model/`, and `event/` modules. No UI changes in this phase.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

N/A - No constitution violations.

## Phase 0: Research

**Status**: NOT STARTED

**Unknowns to Research**:

1. **SQLite ALTER TABLE behavior with DEFAULT values** - How does `ALTER TABLE Queue ADD COLUMN queue_id INTEGER DEFAULT 1` affect existing rows? Is the default applied retroactively?

2. **Composite primary key vs. separate unique constraint** - Queue table has PRIMARY KEY on `id`, but `id` is now scoped by `queue_id`. Should we:
   - Change to composite PRIMARY KEY (id, queue_id)?
   - Add UNIQUE constraint on (id, queue_id)?
   - Keep current structure and enforce in application logic?

3. **SharedPreferences migration timing** - When should PREF_CURRENTLY_PLAYING_* be removed from SharedPreferences? During database migration or after?

4. **EventBus event design for queue switching** - Should QueueEvent include:
   - Old queue ID + new queue ID?
   - Just new queue ID?
   - Queue metadata (name, color)?

5. **Rollback strategy** - If migration fails mid-way, how do we rollback? SQLite transactions, or manual restore?

**Best Practices to Research**:

1. **SQLite migration patterns in Android** - Industry best practices for schema migrations with ALTER TABLE
2. **Database versioning** - When to use MINOR vs PATCH increments (currently proposing 3080000 → 3080100)
3. **Foreign key enforcement** - Should we enable foreign keys in SQLite? Current AntennaPod setup?
4. **Index strategy** - Just `queue_id` index, or composite index on (queue_id, id)?

**Output**: `research.md` with decisions, rationale, and alternatives considered.

## Phase 1: Design & Contracts

**Status**: NOT STARTED

**Prerequisites**: Phase 0 research.md complete

**Deliverables**:

1. **data-model.md** - Entity definitions with:
   - QueueMetadata entity (fields, constraints, relationships)
   - Modified Queue entity (added queue_id field)
   - State transitions for queue lifecycle
   - Validation rules from constraints section

2. **contracts/** directory:
   - `001-create-queue-metadata-table.sql` - DDL for new table
   - `002-alter-queue-add-queue-id.sql` - DDL for modifying Queue table
   - `003-migrate-data.sql` - DML for migrating existing data
   - `migration-sequence.md` - Step-by-step migration procedure

3. **quickstart.md** - Developer guide:
   - How to test migrations locally
   - How to run database tests
   - How to verify backward compatibility
   - Example usage of new methods

4. **Agent context update**:
   - Run `.specify/scripts/bash/update-agent-context.sh claude`
   - Add SQLite migration patterns
   - Add EventBus queue events
   - Add database testing best practices

## Phase 2: Task Breakdown

**Status**: NOT STARTED (use `/speckit.tasks` command)

This phase generates `tasks.md` with actionable implementation tasks. Not part of `/speckit.plan` command.

## Next Steps

1. Execute Phase 0: Research unknowns and gather best practices → `research.md`
2. Execute Phase 1: Design data model and SQL contracts → `data-model.md`, `contracts/`, `quickstart.md`
3. Update agent context file with new technical knowledge
4. Re-check Constitution compliance after design decisions
5. Run `/speckit.tasks` to generate actionable task breakdown

**Command to continue**: After reviewing this plan, run research agents to populate `research.md`, then proceed with Phase 1 design.
