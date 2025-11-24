# Plan: Async Reliability & Executor Consolidation

## Overview
Fix architectural issues with async operations, thread safety, and executor management before merging develop branch. Addresses 4 critical problems: async operation failures, executor duplication, moveQueueItem queue switching, and EventBus information consistency.

## Clarifications

### Session 2025-11-24

- Q1: Error recovery for failed atomic operations → A: Automatic rollback within executor (abort steps 2+, undo step 1, post operationFailed event)
- Q2: Merge gating & race condition metrics → A: All 6 phases required before merge to develop. Metrics: 100 concurrent operations across 3 scenarios (move, copy, remove) with 100% database consistency + execution log verification (no interleaving)
- Q3: Handler.post() failure in async callbacks → A: Throw exception back through Future (caller must handle). Propagates error; burden on caller but prevents silent UI failures

---

## Phase 1: Establish Atomicity Enforcement Rule

**Problem:** Multi-step operations (remove from Q1 → add to Q2) were split across separate executor submissions, allowing failures/interleaving/crashes.

**Solution:** Create a pattern rule that ALL multi-step database operations must execute atomically within a single executor task.

**Concrete Implementation:**
```java
// ❌ WRONG (current pattern causing failures):
executor.submit(() -> removeQueueItem(item));  // Step 1
executor.submit(() -> addQueueItem(item));    // Step 2 - not guaranteed to follow Step 1

// ✅ CORRECT (atomic pattern):
executor.submit(() -> {
    removeQueueItem(item);  // Both steps
    addQueueItem(item);     // execute together
    return result;          // Return status
});
```

**Enforcement Mechanism:**
- Add rule to CLAUDE.md: "Multi-step database operations must not call `executor.submit()` multiple times"
- Use `Futures.whenAllComplete()` or `CompletableFuture.thenApply()` only when steps are independent
- When steps are dependent (output of step 1 feeds into step 2), keep in same `submit()` call

**Error Recovery (Clarification Q1):**
- If any step throws exception: automatically undo previous steps (rollback) within same executor task
- Post `QueueEvent.operationFailed(operationName, reason)` to notify UI
- Return error status in operation result (e.g., `MoveResult.error = true`)
- Never leave database in partially-updated state (both queues modified, neither, etc.)

**Benefits:**
- Prevents race conditions between steps
- Eliminates possibility of half-completed operations
- One commit = one atomic unit
- Automatic rollback guarantees consistent state on failure

---

## Phase 2: Fix moveQueueItem - Remove Queue Switching

**Current Problem:** Switches active queue temporarily, causing:
- PlaybackService to read inconsistent state
- Active queue ID ≠ playing episode's queue
- Race with any concurrent queue switches

**New Implementation:**
```java
// DBWriter.moveQueueItem(sourceQueueId, targetQueueId, feedItem)
public static Future<MoveResult> moveQueueItem(long sourceQueueId, long targetQueueId, FeedItem item) {
    return dbExec.submit(() -> {
        // NEVER touch active queue - just move the episode

        // Step 1: Remove from source queue (if not same as target)
        if (sourceQueueId != targetQueueId) {
            removeQueueItemInternal(sourceQueueId, item);
        }

        // Step 2: Add to target queue
        addQueueItemInternal(targetQueueId, item);

        // Step 3: Post single event with both queue IDs
        QueueEvent.moved(sourceQueueId, targetQueueId, item);

        return new MoveResult(success, removedPosition, addedPosition);
    });
}
```

**Key Changes:**
- Takes explicit `sourceQueueId` and `targetQueueId` parameters (no implicit active queue)
- Never calls `setCurrentQueueId()`
- Atomic within single executor task
- Event includes both queue IDs (only needed for cross-queue move)

**Affected Files:**
- `DBWriter.moveQueueItem()` - rewrite logic
- `DBWriter.copyQueueItem()` - apply same pattern
- All callers in UI layer - add both queue IDs to call site
- Tests: `DBWriterQueueTransferTest.java` - update to validate active queue doesn't change

---

## Phase 3: Evaluate EventBus queueId Inclusion

**Decision Tree:** Include queueId in QueueEvent ONLY if operation affects non-active queue.

| Event | Typical Use | Affects Active Queue? | Include queueId? | Reason |
|-------|------------|----------------------|------------------|--------|
| `added(item)` | Add to current queue | Yes | **No** | Implicit: affected queue is active queue |
| `removed(item)` | Remove from current queue | Yes | **No** | Implicit: affected queue is active queue |
| `cleared()` | Clear current queue | Yes | **No** | Implicit: affected queue is active queue |
| `sorted()` | Reorder current queue | Yes | **No** | Implicit: affected queue is active queue |
| `moved(item, sourceId, targetId)` | Move between queues | Sometimes | **Yes** | May affect non-active queues; subscribers need to know which |
| `queueDeleted(queueId)` | Delete a queue | N/A | **Yes** | Must delete from non-active queue; delete UI must update |
| `queueCreated(queueId)` | Create a queue | N/A | **Yes** | New queue appears; list must update |
| `QUEUE_SWITCHED` | Switch active queue | N/A | **Yes** | Different queue is now active; all subscribers need new ID |

