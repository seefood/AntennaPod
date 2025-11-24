# Tasks: Async Reliability & Executor Consolidation

**Feature**: Async Reliability & Executor Consolidation
**Branch**: `001-smart-queues`
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

---

## Overview

5 user stories (4 P1, 1 P2) organized into 7 phases. Total: 46 tasks. All tasks must complete before merge to develop.

### User Stories (Priority Order)

- **US1** (P1): Prevent Multi-Step Operation Failures - atomic executor, automatic rollback
- **US2** (P1): Consolidate Executor Services - single shared executor
- **US3** (P1): Eliminate Main Thread Blocking - async callbacks, no `.get()` on main thread
- **US4** (P1): Fix moveQueueItem Queue Switching - never modify active queue
- **US5** (P2): Correct EventBus queueId Inclusion - event fields audit & fix

### Dependencies & Parallelization

```
Phase 1 (Setup) → Phase 2 (US1) ↓
                              ├→ Phase 3 (US2) ↓
                              ├→ Phase 4 (US3) ↓
                              ├→ Phase 5 (US4) ↓
                              └→ Phase 6 (US5) ↓
                                          → Phase 7 (Polish & Tests)
```

**Parallel Opportunities**:
- US1 atomicity work in DBWriter: moveQueueItem, copyQueueItem, and event types can be implemented in parallel
- US2 executor consolidation: 4 UI classes (PlaybackService, QueueSelectionDialog, FeedItemMenuHandler, RemoveFromQueueSwipeAction) can be refactored independently
- US3 async callbacks: QueueSelectionDialog and FeedItemMenuHandler can be refactored independently
- US4 & US5: Can start after Phase 2 (US1 atomicity complete)

### Merge Gate Criteria (All Required)

- ✓ All 46 tasks complete
- ✓ 100 concurrent moveQueueItem operations: 100% consistency
- ✓ 100 concurrent copyQueueItem operations: 100% consistency
- ✓ 100 concurrent removeQueueItem operations: 100% consistency
- ✓ Execution logs: zero operation interleaving verified
- ✓ Code review: all 3 reviewers approve (atomicity, executors, async patterns)

---

## Phase 1: Setup & Foundational

**Objective**: Establish patterns and infrastructure for all user stories

### Setup Tasks

- [ ] T001 Add atomicity enforcement rule to CLAUDE.md: "Multi-step database operations MUST not call executor.submit() multiple times; keep dependent steps in single task"
  - File: `CLAUDE.md`
  - Append to "Common Patterns" section
  - Include concrete ✅ CORRECT / ❌ WRONG examples

- [ ] T002 Add executor consolidation rule to CLAUDE.md: "All database I/O MUST use DBWriter.getDbExecutor(); no private executor instances"
  - File: `CLAUDE.md`
  - Append to "Common Patterns" section
  - Link to DBWriter.getDbExecutor() in Requirements section

