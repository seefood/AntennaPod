---
description: "Task list for Phase 8: Queue Episode Transfer Operations"
---

# Tasks: Queue Episode Transfer Operations

**Input**: Design documents from `/specs/008-move-copy-episodes/`
**Prerequisites**: plan.md, spec.md (research.md, data-model.md, contracts/ to be generated)

**Tests**: Unit tests and UI tests are REQUIRED per AntennaPod constitution (Principle II: Test Coverage for New Features)

**Organization**: Tasks organized by user story priority - foundational infrastructure, then single-episode operations (US-1, US-2), then batch operations (US-3, US-4), then UI polish (US-5)

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1 = Move Single, US2 = Copy Single, US3 = Batch Move, US4 = Batch Copy, US5 = Dialog)
- Include exact file paths in descriptions
- All tasks must be independently executable by LLM

## Path Conventions

- **Android multi-module**: `model/`, `storage/`, `ui/common/`, `app/`, paths relative to repository root
- Repository root: `/home/ira/src/AntennaPod/`

---

## Phase 1: Setup & Branch Initialization

**Purpose**: Prepare development environment and verify prerequisites

- [ ] T001 Verify branch `008-move-copy-episodes` is correctly forked from `007-queue-color-gradient`
- [ ] T002 Verify DBWriter.removeQueueItem() only removes from specified queue (not global delete)
- [ ] T003 Verify DBWriter.addQueueItem() has duplicate detection (itemListContains logic)
- [ ] T004 Verify QueueEvent supports new action types (or plan to add: ITEM_MOVED, ITEM_COPIED, etc.)
- [ ] T005 Verify all 6 target screens exist: QueueFragment, EpisodeItemListAdapter, ItemPagerFragment, EpisodeMultiSelectActionHandler

**Checkpoint**: Prerequisites verified, ready to implement core infrastructure

---

## Phase 2: Foundational Infrastructure (Blocking Prerequisites)

**Purpose**: Create result classes and database layer that ALL user stories depend on

### Result Classes (Model Layer)

- [ ] T006 Create `model/src/main/java/de/danoeh/antennapod/model/MoveResult.java` with fields: movedCount (int), skippedCount (int), skippedItemIds (List<Long>), skipReasons (Map<Long, String>)
- [ ] T007 Create `model/src/main/java/de/danoeh/antennapod/model/CopyResult.java` with fields: copiedCount (int), skippedCount (int), skippedItemIds (List<Long>), skipReasons (Map<Long, String>)
- [ ] T008 Add JavaDoc to MoveResult and CopyResult with @param and @return annotations

### Unit Tests for Result Classes

- [ ] T009 Create `model/src/test/java/de/danoeh/antennapod/model/MoveResultTest.java` with test: `testMoveResult_ProperlyAggregates()` - verify counts and maps
- [ ] T010 Create `model/src/test/java/de/danoeh/antennapod/model/CopyResultTest.java` with test: `testCopyResult_ProperlyAggregates()`
- [ ] T011 Run tests: `./gradlew :model:testDebugUnitTest --tests "*MoveResult*,*CopyResult*"` - verify tests pass

### Database Layer - Core Methods

- [ ] T012 Implement `DBWriter.moveQueueItem(feedItemId, sourceQueueId, targetQueueId)` in `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`
  - Implementation: Call removeQueueItem(feedItemId, sourceQueueId) + addQueueItem(feedItemId, targetQueueId) within single transaction
  - Verify: Episode removed from source only, added to target, not affected in other queues
- [ ] T013 Implement `DBWriter.copyQueueItem(feedItemId, targetQueueId)`
  - Implementation: Delegate to existing addQueueItem(feedItemId, targetQueueId)
  - Verify: Duplicate detection works (prevents adding to same queue twice)
- [ ] T014 Implement `DBWriter.moveQueueItems(List<Long> feedItemIds, sourceQueueId, targetQueueId)` - batch move with best-effort
  - Implementation: Iterate moveQueueItem() for each, catch exceptions, aggregate results
  - Return: MoveResult with counts and skip reasons
- [ ] T015 Implement `DBWriter.copyQueueItems(List<Long> feedItemIds, targetQueueId)` - batch copy with best-effort
  - Implementation: Iterate copyQueueItem() for each, catch exceptions, aggregate results
  - Return: CopyResult with counts and skip reasons