**Implementation:**
- Keep queueId in: `moved()`, `queueDeleted()`, `queueCreated()`, `QUEUE_SWITCHED`
- Remove queueId from: `added()`, `removed()`, `cleared()`, `sorted()`
- Cleanup: Remove unused queueId parameters from event constructors

**Files to Update:**
- `event/QueueEvent.java` - adjust event class constructors
- `storage/database/DBWriter.java` - audit all EventBus.post() calls
- `ui/common/QueueViewModel.java` - update subscribers to not expect queueId for single-queue events
- Tests: Ensure events don't carry unnecessary data

---

## Phase 4: Consolidate ExecutorServices

**Current State (Duplication):**
```
DBWriter.dbExec (single-threaded, MIN_PRIORITY) ← SINGLE SOURCE OF TRUTH
├─ PlaybackService.dbExecutor (separate, duplicate)
├─ QueueSelectionDialog.executor (separate, duplicate)
├─ FeedItemMenuHandler.executor (separate, duplicate)
└─ RemoveFromQueueSwipeAction.executor (separate, duplicate)
```

**Problem:** Operations not serialized across executors → race conditions.

**Solution: Expose DBWriter.dbExec and use everywhere**

```java
// DBWriter.java
public static ExecutorService getDbExecutor() {
    return dbExec;
}
```

**Update All UI Layer Code:**

1. **QueueSelectionDialog** (currently: `executor.submit(() -> DBReader.getAllQueues()).get()`)
   ```java
   // BEFORE (blocking main thread):
   List<QueueMetadata> queues = DBWriter.dbExec.submit(() -> DBReader.getAllQueues()).get();

   // AFTER (async pattern):
   DBWriter.getDbExecutor().submit(() -> {
       List<QueueMetadata> queues = DBReader.getAllQueues();
       Handler mainHandler = new Handler(Looper.getMainLooper());
       mainHandler.post(() -> updateQueueList(queues));  // Update UI on main thread
   });
   ```

2. **FeedItemMenuHandler** - same pattern: submit async, post result back to main thread

3. **PlaybackService** - remove private `dbExecutor`, use `DBWriter.getDbExecutor()` instead

4. **RemoveFromQueueSwipeAction** - consolidate to shared executor

**Safety Check:**
- Verify all database calls go through `DBWriter.getDbExecutor()`
- No direct thread creation for database operations
- Remove all `executor.shutdown()` calls (executor is application-lifetime)

**Files to Update:**
- `storage/database/DBWriter.java` - add getter, mark dbExec as static final
- `playback/service/PlaybackService.java` - remove private dbExecutor
- `ui/common/QueueSelectionDialog.java` - use DBWriter.getDbExecutor()
- `ui/common/FeedItemMenuHandler.java` - use DBWriter.getDbExecutor()
- `ui/common/RemoveFromQueueSwipeAction.java` - use DBWriter.getDbExecutor()
- `CLAUDE.md` - add rule: "All database operations use `DBWriter.getDbExecutor()`"

---

## Phase 5: Async Pattern Enforcement

**Rule:** Never call `.get()` on main thread. Use callbacks or LiveData instead.

**Patterns:**

**Pattern A: Fire-and-forget** (operation complete, no result needed)
```java
DBWriter.getDbExecutor().submit(() -> {
    DBWriter.removeQueueItem(item);
    EventBus.getDefault().post(new QueueEvent.removed(item));
});
```

**Pattern B: Result callback** (with exception handling per Clarification Q3)
```java
DBWriter.getDbExecutor().submit(() -> {
    try {
        MoveResult result = DBWriter.moveQueueItem(sourceId, targetId, item).get();  // OK to block here, IN executor
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            if (result.success) {
                showSnackbar("Moved to queue");
            } else {
                showSnackbar("Failed: " + result.error);
            }
        });
    } catch (Exception e) {
        // Clarification Q3: Throw back to caller via Future
        // Caller must catch this exception and handle UI failure
        throw new RuntimeException("UI update failed for moveQueueItem", e);
    }
});
```

**Pattern C: LiveData (preferred for ViewModel)** (with exception handling per Clarification Q3)
```java
// In ViewModel:
private final LiveData<List<QueueMetadata>> queues = new MutableLiveData<>();
private final LiveData<String> error = new MutableLiveData<>();

public void loadQueues() {
    DBWriter.getDbExecutor().submit(() -> {
        try {
            List<QueueMetadata> data = DBReader.getAllQueues();
            ((MutableLiveData<List<QueueMetadata>>) queues).postValue(data);
        } catch (Exception e) {
            // Clarification Q3: Throw exception back; caller (Fragment) must handle
            ((MutableLiveData<String>) error).postValue("Failed to load queues: " + e.getMessage());
        }
    });
}

// In UI:
viewModel.getQueues().observe(this, queueList -> updateUI(queueList));
viewModel.getError().observe(this, errorMsg -> showError(errorMsg));
```

