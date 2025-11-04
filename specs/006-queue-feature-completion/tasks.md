---

description: "Task list for Phase 6: Queue Feature Completion"
---

# Tasks: Queue Feature Completion

**Input**: Design documents from `/specs/006-queue-feature-completion/`
**Prerequisites**: plan.md, spec.md (Phases 1-5 already complete)

**Tests**: No new tests required - this is a quality validation and documentation phase

**Organization**: Tasks are grouped by functional requirement to enable efficient completion

## Format: `[ID] [P?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- Include exact file paths in descriptions

## Path Conventions

- **Multi-module Android project**: `storage/database/`, `event/`, `ui/common/`
- Paths relative to repository root: `/home/ira/src/AntennaPod/`

---

## Phase 1: Setup & Verification

**Purpose**: Establish baseline and understand current state

- [ ] T001 Run checkstyle to identify violations: `./gradlew checkstyle`
- [ ] T002 Run SpotBugs to identify bugs: `./gradlew spotbugsPlayDebug spotbugsDebug`
- [ ] T003 Run Android Lint to identify errors: `./gradlew :app:lintPlayDebug`
- [ ] T004 Document baseline quality metrics in specs/006-queue-feature-completion/quality-baseline.md

---

## Phase 2: Unauthorized Feature Removal (FR-004)

**Purpose**: Remove queue reordering functionality per user directive

**⚠️ CRITICAL**: User explicitly prohibited queue reordering. Queue IDs are immutable.

- [ ] T005 [P] Remove `reorderQueues()` method from storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [ ] T006 [P] Remove `QUEUES_REORDERED` enum value from event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java
- [ ] T007 [P] Remove `queuesReordered()` static method from event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java
- [ ] T008 [P] Remove `testReorderQueues_Success` test from storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueueWriterTest.java
- [ ] T009 [P] Remove `testReorderQueuesPerformance` test from storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueuePerformanceTest.java
- [ ] T010 Search for all references to "reorder" in codebase: `grep -r "reorder" --include="*.java" --include="*.md"`
- [ ] T011 Remove any remaining references to queue reordering found in step T010

**Checkpoint**: All queue reordering code removed

---

## Phase 3: Code Quality Fixes (FR-001)

**Purpose**: Fix all violations identified in Phase 1

- [ ] T012 Fix all checkstyle violations identified in T001
- [ ] T013 Fix all SpotBugs issues identified in T002
- [ ] T014 Fix all Android Lint errors identified in T003
- [ ] T015 Re-run all quality checks to verify zero violations

**Checkpoint**: All quality gates passing with zero violations

---

## Phase 4: JavaDoc Completion (FR-002)

**Purpose**: Complete JavaDoc for all queue-related public methods

### QueueViewModel Documentation

- [ ] T016 Add/verify JavaDoc for `switchActiveQueue()` in ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java
- [ ] T017 Add/verify JavaDoc for `observeActiveQueue()` in ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java
- [ ] T018 Add/verify JavaDoc for `getAllQueues()` in ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java

### DBWriter Queue Methods Documentation

- [ ] T019 Add/verify JavaDoc for `createQueue()` in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [ ] T020 Add/verify JavaDoc for `renameQueue()` in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [ ] T021 Add/verify JavaDoc for `changeQueueColor()` in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [ ] T022 Add/verify JavaDoc for `deleteQueue()` in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java

### DBReader Queue Methods Documentation

- [ ] T023 Add/verify JavaDoc for `getAllQueues()` in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [ ] T024 Add/verify JavaDoc for `getQueue(long queueId)` in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [ ] T025 Add/verify JavaDoc for `getQueueById()` in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java

**Checkpoint**: All queue-related public methods have complete JavaDoc with @param, @return, and descriptions

---

## Phase 5: Documentation Updates (FR-003)

**Purpose**: Update CLAUDE.md with comprehensive queue usage examples and prohibitions

- [ ] T026 Add "Creating a Queue" example to CLAUDE.md
- [ ] T027 Add "Switching Queues" example to CLAUDE.md
- [ ] T028 Add "Getting Queue Episodes" example to CLAUDE.md
- [ ] T029 Add "Managing Queue Metadata" example to CLAUDE.md
- [ ] T030 Add "IMPORTANT - Queue Ordering Prohibition" section to CLAUDE.md documenting that queue reordering is forbidden
- [ ] T031 Verify all existing queue documentation in CLAUDE.md is accurate and complete

**Checkpoint**: CLAUDE.md contains comprehensive queue usage examples and clear prohibition documentation

---

## Phase 6: Spec Cleanup

**Purpose**: Remove reordering references from specification documents

- [ ] T032 Search for reordering references in specs/001-multiple-queues/spec.md
- [ ] T033 Remove or update reordering references found in T032
- [ ] T034 Search for reordering references in specs/001-multiple-queues/tasks.md
- [ ] T035 Remove T019 (reorderQueues implementation) from specs/001-multiple-queues/tasks.md
- [ ] T036 Update any task descriptions referencing queue reordering in specs/001-multiple-queues/tasks.md

**Checkpoint**: All specification documents accurately reflect that queue reordering is prohibited

---

## Phase 7: Final Validation (TR-001, TR-003)

**Purpose**: Verify all success criteria met

