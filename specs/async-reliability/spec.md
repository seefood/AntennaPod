# Feature Specification: Async Reliability & Executor Consolidation

**Feature Branch**: `async-reliability` (integrate into `001-smart-queues`)
**Created**: 2025-11-24
**Status**: Clarified
**Input**: Analysis of develop → 001-smart-queues merge identified 4 critical thread safety violations and EventBus inconsistencies

## Clarifications

### Session 2025-11-24

- Q1: Error recovery for failed atomic operations → A: Automatic rollback within executor (abort steps 2+, undo step 1, post operationFailed event)
- Q2: Merge gating & race condition metrics → A: All 6 phases required before merge to develop. Metrics: 100 concurrent operations across 3 scenarios (move, copy, remove) with 100% database consistency + execution log verification (no interleaving)
- Q3: Handler.post() failure in async callbacks → A: Throw exception back through Future (caller must handle). Propagates error; burden on caller but prevents silent UI failures

---

## User Scenarios & Testing

### User Story 1 - Prevent Multi-Step Operation Failures (Priority: P1)

**Scenario**: Developers implement queue episode transfers (move from Q1 to Q2). Currently, operations split across multiple executor.submit() calls can interleave with concurrent operations, leaving episodes in inconsistent state (in both queues, neither queue, or duplicated).

**Why this priority**: This is the root cause of mysterious queue corruption bugs. Multi-step atomicity is foundational—all queue operations depend on it. Without this, any concurrent database operation risks data corruption.

**Independent Test**: Can be tested by submitting 50 concurrent moveQueueItem operations on the same episode; verify it ends in exactly one queue. Delivers guarantee: no corrupted queue state.

**Acceptance Scenarios**:

1. **Given** Q1=[E1,E2], Q2=[], **When** moveQueueItem(E2, Q1→Q2) AND removeQueueItem(E1, Q1) execute concurrently, **Then** E2 in Q2 only, E1 removed from Q1, no interleaving of steps
2. **Given** moveQueueItem starts, **When** step 1 succeeds but step 2 throws exception, **Then** step 1 automatically rolls back, operationFailed event posted, MoveResult.error=true
3. **Given** copyQueueItem(E1, Q1→Q2), **When** both copy and remove execute concurrently, **Then** E1 in both queues (copy succeeded), remove succeeds independently, no state corruption

---

### User Story 2 - Consolidate Executor Services (Priority: P1)

**Scenario**: Current codebase has 5 separate executor instances (DBWriter.dbExec, PlaybackService.dbExecutor, QueueSelectionDialog.executor, FeedItemMenuHandler.executor, RemoveFromQueueSwipeAction.executor). Operations submitted to different executors are NOT serialized, causing race conditions when one executor makes queue change while another is in progress.

**Why this priority**: Race conditions between executors are impossible to predict/debug. Single shared executor enforces global ordering. Critical for correctness.

**Independent Test**: Can be tested by scanning codebase for ExecutorService/Thread creation; verify all database I/O uses DBWriter.getDbExecutor(). Delivers: all operations globally ordered.

**Acceptance Scenarios**:

1. **Given** DBWriter exposes getDbExecutor(), **When** all UI layer code uses it, **Then** no duplicate executors exist, all operations serialized
2. **Given** PlaybackService previously had private dbExecutor, **When** refactored, **Then** uses DBWriter.getDbExecutor() instead
3. **Given** QueueSelectionDialog, FeedItemMenuHandler, RemoveFromQueueSwipeAction all call database, **When** all use shared executor, **Then** operations execute in submission order, no interleaving

---

### User Story 3 - Eliminate Main Thread Blocking (Priority: P1)

**Scenario**: QueueSelectionDialog and FeedItemMenuHandler call `.get()` on Future objects from main thread, blocking UI during database operations. Can trigger ANR (Application Not Responding) errors.

**Why this priority**: ANR crashes app from user perspective. Blocking main thread violates Android architecture guidelines. Critical for usability.

**Independent Test**: Can be tested by running UI tests with ANR timeout detection; verify no `.get()` calls on main thread. Delivers: responsive UI during all database operations.

**Acceptance Scenarios**:

1. **Given** QueueSelectionDialog.show(), **When** loading queue list, **Then** uses async callback (Handler.post), not `.get()`, UI remains responsive
2. **Given** FeedItemMenuHandler.moveToQueue(target), **When** database operation runs, **Then** posts result back to main thread via Handler, doesn't block
3. **Given** all async patterns, **When** exception occurs in database operation, **Then** thrown back through Future (caller handles), doesn't silently fail

---