**Exception Handling (Clarification Q3):**
- If `Handler.post()` fails or main thread is unresponsive, throw exception back through Future
- Caller must wrap `.get()` in try-catch (only safe inside executor task)
- Prevents silent UI failures (caller knows operation result)
- Burden on caller but explicit error handling

**Enforcement in Code Review:**
- Flag any `.get()` calls on main thread
- Flag any `new Thread()` or `Executors.newXyz()` outside DBWriter
- Flag any database read/write outside executor
- Verify Pattern B & C wrap in try-catch with exception rethrow

---

## Phase 6: Regression Test Framework

**New Test Class:** `AsyncOperationAtomicityTest.java`

Test Patterns:

**Test 1: Multi-step operation atomicity under concurrent operations**
```java
@Test
public void moveQueueItem_remainsAtomicWithConcurrentOperations() {
    // Setup: Q1=[E1,E2], Q2=[]
    // Operation: Move E2 from Q1 to Q2
    // Concurrent: Remove E1 from Q1

    // Verify: E2 is in Q2 only (not in both), E1 is removed from Q1
    // NOT: E2 temporarily in both queues during move
}
```

**Test 2: Active queue never changes during cross-queue operation**
```java
@Test
public void moveQueueItem_neverModifiesActiveQueuePreference() {
    UserPreferences.setCurrentQueueId(QUEUE_1);

    moveQueueItem(QUEUE_2, QUEUE_3, item).get();

    assertEquals("Active queue should remain Q1", QUEUE_1,
                 UserPreferences.getCurrentQueueId());
}
```

**Test 3: Executor serialization prevents interleaving**
```java
@Test
public void dbOperations_executeInOrder_notInterleaved() {
    // Submit multiple operations in sequence
    // Verify they complete in order: add Q1, move to Q2, remove Q2, add Q3
    // NOT: operations complete in random order or overlap
}
```

**Test 4: Event consistency matches database state**
```java
@Test
public void queueEvent_reflectsActualDatabaseState() {
    // After moveQueueItem(Q1 → Q2, item), verify:
    // - QueueEvent.moved(Q1, Q2) is posted
    // - queueId includes Q1 and Q2
    // - Event reflects actual queue contents
}
```

**Files to Create:**
- `storage/database/AsyncOperationAtomicityTest.java` - test suite
- Update existing tests to verify executor consolidation

---

## Implementation Sequence (Clarification Q2: All 6 Phases Required Before Merge)

1. **Phase 1 Week:**
   - Add atomicity rule to CLAUDE.md
   - Expose DBWriter.getDbExecutor()
   - Update all UI layer calls to use shared executor

2. **Phase 2 Week:**
   - Fix moveQueueItem (remove queue switching)
   - Update all cross-queue operation callsites
   - Audit EventBus posts for queueId inclusion

3. **Phase 3 Week:**
   - Remove unused queueId parameters from events
   - Implement async callback patterns in UI layer
   - Remove all `.get()` calls from main thread

4. **Phase 4 Week:**
   - Consolidate ExecutorServices: remove duplicates, expose DBWriter.getDbExecutor()
   - Update all callers (PlaybackService, dialogs, handlers, swipe actions)
   - CLAUDE.md rule: all database operations use shared executor

5. **Phase 5 Week:**
   - Document async patterns (fire-and-forget, callback, LiveData)
   - Implement async callbacks in QueueSelectionDialog, FeedItemMenuHandler
   - Remove all `.get()` calls from main thread
   - Code review: flag violations

6. **Phase 6 Week (Required before merge):**
   - Create regression test framework: AsyncOperationAtomicityTest.java
   - Implement 4 test patterns with concurrent operations
   - Run 100 concurrent operations across 3 scenarios (move, copy, remove)
   - Verify 100% database consistency + execution log verification
   - Code review and validation

**Merge Gate:** Only merge to develop after ALL 6 phases complete and all race condition tests pass with 100% consistency.

---

## Key Metrics for Success (Merge Gating Criteria from Q2)

- ✓ All database operations serialized through single executor
- ✓ No `.get()` calls block main thread
- ✓ moveQueueItem never changes active queue
- ✓ EventBus events carry only necessary queueId info
- ✓ All multi-step operations execute as single units

### Race Condition Test Metrics (Clarification Q2)

- ✓ 100 concurrent operations across 3 scenarios (move, copy, remove)
- ✓ 100% database consistency verification (episodes in correct queues, counts match)
- ✓ Execution log verification (no interleaving of atomic operation steps)
- ✓ All 6 phases complete before merge to develop