- [ ] T037 Run full quality check suite: `./gradlew checkstyle spotbugsPlayDebug spotbugsDebug :app:lintPlayDebug`
- [ ] T038 Run database unit tests: `./gradlew :storage:database:testDebugUnitTest`
- [ ] T039 Run model unit tests: `./gradlew :model:testDebugUnitTest`
- [ ] T040 Verify all quality checks pass with zero violations
- [ ] T041 Verify database tests pass (document any pre-existing infrastructure failures)
- [ ] T042 Create Phase 6 completion summary in specs/006-queue-feature-completion/completion-summary.md

**Checkpoint**: All success criteria met - Phase 6 complete

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies - can start immediately
- **Phase 2 (Removal)**: Can start after Phase 1 (baseline established)
- **Phase 3 (Quality Fixes)**: Depends on Phase 1 completion (need to know what to fix)
- **Phase 4 (JavaDoc)**: Can run in parallel with Phase 3 (different concern)
- **Phase 5 (Documentation)**: Can run in parallel with Phase 3 & 4 (different files)
- **Phase 6 (Spec Cleanup)**: Can run in parallel with Phase 3, 4, 5 (different files)
- **Phase 7 (Validation)**: Depends on Phases 2-6 completion (final verification)

### Task Dependencies Within Phases

**Phase 1**: Sequential (T001 → T002 → T003 → T004)

**Phase 2**:
- T005-T009 can run in parallel [P] (different files)
- T010 must complete before T011 (search before remove)

**Phase 3**: Sequential within phase, but can overlap with other phases
- T012, T013, T014 may be iterative
- T015 verifies all (runs last)

**Phase 4**: All tasks can run in parallel [P] (different methods/files)

**Phase 5**: All tasks can run in parallel [P] (same file but different sections)

**Phase 6**: T032 before T033, T034 before T035-T036

**Phase 7**: T037-T039 can run in parallel [P], then T040-T042 sequential

### Parallel Opportunities

**After Phase 1 baseline established:**
- Phase 2 (Removal) AND Phase 4 (JavaDoc) can run concurrently
- Phase 5 (CLAUDE.md updates) can run concurrently with Phases 2, 3, 4
- Phase 6 (Spec cleanup) can run concurrently with Phases 2, 3, 4, 5

**Optimal parallelization:**
1. Complete Phase 1 (baseline)
2. Launch in parallel:
   - Phase 2: Remove reordering code
   - Phase 4: Add JavaDoc
   - Phase 5: Update CLAUDE.md
   - Phase 6: Clean up specs
3. Phase 3: Fix quality issues (may need Phase 2 complete to avoid conflicts)
4. Phase 7: Final validation

---

## Parallel Example: Maximum Concurrency

```bash
# After Phase 1 baseline complete, launch these in parallel:

# Phase 2 removal tasks (T005-T009 in parallel):
Task: "Remove reorderQueues() from DBWriter.java"
Task: "Remove QUEUES_REORDERED from QueueEvent.java"
Task: "Remove queuesReordered() from QueueEvent.java"
Task: "Remove testReorderQueues_Success from QueueWriterTest.java"
Task: "Remove testReorderQueuesPerformance from QueuePerformanceTest.java"

# Phase 4 JavaDoc tasks (T016-T025 in parallel):
Task: "Add JavaDoc to QueueViewModel methods"
Task: "Add JavaDoc to DBWriter queue methods"
Task: "Add JavaDoc to DBReader queue methods"

# Phase 5 documentation tasks (T026-T031 in parallel):
Task: "Add queue examples to CLAUDE.md"
Task: "Add prohibition documentation to CLAUDE.md"
```

---

## Implementation Strategy

### Sequential Approach (Single Developer)

1. Complete Phase 1: Establish baseline (T001-T004)
2. Complete Phase 2: Remove unauthorized code (T005-T011)
3. Complete Phase 3: Fix quality issues (T012-T015)
4. Complete Phase 4: Add JavaDoc (T016-T025)
5. Complete Phase 5: Update CLAUDE.md (T026-T031)
6. Complete Phase 6: Clean specs (T032-T036)
7. Complete Phase 7: Final validation (T037-T042)

### Parallel Approach (Efficient Single Developer)

1. Phase 1: Baseline (sequential)
2. Phases 2, 4, 5, 6 in parallel (different files, no conflicts)
3. Phase 3: Quality fixes (may conflict with Phase 2, do after)
4. Phase 7: Validation (sequential, last)

**Estimated time**: 2-4 hours total

---

## Success Criteria

- ✅ **SC-001**: Checkstyle passes with zero violations (T040)
- ✅ **SC-002**: SpotBugs passes with zero bugs (T040)
- ✅ **SC-003**: Lint passes with zero errors (T040)
- ✅ **SC-004**: JavaDoc complete for all queue-related public methods (T016-T025)
- ✅ **SC-005**: CLAUDE.md contains comprehensive queue examples (T026-T031)
- ✅ **SC-006**: Queue reordering code completely removed (T005-T011)
- ✅ **SC-007**: Prohibition against queue reordering documented (T030)

---

## Notes

- [P] tasks = different files/sections, no dependencies, safe to parallelize
- No [Story] labels - this is a quality/documentation phase, not feature implementation
- Phase 2 (removal) is highest priority - user directive must be enforced
- Quality gates are NON-NEGOTIABLE - all must pass before Phase 6 complete
- Pre-existing test failures (16 infrastructure issues) are acceptable if documented
- Commit after each phase completion or logical group
- Stop at checkpoints to validate progress
- Total: 42 tasks covering 7 phases