- [ ] T016 Add JavaDoc to all 4 new DBWriter methods with @param, @return, @throws annotations

### Unit Tests for DBWriter Methods

- [ ] T017 Create `storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueTransferTest.java`
- [ ] T018 Write test: `testMoveQueueItem_RemovesFromSourceAddsToTarget()` - verify episode removed from source, added to target
- [ ] T019 Write test: `testMoveQueueItem_DoesNotAffectOtherQueues()` - **CRITICAL**: Episode exists in Q1,Q2,Q3; move from Q1→Q4; verify remains in Q2,Q3
- [ ] T020 Write test: `testMoveQueueItem_FailsWhenNotInSourceQueue()` - verify exception when episode not in source
- [ ] T021 Write test: `testCopyQueueItem_AddsToTargetKeepsInSource()` - verify episode in both queues
- [ ] T022 Write test: `testCopyQueueItem_FailsWhenAlreadyInTarget()` - verify duplicate detection works
- [ ] T023 Write test: `testMoveQueueItems_BestEffort()` - move 5 items, 3 succeed, 2 fail; verify MoveResult.movedCount=3, skippedCount=2
- [ ] T024 Write test: `testCopyQueueItems_SkipsDuplicates()` - copy 5 items, 2 already in target; verify CopyResult.copiedCount=3, skippedCount=2
- [ ] T025 Write test: `testCopyQueueItems_PreservesOrder()` - verify items appended in order to end of target queue
- [ ] T026 Run all tests: `./gradlew :storage:database:testDebugUnitTest --tests "*DBWriterQueueTransfer*"` - verify all pass
- [ ] T027 Call `DBWriter.tearDownTests()` in test teardown to prevent "Illegal connection pointer" errors

### Event Layer - Extend QueueEvent

- [ ] T028 Add new action types to `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`: ITEM_MOVED, ITEM_COPIED, ITEMS_BATCH_MOVED, ITEMS_BATCH_COPIED
- [ ] T029 Add JavaDoc for new action types explaining when they are posted

### Verification

- [ ] T030 Build app: `./gradlew assemblePlayDebug` - verify no compilation errors
- [ ] T031 Run checkstyle: `./gradlew checkstyle` - fix any violations
- [ ] T032 Run SpotBugs: `./gradlew spotbugsPlayDebug spotbugsDebug` - fix any bugs

**Checkpoint**: Foundation ready - result classes, DBWriter methods, and unit tests complete and passing

---

## Phase 3: User Story 1 - Move Single Episode 🎯 MVP

**Goal**: Allow users to move single episode from one queue to another via context menu

**Priority**: P1 (Primary feature)

**Independent Test**: User can move episode from Queue A to Queue B via context menu, episode disappears from A, appears at end of B

### Unit Tests (TDD)

- [ ] T033 [P] Create test: `testMoveOperation_FiresQueueEvent()` - verify ITEM_MOVED event posted with correct queueIds

### Database Layer - Already Complete

(DBWriter.moveQueueItem from Phase 2 - T012)

### UI - Queue Selection Dialog (Used by US-1 and US-2)

- [ ] T034 [P] Create `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSelectionDialog.java` dialog fragment
  - Features: RecyclerView showing queues, filter out source queue for move, highlight last-used destination
  - Session-based memory for last destination (cleared on app restart)
  - Callbacks: OnQueueSelectedListener with onQueueSelected(queue)
- [ ] T035 [P] Create `ui/common/src/main/res/layout/dialog_queue_selection.xml` layout
  - Title: "Select Queue"
  - RecyclerView: queue_list
  - Search TextInputLayout (visibility=gone by default, shown for >10 queues)
  - Create New Queue button at bottom
  - Cancel button in footer
- [ ] T036 [P] Create `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSelectionAdapter.java` RecyclerView adapter
  - Each item shows: queue name, color badge, episode count
  - Highlight last-used destination with different background color
  - Click listener: calls OnQueueSelectedListener callback
- [ ] T037 [P] Create `ui/common/src/main/res/layout/queue_selection_item.xml` list item layout
  - Queue name (large text)
  - Episode count (small text)
  - Color badge (circle with queue color)
  - Click area for selection

### UI - Context Menu Integration (Queue Screen)

