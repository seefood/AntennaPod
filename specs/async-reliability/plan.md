# Implementation Plan: Async Reliability & Executor Consolidation

**Branch**: `001-smart-queues` (integrate async-reliability fixes) | **Date**: 2025-11-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/async-reliability/spec.md`

## Summary

Fix critical thread safety violations preventing merge of 001-smart-queues to develop:

1. **Multi-step operations fail**: removeQueueItem + addQueueItem split across executor.submit() calls can interleave, corrupting queue state
2. **5 duplicate executors**: DBWriter, PlaybackService, QueueSelectionDialog, FeedItemMenuHandler, RemoveFromQueueSwipeAction each have own executor → race conditions
3. **Main thread blocking**: `.get()` calls in UI layer block main thread during database operations → ANR crashes
4. **moveQueueItem breaks invariant**: Temporarily modifies UserPreferences.currentQueueId → PlaybackService reads inconsistent state
5. **EventBus information loss**: Events include unnecessary queueId for single-queue operations; missing queueId for cross-queue moves

**Technical Approach**:
- Refactor all multi-step DB operations into single executor.submit() task with automatic rollback on failure
- Expose DBWriter.dbExec via getDbExecutor(); remove all private executor instances
- Replace `.get()` calls with async callbacks (Handler.post, LiveData.postValue)
- Rewrite moveQueueItem to take explicit sourceQueueId/targetQueueId; never modify active queue
- Audit all EventBus.post() calls; remove queueId from single-queue events; add to cross-queue events
- Add regression test suite: 100 concurrent operations across 3 scenarios with execution log verification

## Technical Context

**Language/Version**: Java 17 (source/target compatibility), Android API 21-35
**Primary Dependencies**: Android SDK, SQLite (via PodDBAdapter), EventBus (GreenRobot), ExoPlayer
**Storage**: SQLite database via PodDBAdapter (no schema changes needed, only logic fixes)
**Testing**: Robolectric for unit tests, Espresso for integration tests (QueueSelectionDialog), JUnit 4, CountDownLatch for concurrent testing
**Target Platform**: Android (API 21-35, 10+ years of Android versions)
**Project Type**: Mobile (Android app with 37-module architecture)

**Key Files**:
- `storage/database/DBWriter.java` - Multi-step operation refactoring + getDbExecutor() + EventBus audit
- `storage/database/DBReader.java` - Audit for async pattern compliance
- `playback/service/PlaybackService.java` - Remove private dbExecutor, queue switch handler, episode removal handler
- `ui/common/QueueSelectionDialog.java` - Replace `.get()` with async callback
- `ui/common/FeedItemMenuHandler.java` - Replace `.get()` with async callback
- `ui/swipeactions/RemoveFromQueueSwipeAction.java` - Remove private executor, use shared
- `event/QueueEvent.java` - Add operationFailed event type, audit queueId inclusion
- `CLAUDE.md` - Add atomicity + executor consolidation rules
- New: `storage/database/AsyncOperationAtomicityTest.java` - Regression test framework

## Constitution Check

**Merge Gating**: All 6 phases required before merge to develop. Enforced via:
- Automatic rollback on multi-step failures (Phase 1, measured in Phase 6 tests)
- Single executor instance (Phase 4, verified by codebase scan)
- Zero `.get()` on main thread (Phase 5, code review + static analysis)
- moveQueueItem never modifies active queue (Phase 2, thread-safety test)
- EventBus queueId only for non-active-queue ops (Phase 3, audit verification)
- 100% consistency across 100 concurrent operations (Phase 6, automated test)

## Implementation Strategy

**MVP Scope**: User Stories 1–2 (Prevent Multi-Step Failures + Consolidate Executors)
- Foundation for all other fixes
- Unblocks UI layer refactoring (US3–4)
- Enables EventBus audit (US5)

**Incremental Delivery**:
- Phase 1: Establish atomicity pattern + expose getDbExecutor()
- Phase 2: Fix moveQueueItem (depends on Phase 1)
- Phase 3: EventBus audit (depends on Phase 1)
- Phase 4: Consolidate executors (depends on Phase 1–3)
- Phase 5: Async pattern implementation (depends on Phase 4)
- Phase 6: Regression tests (depends on Phase 1–5)

**Parallelization**:
- US1 atomicity refactoring (DBWriter.moveQueueItem, copyQueueItem, etc.)
- US2 executor consolidation (4 UI classes can be refactored independently in Phase 4)
- US3 async callbacks (QueueSelectionDialog, FeedItemMenuHandler can be refactored in parallel in Phase 5)

## Merge Gate Criteria

- ✓ All 6 phases complete
- ✓ 100 concurrent moveQueueItem operations: 100% consistency
- ✓ 100 concurrent copyQueueItem operations: 100% consistency
- ✓ 100 concurrent removeQueueItem operations: 100% consistency
- ✓ Execution logs: zero operation interleaving
- ✓ Code review: approved
- ✓ All tests passing (existing + new regression tests)