- [ ] T003 Expose DBWriter.getDbExecutor() public static method in DBWriter.java
  - File: `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Add public static method returning dbExec
  - Document: "Single source of truth for all database I/O. Serialized execution prevents race conditions."
  - Mark dbExec as `private static final`

- [ ] T004 Create AsyncOperationAtomicityTest.java test class skeleton
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Import: Robolectric, JUnit 4, CountDownLatch, CyclicBarrier
  - Add test setup: in-memory database, queue initialization
  - Add helper: assertQueueContains(), assertExecutionLogsShowNoInterleaving()

---

## Phase 2: US1 - Prevent Multi-Step Operation Failures (P1)

**Story Goal**: All multi-step database operations execute atomically with automatic rollback on failure

**Independent Test**: 50 concurrent moveQueueItem operations on same episode; verify episode in exactly one queue. Delivers: no queue corruption.

**Acceptance Criteria**:
1. moveQueueItem(E2, Q1→Q2) and removeQueueItem(E1, Q1) concurrent: E2 in Q2 only, E1 removed, no interleaving
2. moveQueueItem with step 2 exception: step 1 rolls back, operationFailed event posted, MoveResult.error=true
3. copyQueueItem(E1, Q1→Q2) concurrent with remove: E1 in both queues, remove succeeds, no corruption

### Implementation Tasks

- [ ] T005 [P] [US1] Refactor DBWriter.moveQueueItem() to atomic executor task
  - File: `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java` (lines ~1700-1750)
  - Wrap entire operation in single dbExec.submit() call
  - Step 1: removeQueueItemInternal(sourceQueueId, item)
  - Step 2: addQueueItemInternal(targetQueueId, item)
  - Step 3: EventBus.post(QueueEvent.moved(...))
  - Return MoveResult(success, removedPos, addedPos)

- [ ] T006 [P] [US1] Implement automatic rollback for moveQueueItem on exception
  - File: `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Add try-catch in executor task
  - On exception: undo step 1 (addQueueItem to restore), post QueueEvent.operationFailed()
  - Return MoveResult(error=true, errorMessage=exception.message)
  - Log: "Move operation rolled back due to: {exception}"