### User Story 4 - Fix moveQueueItem Queue Switching (Priority: P1)

**Scenario**: moveQueueItem(Q2, Q3, item) temporarily calls setCurrentQueueId(Q2) → setCurrentQueueId(Q3) → restore, modifying UserPreferences. PlaybackService can read intermediate states, causing playing episode ID to mismatch active queue ID.

**Why this priority**: This breaks fundamental assumption: currently-playing episode belongs to active queue. When mismatch occurs, queue operations fail silently or corrupt playback state. Critical architectural invariant.

**Independent Test**: Can be tested by moveQueueItem(Q2→Q3) while monitoring UserPreferences.getCurrentQueueId(); verify it never changes from initial value. Delivers: queue ID always consistent with playing episode.

**Acceptance Scenarios**:

1. **Given** currentQueueId=Q1, **When** moveQueueItem(Q2, Q3, item) executes, **Then** currentQueueId remains Q1 (unchanged)
2. **Given** moveQueueItem removes explicit sourceQueueId/targetQueueId parameters, **When** UI calls it, **Then** operation doesn't touch active queue, purely moves episode between two specified queues
3. **Given** move succeeds, **When** PlaybackService checks playing episode queue, **Then** it still matches active queue

---

### User Story 5 - Correct EventBus queueId Inclusion (Priority: P2)

**Scenario**: QueueEvent.added(), removed(), cleared(), sorted() posts include queueId parameter but all operate on active queue only. Subscribers unnecessarily reload all queues. QueueEvent.moved() doesn't include queueId but affects non-active queues, so subscribers can't determine which queues changed.

