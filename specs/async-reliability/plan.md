# Implementation Plan: Async Reliability & Executor Consolidation

**Branch**: `001-smart-queues` (integrate async-reliability fixes) | **Date**: 2025-11-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/async-reliability/spec.md`

## Summary

Fix critical thread safety violations preventing merge of 001-smart-queues to develop:

1. **Multi-step operations fail**: removeQueueItem + addQueueItem split across executor.submit() calls can interleave, corrupting queue state
2. **4 duplicate executors**: PlaybackService, QueueSelectionDialog, FeedItemMenuHandler, RemoveFromQueueSwipeAction each have own executor → race conditions (DBWriter.dbExec is primary)
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

## Constitution Check (AntennaPod Development Constitution v1.0.1 Aligned)

**Principle I: Code Quality First**
- ✓ T057: Pre-review static analysis (checkstyle, lint, spotbugs)
- ✓ T053: Final static analysis before merge
- Enforced via CI gates and merge gate criteria

**Principle II: Test Coverage for New Features**
- ✓ T009a, T010a: Tests for new MoveResult/CopyResult error fields
- ✓ T011-T013: Unit tests for atomicity patterns
- ✓ T027-T028: UI integration tests for async callbacks
- ✓ T033-T034: Thread-safety tests for moveQueueItem
- ✓ T044: QueueEvent field verification tests
- ✓ T045-T048: Regression tests for concurrent operations

**Principle V: Database Integrity**
- ✓ T003: Expose DBWriter.getDbExecutor() (single source of truth)
- ✓ T058: Verify MIN_PRIORITY threads (prevents starving UI thread)
- ✓ Single-threaded executor enforces atomicity and prevents race conditions

**Merge Gating**: All 50 tasks (46 feature + 4 remediation) required before merge to develop:
- Automatic rollback on multi-step failures (Phase 2, measured in Phase 7 tests)
- Single executor instance (Phase 3, verified by codebase scan + T021)
- Zero `.get()` on main thread (Phase 4, code review + static analysis + T026)
- moveQueueItem never modifies active queue (Phase 5, thread-safety test)
- EventBus queueId only for non-active-queue ops (Phase 6, audit verification)
- 100% consistency across 100 concurrent operations (Phase 7, automated test)
- MIN_PRIORITY verification + pre-review + final static analysis (Phase 7, T057/T058)

## Implementation Strategy

**MVP Scope**: Phase 1 + Phase 2 (19 tasks, US1 + Setup)
- Establish atomicity pattern + expose getDbExecutor()
- Refactor moveQueueItem/copyQueueItem with rollback
- Test error fields (T009a, T010a) + concurrent atomicity (T011-T013)
- Foundation for all other fixes
- Unblocks UI layer refactoring (Phase 3-5)
- Enables EventBus audit (Phase 6)
- **Timeline**: 1 week (2 developers)

**Incremental Delivery** (Full 50 tasks):
- Phase 1 (4 tasks): Establish atomicity pattern + expose getDbExecutor()
- Phase 2 (15 tasks): US1 - Multi-step failures (includes error field tests T009a/T010a)
- Phase 3 (9 tasks): US2 - Executor consolidation (depends on Phase 1)
- Phase 4 (8 tasks): US3 - Main thread blocking (depends on Phase 1-3)
- Phase 5 (6 tasks): US4 - moveQueueItem signature (depends on Phase 1)
- Phase 6 (10 tasks): US5 - EventBus queueId (depends on Phase 1)
- Phase 7 (15 tasks): Polish & merge gate (T056-T058 remediation, depends on Phase 1-6)
- **Timeline**: 3-4 weeks (4-5 developers with parallelization)

**Parallelization** (after Phase 1 complete):
- Phase 2: US1 atomicity refactoring (moveQueueItem, copyQueueItem, event types, error field tests)
- Phase 3-6: US2-5 refactoring work (executor consolidation, async callbacks, signature changes, EventBus audit)
- Phase 7: Sequential execution required (T056 → T057-T058 → T049-T051 code reviews → T052-T055 validation)
  - T056: Assign reviewers (prerequisite for code review tasks)
  - T057: Pre-review static analysis (must pass before code reviews)
  - T049-T051: Code reviews (depends on T056, T057)
  - T053-T055: Final validation (depends on code review approvals)

## Merge Gate Criteria (50 Tasks, Constitution Aligned)

- ✓ All 50 tasks complete (46 feature + 4 remediation for Constitution alignment)
- ✓ T057: Pre-review static analysis passed (0 checkstyle, lint, spotbugs violations)
- ✓ Phase 1-6 complete: All 6 feature phases implemented
- ✓ 100 concurrent moveQueueItem operations: 100% consistency (T045)
- ✓ 100 concurrent copyQueueItem operations: 100% consistency (T046)
- ✓ 100 concurrent removeQueueItem operations: 100% consistency (T047)
- ✓ Execution logs: zero operation interleaving verified (T048)
- ✓ Code review: all 3 domain reviewers approve (T049-T051, assigned in T056)
- ✓ T053: Final static analysis passed (0 violations)
- ✓ T058: MIN_PRIORITY verification complete (FR-009, Constitution V: Database Integrity)
- ✓ All tests passing (existing + new concurrent operation tests + error field tests)