- [ ] T007 [P] [US1] Implement automatic rollback for copyQueueItem on exception
  - File: `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Apply same atomic pattern as moveQueueItem
  - On exception: remove added item, post operationFailed event
  - Return CopyResult(error=true, errorMessage)

- [ ] T008 [US1] Create QueueEvent.operationFailed() event type
  - File: `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`
  - New inner class: `public static class OperationFailed extends QueueEvent`
  - Fields: operationName (String), reason (String)
  - Constructor: public OperationFailed(String operationName, String reason)

- [ ] T009 [US1] Update MoveResult to include error status
  - File: `model/src/main/java/de/danoeh/antennapod/model/MoveResult.java`
  - Add fields: `public final boolean error`, `public final String errorMessage`
  - Update constructor to initialize error status
  - Getter: getError(), getErrorMessage()

- [ ] T010 [US1] Update CopyResult to include error status
  - File: `model/src/main/java/de/danoeh/antennapod/model/CopyResult.java`
  - Add fields: `public final boolean error`, `public final String errorMessage`
  - Update constructor to initialize error status
  - Getter: getError(), getErrorMessage()

### Test Tasks (US1)

- [ ] T011 [US1] Write unit test: moveQueueItem atomicity with concurrent operations
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: moveQueueItem_remainsAtomicWithConcurrentOperations()
  - Setup: Q1=[E1,E2], Q2=[]
  - Concurrent: moveQueueItem(E2, Q1→Q2) + removeQueueItem(E1, Q1)
  - Verify: E2 in Q2 only, E1 removed, counts match
  - Use: CountDownLatch to synchronize threads, CyclicBarrier for start coordination

- [ ] T012 [US1] Write unit test: moveQueueItem rollback on exception
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: moveQueueItem_rollsBackOnException()
  - Mock: addQueueItemInternal to throw exception on step 2
  - Verify: E2 not in Q2, still in Q1, operationFailed event posted, MoveResult.error=true

- [ ] T013 [US1] Write unit test: copyQueueItem atomicity
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: copyQueueItem_remainsAtomicWithConcurrentOperations()
  - Setup: Q1=[E1,E2], Q2=[]
  - Concurrent: copyQueueItem(E1, Q1→Q2) + removeQueueItem(E1, Q1)
  - Verify: E1 in both Q1 and Q2, counts match, no corruption

---

## Phase 3: US2 - Consolidate Executor Services (P1)

**Story Goal**: Single shared executor (DBWriter.dbExec) for all database I/O; remove 4 duplicate executors

**Independent Test**: Scan codebase for ExecutorService/Thread creation; verify all database I/O uses DBWriter.getDbExecutor(). Delivers: all operations globally ordered.

**Acceptance Criteria**:
1. DBWriter exposes getDbExecutor(), all UI layer code uses it
2. PlaybackService removes private dbExecutor, uses shared
3. QueueSelectionDialog, FeedItemMenuHandler, RemoveFromQueueSwipeAction remove private executors

### Implementation Tasks

- [ ] T014 [P] [US2] Remove private dbExecutor from PlaybackService
  - File: `playback/service/src/main/java/de/danoeh/antennapod/playback/service/PlaybackService.java`
  - Find and remove: private static ExecutorService dbExecutor declaration
  - Find and remove: Executors.newSingleThreadExecutor(r → Thread(...)) initialization
  - Search for all dbExecutor.submit() and executor.submit() calls

- [ ] T015 [P] [US2] Update PlaybackService to use DBWriter.getDbExecutor()
  - File: `playback/service/src/main/java/de/danoeh/antennapod/playback/service/PlaybackService.java`
  - Replace all `dbExecutor.submit()` with `DBWriter.getDbExecutor().submit()`
  - Replace all `executor.submit()` with `DBWriter.getDbExecutor().submit()`
  - Verify: no private executor references remain
  - Add import: `import de.danoeh.antennapod.storage.database.DBWriter;`

- [ ] T016 [P] [US2] Remove private executor from QueueSelectionDialog
  - File: `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSelectionDialog.java`
  - Find and remove: private ExecutorService executor declaration
  - Find and remove: Executors.newSingleThreadExecutor() initialization
  - Search for all executor.submit() calls

- [ ] T017 [P] [US2] Remove private executor from FeedItemMenuHandler
  - File: `app/src/main/java/de/danoeh/antennapod/ui/episodeslist/FeedItemMenuHandler.java`
  - Find and remove: private ExecutorService executor declaration
  - Find and remove: Executors.newSingleThreadExecutor() initialization
  - Search for all executor.submit() calls

- [ ] T018 [P] [US2] Remove private executor from RemoveFromQueueSwipeAction
  - File: `app/src/main/java/de/danoeh/antennapod/ui/swipeactions/RemoveFromQueueSwipeAction.java`
  - Find and remove: private ExecutorService executor declaration
  - Find and remove: Executors.newSingleThreadExecutor() initialization
  - Search for all executor.submit() calls

- [ ] T019 [US2] Update 4 UI classes to use DBWriter.getDbExecutor()
  - Files: QueueSelectionDialog, FeedItemMenuHandler, RemoveFromQueueSwipeAction, PlaybackService
  - Replace all `executor.submit()` with `DBWriter.getDbExecutor().submit()`
  - Add imports if needed: `import de.danoeh.antennapod.storage.database.DBWriter;`
  - Verify: 0 private executor/thread instances remain in any class

- [ ] T020 [US2] Remove all executor.shutdown() calls (except app cleanup)
  - File: Find all .shutdown() calls: `grep -r "executor.shutdown()" --include="*.java" storage/ ui/ app/ playback/`
  - Remove from QueueSelectionDialog, FeedItemMenuHandler, RemoveFromQueueSwipeAction, PlaybackService
  - Keep ONLY if in application-level cleanup code (check with CLAUDE.md)
  - Verify: dbExec is never shut down during app lifetime

- [ ] T021 [US2] Codebase scan: verify all database I/O uses shared executor
  - Search for: `Executors.newSingleThreadExecutor()`, `new Thread(`, `new ThreadPoolExecutor(`
  - Excluding: `DBWriter.dbExec`, `intentionally-async` utilities
  - Report findings to code review

### Test Tasks (US2)

- [ ] T022 [US2] Write unit test: all database operations serialized through shared executor
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: dbOperations_executeInOrder_notInterleaved()
  - Submit sequence: add Q1, move Q1→Q2, remove Q2, add Q3
  - Verify: operations execute in submission order (via execution logs)
  - Use: CountDownLatch, execution timestamps

---

## Phase 4: US3 - Eliminate Main Thread Blocking (P1)

**Story Goal**: No `.get()` calls on main thread; all database results delivered via async callbacks (Handler.post, LiveData.postValue)

**Independent Test**: Run UI tests with ANR timeout detection; verify no `.get()` calls block main thread. Delivers: responsive UI.

**Acceptance Criteria**:
1. QueueSelectionDialog.show() uses async callback (Handler.post), not `.get()`
2. FeedItemMenuHandler.moveToQueue() posts result back to main thread
3. All exceptions in Handler.post() propagate through Future (caller handles)

### Implementation Tasks

- [ ] T023 [P] [US3] Refactor QueueSelectionDialog to use async callback instead of .get()
  - File: `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSelectionDialog.java`
  - Find: `executor.submit(() -> DBReader.getAllQueues()).get()`
  - Replace with async pattern:
    ```java
    DBWriter.getDbExecutor().submit(() -> {
        List<QueueMetadata> queues = DBReader.getAllQueues();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> updateQueueList(queues));
    });
    ```
  - Wrap Handler.post() in try-catch; rethrow exception
  - Main thread returns immediately (no blocking)

- [ ] T024 [P] [US3] Refactor FeedItemMenuHandler to use async callback instead of .get()
  - File: `app/src/main/java/de/danoeh/antennapod/ui/episodeslist/FeedItemMenuHandler.java`
  - Find all `.get()` calls on main thread
  - Replace with async callback pattern (Handler.post)
  - Wrap exceptions: if Handler.post() fails, throw RuntimeException back to executor (caller handles)

- [ ] T025 [US3] Add exception handling to async callbacks (Handler.post failure)
  - Files: QueueSelectionDialog, FeedItemMenuHandler
  - Pattern: try-catch around Handler.post(); throw RuntimeException("UI delivery failed", e)
  - Per Clarification Q3: Exception propagates back through Future; caller must handle

- [ ] T026 [US3] Verify no `.get()` calls remain on main thread
  - Search: `grep -r "\.get()" --include="*.java" ui/ app/ playback/` (excluding executor context)
  - Verify: all `.get()` calls are ONLY inside executor.submit() (background thread safe)
  - Report findings

### Test Tasks (US3)

- [ ] T027 [US3] Write unit test: QueueSelectionDialog loads queues asynchronously
  - File: `ui/common/src/test/java/de/danoeh/antennapod/ui/common/QueueSelectionDialogTest.java`
  - Test method: show_loadsQueuesAsynchronously_notBlockingMainThread()
  - Verify: show() returns immediately without calling .get()
  - Mock: DBReader.getAllQueues(), Handler.post()
  - Assert: Handler.post was called with queue list

- [ ] T028 [US3] Write Espresso integration test: UI responsive during database operation
  - File: `app/src/androidTest/java/de/danoeh/antennapod/ui/common/QueueSelectionDialogEspressoTest.java`
  - Test method: openQueueSelectionDialog_UIResponsiveWhileLoadingQueues()
  - Action: Open dialog, trigger queue load
  - Assert: Main thread doesn't block (ANR timeout not triggered)
  - Measure: Time to respond to user input during load

---

## Phase 5: US4 - Fix moveQueueItem Queue Switching (P1)

**Story Goal**: moveQueueItem(Q2, Q3, item) never modifies UserPreferences.currentQueueId

**Independent Test**: moveQueueItem(Q2→Q3, item) with active queue Q1; verify active queue unchanged. Delivers: queue ID consistency.

**Acceptance Criteria**:
1. moveQueueItem never calls setCurrentQueueId()
2. Takes explicit sourceQueueId/targetQueueId (not implicit active queue)
3. Playing episode queue matches active queue after move

### Implementation Tasks

- [ ] T029 [P] [US4] Rewrite DBWriter.moveQueueItem(sourceQueueId, targetQueueId, item) signature
  - File: `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Change signature: `public static Future<MoveResult> moveQueueItem(long sourceQueueId, long targetQueueId, FeedItem item)`
  - Explicit parameters: sourceQueueId and targetQueueId (no implicit active queue)
  - Document: "Never modifies active queue; purely moves episode between two specified queues"

- [ ] T030 [US4] Remove all setCurrentQueueId() calls from moveQueueItem logic
  - File: `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Search moveQueueItem implementation for: `setCurrentQueueId(`, `UserPreferences.setCurrentQueueId(`
  - Remove all occurrences
  - Verify: operation ONLY removes from source queue and adds to target queue

- [ ] T031 [P] [US4] Update moveQueueItem callsites with explicit queue IDs
  - Search: `moveQueueItem(` in all UI classes
  - Files: QueueViewModel, FeedItemMenuHandler, and any other callers
  - Update calls: `moveQueueItem(sourceQueueId, targetQueueId, item)` instead of implicit
  - Verify: all callsites updated

- [ ] T032 [P] [US4] Update copyQueueItem callsites with explicit queue IDs
  - Search: `copyQueueItem(` in all UI classes
  - Apply same pattern: explicit sourceQueueId, targetQueueId parameters
  - Verify: all callsites updated

### Test Tasks (US4)

- [ ] T033 [US4] Write unit test: moveQueueItem never modifies active queue
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: moveQueueItem_neverModifiesActiveQueuePreference()
  - Setup: UserPreferences.setCurrentQueueId(QUEUE_1)
  - Action: moveQueueItem(QUEUE_2, QUEUE_3, item).get()
  - Assert: UserPreferences.getCurrentQueueId() == QUEUE_1 (unchanged)

- [ ] T034 [US4] Write thread-safety test: concurrent move + PlaybackService queue reads
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: moveQueueItem_withConcurrentPlaybackServiceQueReads_noMismatch()
  - Setup: Q1=[E1,E2], Q2=[E3], playing=E1 from Q1
  - Concurrent: moveQueueItem(Q2, Q3, E2) + PlaybackService reads currentQueueId
  - Verify: PlaybackService always reads consistent queue (never intermediate state)
  - Use: CyclicBarrier for thread synchronization

---

## Phase 6: US5 - Correct EventBus queueId Inclusion (P2)

**Story Goal**: EventBus events include queueId ONLY for operations affecting non-active queues

**Independent Test**: Audit all EventBus.post() calls; verify queueId only in moved/deleted/created/switched. Delivers: efficient UI updates.

**Acceptance Criteria**:
1. QueueEvent.added(), removed(), cleared(), sorted() DO NOT include queueId
2. QueueEvent.moved() INCLUDES sourceId, targetId
3. QueueEvent.queueCreated(), deleted(), switched() INCLUDE queueId

### Implementation Tasks

- [ ] T035 [P] [US5] Audit all DBWriter EventBus.post() calls
  - File: `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Find all: `EventBus.getDefault().post(QueueEvent.*)`
  - Document: which events include queueId, which should be removed
  - Identify lines requiring changes

- [ ] T036 [US5] Remove queueId from QueueEvent.added() constructor
  - File: `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`
  - Remove queueId parameter from: `public QueueEvent added(FeedItem item)` signature
  - Update all call sites: DBWriter.post(QueueEvent.added(item)) [no queueId]

- [ ] T037 [US5] Remove queueId from QueueEvent.removed() constructor
  - File: `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`
  - Remove queueId parameter from: `public QueueEvent removed(FeedItem item)` signature
  - Update all call sites in DBWriter

- [ ] T038 [US5] Remove queueId from QueueEvent.cleared() constructor
  - File: `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`
  - Remove queueId parameter
  - Update all call sites

- [ ] T039 [US5] Remove queueId from QueueEvent.sorted() constructor
  - File: `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`
  - Remove queueId parameter
  - Update all call sites

- [ ] T040 [P] [US5] Verify queueId present in QueueEvent.moved()
  - File: `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`
  - Verify: public QueueEvent moved(long sourceQueueId, long targetQueueId, FeedItem item)
  - Ensure fields: sourceQueueId, targetQueueId stored
  - Verify DBWriter calls include both IDs

- [ ] T041 [P] [US5] Verify queueId present in QueueEvent.queueCreated()
  - File: `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`
  - Verify: includes queueId parameter
  - Update DBWriter call sites if missing

- [ ] T042 [P] [US5] Verify queueId present in QueueEvent.deleted(), switched()
  - File: `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`
  - Verify: both events include queueId
  - Update DBWriter call sites

- [ ] T043 [US5] Update QueueViewModel subscribers to handle new event structure
  - File: `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java`
  - Find all @Subscribe methods listening to QueueEvent
  - Remove: queueId extraction from added, removed, cleared, sorted events
  - Add: queueId extraction from moved, deleted, created, switched events
  - Update: UI refresh logic (single-queue events: reload current queue; cross-queue events: reload specified queues)

### Test Tasks (US5)

- [ ] T044 [US5] Write unit test: QueueEvent field verification
  - File: `event/src/test/java/de/danoeh/antennapod/event/QueueEventTest.java`
  - Test method: eventFields_matchSpecification()
  - Verify: added(), removed(), cleared(), sorted() DO NOT have queueId field
  - Verify: moved() HAS sourceQueueId, targetQueueId
  - Verify: queueCreated(), deleted(), switched() HAVE queueId
  - Use reflection to check field presence

---

## Phase 7: Polish & Merge Gate Validation

**Objective**: Comprehensive testing, code review, merge gate validation

### Regression Test Tasks

- [ ] T045 [P] Write concurrent operation test: 100 moveQueueItem operations
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: moveQueueItem_100ConcurrentOperations_100PercentConsistency()
  - Setup: Q1=[E1..E100], Q2=[]
  - Action: Submit 100 concurrent moveQueueItem(E_i, Q1→Q2)
  - Verify: All 100 episodes in Q2, Q1 empty, counts match exactly
  - Use: CountDownLatch(100) for start coordination, CyclicBarrier for final check
  - Log execution timestamps; verify no interleaving of operation steps

- [ ] T046 [P] Write concurrent operation test: 100 copyQueueItem operations
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: copyQueueItem_100ConcurrentOperations_100PercentConsistency()
  - Setup: Q1=[E1..E50], Q2=[]
  - Action: Submit 100 concurrent copyQueueItem (each episode copied twice to Q2)
  - Verify: All episodes in both Q1 and Q2, counts match (Q1: 50, Q2: 100)
  - Log timestamps; verify no interleaving

- [ ] T047 [P] Write concurrent operation test: 100 removeQueueItem operations
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: removeQueueItem_100ConcurrentOperations_100PercentConsistency()
  - Setup: Q1=[E1..E100]
  - Action: Submit 100 concurrent removeQueueItem(E_i, Q1)
  - Verify: Q1 empty, all episodes removed exactly once, counts match
  - Log timestamps; verify no interleaving

- [ ] T048 [P] Execute and verify execution log analysis
  - File: `storage/database/src/test/java/de/danoeh/antennapod/storage/database/AsyncOperationAtomicityTest.java`
  - Test method: concurrentOperations_haveNoInterleavingOfAtomicSteps()
  - Analyze logs from T045, T046, T047
  - Verify: For each operation, all steps execute consecutively before next operation begins
  - Report: 0 interleaving detected (100% serialization)

### Code Review Tasks

- [ ] T049 [P] Code review: US1 atomicity patterns
  - Reviewers: 1 (architect)
  - Files: DBWriter.moveQueueItem(), copyQueueItem(), rollback logic, event posting
  - Checklist:
    - [ ] All multi-step operations in single dbExec.submit() task
    - [ ] Automatic rollback on exception
    - [ ] QueueEvent.operationFailed() posted on failure
    - [ ] MoveResult/CopyResult include error status
    - [ ] No executor.shutdown() calls
  - Approval required before Phase 7 completion

- [ ] T050 [P] Code review: US2 executor consolidation
  - Reviewers: 1 (architect)
  - Files: PlaybackService, QueueSelectionDialog, FeedItemMenuHandler, RemoveFromQueueSwipeAction, DBWriter.getDbExecutor()
  - Checklist:
    - [ ] 0 private executor instances remain
    - [ ] All database I/O uses DBWriter.getDbExecutor()
    - [ ] No executor.shutdown() except app cleanup
    - [ ] Codebase scan confirms single executor usage
  - Approval required

- [ ] T051 [P] Code review: US3 async patterns
  - Reviewers: 1 (Android expert)
  - Files: QueueSelectionDialog, FeedItemMenuHandler async callbacks
  - Checklist:
    - [ ] No `.get()` on main thread
    - [ ] Handler.post() exceptions thrown back through Future
    - [ ] Main thread returns immediately (no blocking)
  - Approval required

### Merge Gate Validation

- [ ] T052 Run complete test suite
  - Command: `./gradlew testPlayDebugUnitTest testDebugUnitTest`
  - Verify: All tests pass (existing + new concurrent operation tests)
  - Report: Test count, pass/fail summary

- [ ] T053 Run static analysis
  - Command: `./gradlew checkstyle :app:lintPlayDebug spotbugsPlayDebug spotbugsDebug`
  - Verify: 0 checkstyle violations, 0 lint errors, 0 spotbugs violations
  - Report: Summary

- [ ] T054 Verify merge gate criteria met
  - Checklist:
    - [ ] All 46 tasks complete
    - [ ] T045, T046, T047: 100% consistency verified
    - [ ] T048: Zero interleaving verified
    - [ ] T049, T050, T051: Code review approvals obtained
    - [ ] T052: All tests passing
    - [ ] T053: Static analysis passing
  - Sign-off: [name], date

- [ ] T055 Final commit: mark async-reliability phase complete
  - Message: "feat: Complete async reliability & executor consolidation (all 6 phases, 46 tasks, merge gate passed)"
  - Include: Test summary, concurrent operation results, code review approvals

---

## Task Summary

| Phase | Story | Task Count | Key Metrics |
|-------|-------|-----------|-------------|
| 1 | Setup | 4 | getDbExecutor() exposed, CLAUDE.md rules added |
| 2 | US1 | 13 | moveQueueItem/copyQueueItem atomic, rollback tested |
| 3 | US2 | 9 | Single executor, 0 private instances |
| 4 | US3 | 8 | No `.get()` on main thread, async callbacks tested |
| 5 | US4 | 6 | moveQueueItem never modifies active queue, tested |
| 6 | US5 | 10 | EventBus queueId fields corrected, audited |
| 7 | Polish | 11 | 100 concurrent ops tested, code review approved |
| **TOTAL** | | **46** | **Merge gate passed** |

---

## Parallel Execution Opportunities

**Can execute in parallel (after Phase 1)**:
- US1 atomicity: moveQueueItem + copyQueueItem + event types (3 developers)
- US2 executor removal: PlaybackService + 3 UI classes (4 developers)
- US3 callbacks: QueueSelectionDialog + FeedItemMenuHandler (2 developers)
- US4 signature changes: moveQueueItem + copyQueueItem callsites (2 developers)
- US5 event audit: event types + subscriber updates (2 developers)

**Suggested allocation**: 4 developers, 3-4 weeks (with parallelization)
- Developer A: US1 atomicity
- Developer B: US2 executors (2 classes) + US3 callbacks (1 class)
- Developer C: US3 callbacks (1 class) + US4 signature changes
- Developer D: US5 event audit

---

## MVP Scope

**Minimum Viable Product** (Phase 1 + Phase 2 only):
- Establish atomicity pattern + expose getDbExecutor()
- Refactor moveQueueItem/copyQueueItem to atomic operations with rollback
- Basic concurrent operation testing

**Deliverable**: Foundation for all other fixes. After MVP, can proceed with US2-5 and Phase 7 testing.

**Timeline**: 1 week for MVP (with 2 developers)