**Why this priority**: Performance regression for multi-queue UI (reloading 100 queues on every add). Information loss (moved events don't say which queues). Medium impact but should be fixed systematically.

**Independent Test**: Can be tested by auditing all EventBus.post() calls; verify queueId only included for events affecting non-active queues (moved, deleted, created, switched). Delivers: subscribers know exactly which queue changed, load efficiently.

**Acceptance Scenarios**:

1. **Given** QueueEvent.added(item), **When** posted, **Then** queueId NOT included (implicit: current active queue), subscribers don't need queueId
2. **Given** QueueEvent.moved(sourceId, targetId, item), **When** posted, **Then** includes both queue IDs, subscribers know which queues affected
3. **Given** QueueEvent.queueCreated(queueId), **When** posted, **Then** includes queueId, queue list UI updates only that queue

---

### Edge Cases

- **Atomicity failure mid-operation**: Step 1 succeeds, step 2 throws exception → automatic rollback must undo step 1 within same executor task, not leave partial state
- **Executor shutdown during operation**: Remove all executor.shutdown() calls; executor is application-lifetime resource
- **Handler.post() fails in async callback**: Exception must propagate back through Future (caller must catch), not silently fail
- **Concurrent queue switches + episode move**: PlaybackService reads intermediate queue IDs during move → never happens because move doesn't modify active queue
- **Empty queue during refill**: If automatic queue refill adds zero episodes, playback must gracefully stop (not crash)
- **Database I/O on main thread**: Any database read/write on main thread must be detected in code review and fixed (use executor)

---

## Requirements

### Functional Requirements

- **FR-001**: System MUST execute all multi-step database operations (e.g., remove-then-add for moveQueueItem) atomically within a single executor task, never split across multiple submit() calls
- **FR-002**: System MUST provide single shared executor for all database I/O via DBWriter.getDbExecutor(), eliminating duplicate executor instances
- **FR-003**: System MUST remove private dbExecutor from PlaybackService, QueueSelectionDialog, FeedItemMenuHandler, RemoveFromQueueSwipeAction; all must use DBWriter.getDbExecutor()
- **FR-004**: System MUST NOT call `.get()` on main thread; all database operations must use async callbacks (Handler.post, LiveData.postValue) to post results back to main thread
- **FR-005**: moveQueueItem(sourceQueueId, targetQueueId, item) MUST accept explicit queue IDs and MUST NOT call setCurrentQueueId() at any point
- **FR-006**: EventBus events MUST include queueId only for operations affecting non-active queues (moved, deleted, created, switched); omit queueId from added, removed, cleared, sorted
- **FR-007**: If any step in multi-step atomic operation throws exception, system MUST automatically rollback all previous steps within same executor task and post QueueEvent.operationFailed()
- **FR-008**: Exception in Handler.post() or main thread delivery MUST propagate back through Future; caller (on executor thread) must catch and handle, preventing silent UI failure
- **FR-009**: All database operations MUST use MIN_PRIORITY threads to avoid starving UI thread
- **FR-010**: No executor.shutdown() calls except application-level cleanup (executors are application-lifetime resources)

### Key Entities

- **DatabaseExecutor** (singleton, MIN_PRIORITY): Single source of truth for all database I/O. Exported via DBWriter.getDbExecutor(). Serializes all operations to enforce atomicity and ordering.
- **MoveResult / CopyResult**: Return objects for multi-step operations. Include success boolean, error message, removed position, added position. Propagate rollback failures.
- **QueueEvent** variants: added, removed, cleared, sorted (no queueId), moved (includes sourceId/targetId), deleted, created, switched (include queueId), operationFailed (new event type for rollback failures)

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100 concurrent moveQueueItem operations with 100% database consistency (all episodes in correct queues, no duplicates/missing, counts match)
- **SC-002**: 100 concurrent copyQueueItem operations with zero state corruption (episodes present in both source+target queues as expected)
- **SC-003**: 100 concurrent removeQueueItem operations with zero state corruption (episodes removed exactly once, counts accurate)
- **SC-004**: Execution logs for concurrent operations show zero interleaving of atomic operation steps (all steps of one operation complete before next operation begins)
- **SC-005**: Codebase contains exactly one DBWriter.dbExec instance; all database I/O uses it via getDbExecutor(); no duplicates detected in scanning
- **SC-006**: Zero `.get()` calls on main thread (code review + static analysis); all database results delivered via Handler.post() or LiveData
- **SC-007**: moveQueueItem(Q2, Q3, item) with active queue Q1: after operation completes, active queue is still Q1 (verified in test + thread-safety test)
- **SC-008**: All EventBus events audited; queueId present only in moved/deleted/created/switched; absent from added/removed/cleared/sorted
- **SC-009**: Failed atomic operations (step 2 exception) trigger automatic rollback; QueueEvent.operationFailed posted; MoveResult/CopyResult.error=true
- **SC-010**: All 6 phases complete (Atomicity Rule, moveQueueItem Fix, EventBus Audit, Executor Consolidation, Async Pattern Enforcement, Regression Test Framework); branch passes all checks before merge to develop

---

## Architecture & Design Notes

### Atomicity Pattern

All multi-step operations execute within single dbExecutor.submit() call:

```java
dbExec.submit(() -> {
    try {
        // Step 1
        removeQueueItemInternal(sourceQueueId, item);
        // Step 2
        addQueueItemInternal(targetQueueId, item);
        // Step 3
        postEvent(success);
        return result;
    } catch (Exception e) {
        // Automatic rollback: undo step 1
        undoStep(item);
        postEvent(operationFailed);
        return errorResult;
    }
});
```

### Executor Consolidation

Single `DBWriter.dbExec` (single-threaded, MIN_PRIORITY) is source of truth. Exposed via public static getter:

```java
public static ExecutorService getDbExecutor() {
    return dbExec;
}
```

All database I/O routes through this executor.

### Async Patterns

**Pattern A: Fire-and-forget**
```java
DBWriter.getDbExecutor().submit(() -> {
    DBWriter.removeQueueItem(item);
    EventBus.post(QueueEvent.removed(item));
});
```

**Pattern B: Result callback**
```java
DBWriter.getDbExecutor().submit(() -> {
    try {
        MoveResult result = DBWriter.moveQueueItem(...).get();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> updateUI(result));
    } catch (Exception e) {
        throw new RuntimeException("UI delivery failed", e);
    }
});
```

**Pattern C: LiveData**
```java
viewModel.loadQueues(); // ViewModel submits to executor, posts value on main thread
liveData.observe(this, queues -> updateUI(queues));
```

---

## Phases (from Clarification Q2)

All 6 phases required before merge to develop:

1. **Phase 1**: Establish atomicity enforcement rule in CLAUDE.md; expose DBWriter.getDbExecutor()
2. **Phase 2**: Fix moveQueueItem (remove queue switching); audit callsites
3. **Phase 3**: Evaluate EventBus queueId inclusion; remove unnecessary parameters
4. **Phase 4**: Consolidate ExecutorServices (remove duplicates, use shared executor)
5. **Phase 5**: Document async patterns; implement callbacks in UI layer; remove `.get()` from main thread
6. **Phase 6**: Create regression test framework (AsyncOperationAtomicityTest); run 100-concurrent-ops tests; achieve 100% consistency verification

---

## Merge Gate

Only merge to develop after:
- ✓ All 6 phases complete
- ✓ 100 concurrent operations across 3 scenarios (move, copy, remove) pass with 100% database consistency
- ✓ Execution logs show zero operation interleaving
- ✓ Code review approved