- [ ] T038 [US1] Add "Move to Queue" menu item to QueueFragment context menu in `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueFragment.java`
  - Long-press on episode → context menu with "Move to Queue" action
  - On click: Launch QueueSelectionDialog
  - On queue selected: Call `DBWriter.moveQueueItem(feedItemId, currentQueueId, selectedQueueId).get()`
  - Show Snackbar: "Episode moved to [queue name]"
  - Verify episode removed from queue (UI refresh)

### UI - Context Menu Integration (Episode List Screen)

- [ ] T039 [US1] Add "Move to Queue" menu item to EpisodeItemListAdapter in `app/src/main/java/de/danoeh/antennapod/ui/screen/episodes/EpisodeItemListAdapter.java`
  - Swipe menu on episode → "Move to Queue" action
  - Same dialog and flow as QueueFragment
  - Determine current queue from context (or pass as parameter)

### UI - Episode Detail Screen

- [ ] T040 [US1] Add "Move to Queue" menu item to ItemPagerFragment in `app/src/main/java/de/danoeh/antennapod/ui/fragment/ItemPagerFragment.java`
  - Overflow menu → "Move to Queue" action
  - Same dialog and flow

### Integration Tests (Espresso)

- [ ] T041 [US1] Create Espresso test: Episode move via context menu
  - Create 2 test queues: "Queue A", "Queue B"
  - Add episode to Queue A
  - Long-press episode → "Move to Queue" → select "Queue B"
  - Verify Snackbar shows "Episode moved to Queue B"
  - Verify episode removed from Queue A (check RecyclerView)
  - Verify episode appears in Queue B

### Verification

- [ ] T042 [US1] Manual test: Move episode from Queue A to Queue B via all 3 entry points (queue menu, episode list, detail screen)
- [ ] T043 [US1] Manual test: Move episode that exists in multiple queues (Q1, Q2, Q3) → move from Q1 to Q4 → verify remains in Q2, Q3
- [ ] T044 [US1] Manual test: Snackbar message appears and is readable
- [ ] T045 [US1] Code quality check: Run checkstyle, SpotBugs, Lint - fix any violations

**Checkpoint**: User Story 1 complete - single episode move working from all 3 screens with proper isolation

---

## Phase 4: User Story 2 - Copy Single Episode

**Goal**: Allow users to copy episode to another queue while keeping it in source queue

**Priority**: P1 (Primary feature, depends on US-1 infrastructure)

**Independent Test**: User can copy episode from Queue A to Queue B, episode remains in A and appears in B

### UI - Context Menu Integration (Reuse from US-1)

- [ ] T046 [US2] Add "Copy to Queue" menu item to QueueFragment context menu in `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueFragment.java`
  - Long-press on episode → context menu with "Copy to Queue" action
  - Launch QueueSelectionDialog (same as Move, but don't filter out current queue)
  - On queue selected: Call `DBWriter.copyQueueItem(feedItemId, selectedQueueId).get()`
  - Handle exception if episode already in target queue: Show Snackbar "Episode already in [queue name]"
  - On success: Show Snackbar "Episode copied to [queue name]"

- [ ] T047 [US2] Add "Copy to Queue" to EpisodeItemListAdapter swipe menu in `app/src/main/java/de/danoeh/antennapod/ui/screen/episodes/EpisodeItemListAdapter.java`
  - Same dialog and flow as Move

- [ ] T048 [US2] Add "Copy to Queue" to ItemPagerFragment overflow menu in `app/src/main/java/de/danoeh/antennapod/ui/fragment/ItemPagerFragment.java`
  - Same dialog and flow

### Integration Tests

- [ ] T049 [US2] Create Espresso test: Episode copy via context menu
  - Create 2 test queues: "Queue A", "Queue B"
  - Add episode to Queue A
  - Long-press episode → "Copy to Queue" → select "Queue B"
  - Verify Snackbar shows "Episode copied to Queue B"
  - Verify episode still in Queue A (check RecyclerView)
  - Verify episode appears in Queue B

- [ ] T050 [US2] Create Espresso test: Duplicate copy detection
  - Episode already in target queue
  - Try to copy same episode to same queue
  - Verify Snackbar shows "Episode already in Queue B"
  - Verify no duplicate added

### Verification

- [ ] T051 [US2] Manual test: Copy episode from Queue A to Queue B, verify remains in A and appears in B
- [ ] T052 [US2] Manual test: Try copying episode already in target queue, verify duplicate prevention message
- [ ] T053 [US2] Manual test: Copy episode that exists in multiple queues (Q1, Q2) to Q3 → verify Q1, Q2 unaffected
- [ ] T054 [US2] Code quality check: Run checkstyle, SpotBugs, Lint - fix any violations

**Checkpoint**: User Story 2 complete - single episode copy working with duplicate detection

---

## Phase 5: User Story 3 - Batch Move Episodes

**Goal**: Allow users to select multiple episodes and move all to another queue

**Priority**: P2 (Enhancement, depends on US-1)

**Independent Test**: User can select 3 episodes, move all to another queue, all disappear from source and appear at end of target in original order

### UI - Multi-Select Integration

- [ ] T055 [US3] Update `EpisodeMultiSelectActionHandler` in `app/src/main/java/de/danoeh/antennapod/ui/` to add "Move Selected to Queue" action
  - When user selects multiple episodes and taps "Move to Queue" button in action bar
  - Launch QueueSelectionDialog
  - On queue selected: Call `DBWriter.moveQueueItems(selectedFeedItemIds, currentQueueId, selectedQueueId).get()`
  - Show Snackbar: "N episodes moved to [queue name]"
  - Handle partial failures: "M episodes moved, N skipped (reasons listed)"

### Progress Indicator (for large batches)

- [ ] T056 [US3] Show progress dialog for batch move >50 episodes in `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueFragment.java`
  - Dialog shows: "Moving episodes... X of Y"
  - Cancelable (but shows warning about partial completion)

### Integration Tests

- [ ] T057 [US3] Create Espresso test: Batch move multiple episodes
  - Create 2 test queues: "Queue A", "Queue B"
  - Add 5 episodes to Queue A
  - Select all 5 episodes
  - Tap "Move to Queue" → select "Queue B"
  - Verify Snackbar shows "5 episodes moved to Queue B"
  - Verify all 5 removed from Queue A
  - Verify all 5 appear at end of Queue B in original order

- [ ] T058 [US3] Create Espresso test: Batch move with partial failures
  - Create 3 episodes in Queue A, 1 already in target Queue B
  - Select all 3 for move to Queue B
  - Verify Snackbar: "2 episodes moved, 1 skipped (already in queue)"
  - Verify 2 new episodes in Queue B, original episode still present (no duplicate)

### Verification

- [ ] T059 [US3] Manual test: Batch move 10 episodes at once
- [ ] T060 [US3] Manual test: Batch move >50 episodes and verify progress dialog appears
- [ ] T061 [US3] Manual test: Batch move with partial failures (some episodes not in source queue) and verify result reporting
- [ ] T062 [US3] Code quality check: Run checkstyle, SpotBugs, Lint - fix any violations

**Checkpoint**: User Story 3 complete - batch move with progress indicator and error handling

---

## Phase 6: User Story 4 - Batch Copy Episodes

**Goal**: Allow users to select multiple episodes and copy all to another queue

**Priority**: P2 (Enhancement, depends on US-2)

**Independent Test**: User can select 3 episodes, copy all to another queue, all remain in source and appear at end of target in original order

### UI - Multi-Select Integration

- [ ] T063 [US4] Update `EpisodeMultiSelectActionHandler` to add "Copy Selected to Queue" action
  - When user selects multiple episodes and taps "Copy to Queue" button in action bar
  - Launch QueueSelectionDialog (don't filter out current queue for copy)
  - On queue selected: Call `DBWriter.copyQueueItems(selectedFeedItemIds, selectedQueueId).get()`
  - Show Snackbar: "N episodes copied to [queue name]"
  - Handle duplicates: "M episodes copied, N skipped (already in queue)"

### Progress Indicator (for large batches)

- [ ] T064 [US4] Show progress dialog for batch copy >50 episodes
  - Dialog shows: "Copying episodes... X of Y"
  - Cancelable (but shows warning about partial completion)

### Integration Tests

- [ ] T065 [US4] Create Espresso test: Batch copy multiple episodes
  - Create 2 test queues: "Queue A", "Queue B"
  - Add 5 episodes to Queue A
  - Select all 5 episodes
  - Tap "Copy to Queue" → select "Queue B"
  - Verify Snackbar shows "5 episodes copied to Queue B"
  - Verify all 5 still in Queue A
  - Verify all 5 appear at end of Queue B in original order

- [ ] T066 [US4] Create Espresso test: Batch copy with duplicate handling
  - Create 3 episodes in Queue A, 1 already in target Queue B
  - Select all 3 for copy to Queue B
  - Verify Snackbar: "2 episodes copied, 1 skipped (already in queue)"
  - Verify all 3 still in Queue A
  - Verify 2 new episodes in Queue B, original episode still present (no duplicate)

### Verification

- [ ] T067 [US4] Manual test: Batch copy 10 episodes at once
- [ ] T068 [US4] Manual test: Batch copy >50 episodes and verify progress dialog appears
- [ ] T069 [US4] Manual test: Batch copy with partial failures (some duplicates) and verify result reporting
- [ ] T070 [US4] Code quality check: Run checkstyle, SpotBugs, Lint - fix any violations

**Checkpoint**: User Story 4 complete - batch copy with progress indicator and duplicate handling

---

## Phase 7: User Story 5 - Queue Selection Dialog Polish

**Goal**: Complete UI polish for dialog (search, filtering, "Create New Queue" option)

**Priority**: P2 (Enhancement, depends on US-1)

**Independent Test**: User can search for queues in dialog, see only matching queues, can create new queue from dialog

### Search/Filter Implementation

- [ ] T071 [US5] Implement search functionality in QueueSelectionDialog in `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSelectionDialog.java`
  - Show search field when >10 queues exist
  - Real-time filtering as user types
  - Case-insensitive queue name matching

- [ ] T072 [US5] Update QueueSelectionAdapter to support filtered list
  - Accept filter string from dialog
  - Update RecyclerView when filter changes

### "Create New Queue" Option

- [ ] T073 [US5] Implement "Create New Queue" button in dialog
  - User taps button → inline form appears
  - Input: Queue name, color picker
  - On create: New queue created via `DBWriter.createQueue()`
  - New queue auto-selected for move/copy operation
  - Dialog closes, operation proceeds with new queue

### Last Destination Memory

- [ ] T074 [US5] Implement session-based last destination memory in `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSelectionViewModel.java`
  - Use ViewModel with MutableLiveData<Long> lastDestinationQueueId
  - On queue selection, save destination ID
  - On dialog open, move last destination to top of list with highlight
  - Clear on app restart (ViewModel destroyed)

### Integration Tests

- [ ] T075 [US5] Create Espresso test: Search in queue list
  - 15 queues available
  - User types "work" in search field
  - Verify only queues matching "work" shown (e.g., "Work", "Work Podcasts", "Workout")
  - User clears search
  - Verify all queues shown again

- [ ] T076 [US5] Create Espresso test: Create new queue from dialog
  - User opens move/copy dialog
  - Taps "Create New Queue"
  - Enters name "New Queue" and selects color
  - Taps Create
  - Verify "New Queue" appears in list and is auto-selected
  - Verify move/copy operation proceeds with new queue

- [ ] T077 [US5] Create Espresso test: Last destination memory
  - User moves episode to "Queue A"
  - User opens move/copy dialog again
  - Verify "Queue A" is at top with highlight
  - User moves episode to "Queue B"
  - User opens dialog again
  - Verify "Queue B" is now at top

### Verification

- [ ] T078 [US5] Manual test: Search filters queues correctly
- [ ] T079 [US5] Manual test: Create new queue from dialog
- [ ] T080 [US5] Manual test: Last destination highlighted and at top
- [ ] T081 [US5] Code quality check: Run checkstyle, SpotBugs, Lint - fix any violations

**Checkpoint**: User Story 5 complete - dialog fully polished with search, creation, and memory

---

## Phase 8: Polish & Quality Assurance

**Purpose**: Testing, quality checks, documentation, edge cases

### Theme & Edge Case Testing

- [ ] T082 [P] Manual test: Switch from Light to Dark theme - verify all UI elements readable
- [ ] T083 [P] Manual test: Move/copy with 1 episode (edge case: queue becomes empty when last episode moved)
- [ ] T084 [P] Manual test: Rapid queue switching while batch operation in progress
- [ ] T085 [P] Manual test: Orientation change (portrait/landscape) during move/copy operation
- [ ] T086 [P] Manual test: Device with notch - verify dialog displays correctly

### Cross-Queue Behavior Testing

- [ ] T087 Manual test: **CRITICAL - Queue Removal Semantics**
  - Create 3 queues: Q1, Q2, Q3 with same episode E in all 3
  - Move E from Q1 to Q4 → Verify E removed from Q1, remains in Q2 and Q3, added to Q4
  - Delete E from Q2 → Verify E removed from Q2, remains in Q3 and Q4, NOT removed from Q1 or other queues
  - Copy E to Q5 → Verify E in all original queues + Q5 (no duplicates within queue)
  - Verify no operation affects queues other than specified source/target

### Code Quality Gates

- [ ] T088 Run checkstyle: `./gradlew checkstyle` - fix any violations
- [ ] T089 Run SpotBugs: `./gradlew spotbugsPlayDebug spotbugsDebug` - fix any bugs
- [ ] T090 Run Android Lint: `./gradlew :app:lintPlayDebug` - fix any errors
- [ ] T091 Format XML layouts: `curl -s -L https://github.com/ByteHamster/android-xml-formatter/releases/download/1.1.0/android-xml-formatter.jar > android-xml-formatter.jar && find . -wholename "*/res/layout/queue_selection*.xml" | xargs java -jar android-xml-formatter.jar`
- [ ] T092 Re-run all quality checks to verify zero violations

### Documentation

- [ ] T093 [P] Update CLAUDE.md with move/copy operation examples showing:
  - Single episode move/copy code example
  - Batch move/copy code example
  - Critical behavior: episode removal only affects source queue
- [ ] T094 [P] Add to CLAUDE.md: Queue removal semantics documentation
  - User-initiated removal scope
  - Playback completion global removal
  - Test this behavior before committing
- [ ] T095 [P] Add to CLAUDE.md: Queue transfer operation troubleshooting
  - What to check if move/copy doesn't work
  - How to verify correct queue isolation

### Performance Validation

- [ ] T096 Manual test: Batch move 100 episodes - should complete in <500ms
- [ ] T097 Manual test: Batch copy 100 episodes - should complete in <300ms
- [ ] T098 Manual test: Dialog with 100 queues - should load in <200ms

### Final Verification

- [ ] T099 Run all unit tests: `./gradlew testPlayDebugUnitTest testDebugUnitTest` - verify all pass
- [ ] T100 Run all integration tests: `./gradlew connectedPlayDebugAndroidTest` - verify all pass
- [ ] T101 Run full quality suite: `./gradlew checkstyle spotbugsPlayDebug spotbugsDebug :app:lintPlayDebug` - zero violations
- [ ] T102 Manual test: All 5 user stories working end-to-end (single move, single copy, batch move, batch copy, dialog features)
- [ ] T103 Create completion summary in specs/008-move-copy-episodes/COMPLETION.md with screenshots

**Checkpoint**: All quality gates passed, feature ready for PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies - can start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 - BLOCKS all user story work
- **Phase 3 (US-1)**: Depends on Phase 2 - MVP foundation
- **Phase 4 (US-2)**: Depends on Phase 3 - shares dialog infrastructure
- **Phase 5 (US-3)**: Depends on Phase 3 - reuses move operation
- **Phase 6 (US-4)**: Depends on Phase 4 - reuses copy operation
- **Phase 7 (US-5)**: Depends on Phase 3 - dialog enhancements
- **Phase 8 (Polish)**: Depends on all user stories complete

### Critical Task Dependencies Within Phases

**Phase 2 (Foundation)**:
- **Result classes** (T006-T008): Create before tests
- **Tests** (T009-T011): Run after classes created
- **DBWriter methods** (T012-T016): Implement after tests pass
- **DBWriter tests** (T017-T027): Run after methods implemented
- **Events** (T028-T029): Implement after DBWriter tests pass
- **Build verification** (T030-T032): Final check after all above

**Phase 3 (US-1)**:
- Dialog components (T034-T037) can run in parallel
- Context menu integration (T038-T040) depends on dialog ready
- Tests (T041-T045) depend on all above

**Phases 4-7**: Can largely run in parallel after Phase 3 dialog is ready

### Parallel Opportunities

**Within Phase 2**:
- Result classes (T006-T008) parallel with DBWriter methods once ready
- Unit tests (T009-T011, T017-T027) parallel with implementation

**Within Phase 3-7**:
- Dialog components (T034-T037) - all [P] parallel
- Context menu integrations (T038-T040, T046-T048, T055, T063) - different files, can parallel
- Tests for each story can run in parallel with implementation

**Maximum parallelization after Phase 2**:
- US-1 and US-2 components ready in parallel (different files)
- US-3 and US-4 components ready in parallel (different files)
- US-5 enhancements parallel with US-1,2,3,4

---

## Implementation Strategy

### MVP First Approach (Recommended)

**MVP Scope**: User Stories 1 + 2 (Single Episode Move/Copy)

1. **Phase 1**: Setup verification (T001-T005)
2. **Phase 2**: Foundation complete (T006-T032)
3. **Phase 3**: US-1 (Move Single) complete (T033-T045)
4. **Phase 4**: US-2 (Copy Single) complete (T046-T054)
5. **Phase 8 (partial)**: Quality gates + documentation (T088-T095)

**MVP Time**: ~3-4 hours
**MVP Tasks**: ~54 tasks (T001-T054)

**Post-MVP Phases**:
- Phase 5: US-3 (Batch Move)
- Phase 6: US-4 (Batch Copy)
- Phase 7: US-5 (Dialog Polish)
- Phase 8: Final quality & docs

### Sequential Single-Developer Approach

1. Phase 1: Setup (30 min)
2. Phase 2: Foundation (2 hours)
3. Phase 3: US-1 (1.5 hours)
4. Phase 4: US-2 (1.5 hours)
5. Phase 5: US-3 (1 hour)
6. Phase 6: US-4 (1 hour)
7. Phase 7: US-5 (1 hour)
8. Phase 8: Polish (2 hours)

**Estimated total**: 10-12 hours

### Parallel Multi-Agent Approach

1. Phase 1: Setup (30 min)
2. Phase 2: Foundation (2 hours)
3. Launch 4 agents in parallel for Phase 3-6:
   - Agent 1: US-1 (Move Single)
   - Agent 2: US-2 (Copy Single)
   - Agent 3: US-3 (Batch Move)
   - Agent 4: US-4 (Batch Copy)
4. Sequential: US-5, Polish, Tests
5. Sequential: Quality gates + final verification

**Estimated wall time**: 5-6 hours

---

## Success Criteria

- ✅ **SC-001**: User Story 1 (Move Single) complete and tested
- ✅ **SC-002**: User Story 2 (Copy Single) complete and tested
- ✅ **SC-003**: User Story 3 (Batch Move) complete and tested
- ✅ **SC-004**: User Story 4 (Batch Copy) complete and tested
- ✅ **SC-005**: User Story 5 (Dialog Polish) complete and tested
- ✅ **SC-006**: Episode removal only affects specified queue (CRITICAL behavior verified)
- ✅ **SC-007**: All operations preserve episode playback position (shared state)
- ✅ **SC-008**: Batch operations report success/skip counts with reasons
- ✅ **SC-009**: Dialog provides queue search, creation, and last-destination memory
- ✅ **SC-010**: Code passes all quality gates (checkstyle, SpotBugs, Lint) with zero violations
- ✅ **SC-011**: Unit tests: 100% coverage for result classes and DBWriter methods
- ✅ **SC-012**: Integration tests: Move/copy across all 3 entry points (queue menu, episode list, detail)

---

## Notes

- **[P]** tasks = different files/components, no dependencies, safe to parallelize
- **[US]** label = User Story phase
- Tests written alongside implementation (TDD recommended)
- All fragments follow identical pattern: dialog → DBWriter call → Snackbar → UI refresh
- Commit after each phase completion or logical group
- **CRITICAL**: Phase 2 foundation must be complete before any user story work
- **CRITICAL**: T087 - Queue removal semantics must be manually tested to verify no cross-queue side effects
- **Total**: 103 tasks across 8 phases
- **Parallelizable**: ~45 tasks marked [P] (~44% parallel efficiency)
- **Test coverage**: 27 unit tests + 15 integration tests = 42 automated tests + extensive manual testing
