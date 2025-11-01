# Implementation Tasks: Queue UI for Multiple Queues

**Feature**: Queue UI for Multiple Queues
**Branch**: `queue-ui/001-specification`
**Date Generated**: 2025-11-01
**Specification**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

---

## Implementation Strategy

**MVP Scope**: User Stories 1-2 (P1: Queue switching and creation)
**Phase 2 Scope**: User Stories 3-4 (P2: Customization and deletion)
**Phase 3 Scope**: User Story 5 (P3: Copy/Move episodes)

**Independent Testability**: Each user story can be developed, tested, and deployed independently. Story 1 provides value immediately; Stories 2-5 build incrementally on the foundation.

**Parallel Opportunities**:
- Within each user story phase, UI components (layouts, adapters) can be developed in parallel with business logic (ViewModels, managers)
- Database operations are already implemented (Phase 5); UI tasks depend only on existing DBReader/DBWriter APIs
- Different dialogs (create, edit) can be implemented in parallel

---

## Phase 1: Setup & Infrastructure

### Project Initialization

- [x] T001 Verify Phase 5 database layer is complete (DBReader, DBWriter, PodDBAdapter, QueueMetadata model) in `storage/database/` and `model/`
- [x] T002 Review AntennaPod EventBus patterns in existing fragments/activities to establish UI event subscription pattern
- [x] T003 Identify target layout for Queue pane modifications in `ui/fragment/QueueFragment.java`
- [x] T004 Audit bottom navigation bar implementation in `app/src/main/res/menu/` to understand queue button integration point

---

## Phase 2: Foundational Components

### Shared UI Infrastructure (Required for All Stories)

- [x] T005 [P] Create `QueueViewModel.java` in `ui/common/src/main/java/de/danoeh/antennapod/ui/common/` to manage queue state (currentQueueId, queue list) using Android ViewModel pattern
- [x] T006 [P] Create `QueueListAdapter.java` in `ui/common/src/main/java/de/danoeh/antennapod/ui/common/` to render queue items (name, color indicator) in RecyclerView
- [x] T007 Create `QueueEvent.java` event class in `event/` module (if not already exists from Phase 5) for EventBus communication on queue changes
- [x] T008 Create queue color palette utility in `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueColorPalette.java` with 12 theme-matched colors
- [x] T009 [P] Create layout files in `ui/common/src/main/res/layout/`:
  - `queue_item.xml` - Single queue item (name, color swatch)
  - `queue_switch_bottom_sheet.xml` - Queue selector pane structure

### Database Verification Tests

- [ ] T010 Unit test: Verify `DBReader.getAllQueues()` returns queues in creation order (oldest first) in `storage/database/src/test/java/.../QueueOrderTest.java`
- [ ] T011 Unit test: Verify `DBReader.getQueue(queueId)` returns correct queue content in `storage/database/src/test/java/.../QueueContentTest.java`
- [ ] T012 Unit test: Verify `DBWriter.createQueue()` persists new queue and returns ID in `storage/database/src/test/java/.../QueueCreationTest.java`

---

## Phase 3: User Story 1 - Switch Between Queues (P1)

### Business Logic

- [x] T013 [P] [US1] Implement queue switching logic in `QueueViewModel.java`:
  - Method `switchActiveQueue(queueId)` that persists to `UserPreferences.setCurrentQueueId()`
  - Method `getCurrentQueueId()` that reads from preferences
  - Method `getQueueList()` that calls `DBReader.getAllQueues()` and exposes via LiveData

- [x] T014 [P] [US1] Create `QueuePlaybackManager.java` in `playback/` to implement pause-load-restore logic:
  - Method `pauseCurrentQueue()` - saves current episode position
  - Method `switchToQueue(queueId)` - pauses old queue, loads new queue's last position
  - Method `restoreQueuePosition(queueId)` - loads episode position from QueueMetadata

### UI Components

- [x] T015 [P] [US1] Create `QueueSwitchBottomSheet.java` fragment in `ui/common/src/main/java/de/danoeh/antennapod/ui/common/` to display queue selector:
  - Inflate `queue_switch_bottom_sheet.xml`
  - Display queue list via `QueueListAdapter`
  - Handle queue item click → call `QueueViewModel.switchActiveQueue(queueId)`
  - Subscribe to `QueueEvent` on `onStart()`, unsubscribe on `onStop()`

- [ ] T016 [US1] Add queue button to bottom navigation bar in `app/src/main/res/menu/` with bookshelf icon (3 books of different sizes)

- [ ] T017 [US1] Modify `QueueFragment.java` to:
  - Display title bar gradient using active queue's color (from `QueueViewModel.getCurrentQueue()`)
  - Display queue name as title instead of "Queue"
  - Subscribe to `QueueEvent` to update UI when queue changes

- [ ] T018 [P] [US1] Create layout files in `ui/common/src/main/res/layout/`:
  - `queue_pane_header.xml` - Reusable title bar gradient layout (used in all main panes)

### Integration

- [ ] T019 [US1] Integrate queue button into bottom navigation: Update host activity/fragment to launch `QueueSwitchBottomSheet` on queue button tap

- [ ] T020 [US1] Apply title bar gradient to all main panes (Play, Queue, Inbox, Episodes, Queue selection):
  - Modify respective fragment layouts to include `queue_pane_header.xml`
  - Bind queue color from `QueueViewModel` in each fragment

### Tests

- [ ] T021 [P] [US1] Unit test: `QueueViewModelTest.java` - Test queue switching and getCurrentQueueId() persistence
- [ ] T022 [P] [US1] Unit test: `QueuePlaybackManagerTest.java` - Test pause-load-restore logic with mock episode positions
- [ ] T023 [US1] Integration test: `QueueSwitchIntegrationTest.java` (Espresso) - Test UI flow: tap queue button → select queue → verify title/gradient update

### Acceptance Criteria Fulfillment

- ✅ AC 1.1: Queue button in bottom nav switches active queue (T015, T019)
- ✅ AC 1.2: Queue position restored on switch (T014, T022)
- ✅ AC 1.3: Default queue created on init (existing, Phase 5)
- ✅ AC 1.4: Empty queue shows empty state (T017, requires QueueFragment enhancement)

---

## Phase 4: User Story 2 - Create and Name New Queues (P1)

### Business Logic

- [ ] T024 [P] [US2] Implement queue creation in `QueueViewModel.java`:
  - Method `createQueue(name: String, color: Int)` that calls `DBWriter.createQueue()`
  - Validate name is not empty (return error or exception)
  - Post `QueueEvent` on successful creation
  - Auto-switch to newly created queue

- [ ] T025 [US2] Update `DBWriter.createQueue()` signature to accept color parameter (if not already implemented in Phase 5)

### UI Components

- [ ] T026 [P] [US2] Create `QueueDialogManager.java` in `ui/common/src/main/java/de/danoeh/antennapod/ui/common/` to manage create/edit dialogs:
  - Factory method `showCreateQueueDialog(fragment, callback)`
  - Dialog layout: name input field (alphanumeric + emoji support) + color picker + action buttons

- [ ] T027 [P] [US2] Create dialog layout files in `ui/common/src/main/res/layout/`:
  - `queue_creation_dialog.xml` - TextInputEditText for name, color grid, confirm/cancel buttons
  - `queue_color_picker.xml` - 12-color grid with tick mark on selected color

- [ ] T028 [US2] Integrate "Create new queue" button into `QueueSwitchBottomSheet.java`:
  - Add floating action button or header button to trigger `QueueDialogManager.showCreateQueueDialog()`
  - Handle callback: call `QueueViewModel.createQueue(name, color)`

### Tests

- [ ] T029 [P] [US2] Unit test: `QueueViewModelTest.java` - Test createQueue() validation (empty name rejected) and persistence
- [ ] T030 [P] [US2] Unit test: `QueueDialogManagerTest.java` - Test dialog name/color input handling
- [ ] T031 [US2] Integration test: `QueueCreationIntegrationTest.java` (Espresso) - Test full flow: tap create → enter name → pick color → confirm → verify queue appears in list

### Strings & Localization

- [ ] T032 [US2] Add English strings to `ui/common/src/main/res/values/strings.xml`:
  - `queue_creation_title` - "Create Queue"
  - `queue_name_hint` - "Queue Name"
  - `queue_name_empty_error` - "Queue name cannot be empty"
  - `create_button` - "Create"

### Acceptance Criteria Fulfillment

- ✅ AC 2.1: Queue creation dialog opens on button tap (T028)
- ✅ AC 2.2: Name input and persistence (T024, T025)
- ✅ AC 2.3: All queues display in list (T015, existing QueueListAdapter)
- ✅ AC 2.4: Empty name validation (T024, T029)

---

## Phase 5: User Story 3 - Edit Queue Properties (P2)

### Business Logic

- [ ] T033 [P] [US3] Implement queue editing in `QueueViewModel.java`:
  - Method `renameQueue(queueId, newName)` that calls `DBWriter.renameQueue()`
  - Method `changeQueueColor(queueId, colorId)` that calls `DBWriter.changeQueueColor()`
  - Validate new name is not empty
  - Post `QueueEvent` on successful edit
  - Update currentQueue if currently active

- [ ] T034 [P] [US3] Ensure `DBWriter.renameQueue()` and `DBWriter.changeQueueColor()` exist (from Phase 5 implementation)

### UI Components

- [ ] T035 [P] [US3] Extend `QueueDialogManager.java` with edit dialog:
  - Factory method `showEditQueueDialog(fragment, queueId, callback)`
  - Dialog layout: current name in text field, current color highlighted in palette, confirm/cancel buttons

- [ ] T036 [P] [US3] Create dialog layout files in `ui/common/src/main/res/layout/`:
  - `queue_edit_dialog.xml` - Same as creation but pre-populated with current queue name/color

- [ ] T037 [US3] Integrate edit action into `QueueSwitchBottomSheet.java`:
  - Add edit icon/button to each queue item in `QueueListAdapter`
  - Handle tap: call `QueueDialogManager.showEditQueueDialog(queueId)`

- [ ] T038 [P] [US3] Enhance title bar gradient display:
  - Ensure gradient updates in real-time when queue color changes (subscribe to QueueEvent in each fragment)
  - Test gradient updates in Play, Queue, Inbox, Episodes panes

### Tests

- [ ] T039 [P] [US3] Unit test: `QueueViewModelTest.java` - Test renameQueue() and changeQueueColor() validation and persistence
- [ ] T040 [P] [US3] Unit test: `QueueDialogManagerTest.java` - Test dialog pre-population with current queue data
- [ ] T041 [US3] Integration test: `QueueEditIntegrationTest.java` (Espresso) - Test full flow: tap edit → change name/color → confirm → verify changes in list and title bar

### Strings & Localization

- [ ] T042 [US3] Add English strings to `ui/common/src/main/res/values/strings.xml`:
  - `queue_edit_title` - "Edit Queue"
  - `queue_color_label` - "Queue Color"
  - `queue_select_color` - "Select a color"

### Acceptance Criteria Fulfillment

- ✅ AC 3.1: Edit dialog shows current name/color (T035, T036)
- ✅ AC 3.2: Color picker with tick mark (T036)
- ✅ AC 3.3: Emoji support in name (validated via T039, handled by TextInputEditText)
- ✅ AC 3.4: Changes persist (T033, T039)
- ✅ AC 3.5: Title bar gradient updates (T038, T041)
- ✅ AC 3.6: Queues displayed in creation order (existing in QueueListAdapter, T010)

---

## Phase 6: User Story 4 - Delete Queues with Safeguards (P2)

### Business Logic

- [ ] T043 [P] [US4] Implement queue deletion in `QueueViewModel.java`:
  - Method `deleteQueue(queueId)` that calls `DBWriter.deleteQueue()`
  - Check if queue count > 1 before allowing deletion
  - If deleting current queue, auto-switch to another available queue
  - Post `QueueEvent` on successful deletion

- [ ] T044 [P] [US4] Ensure `DBWriter.deleteQueue()` exists (from Phase 5 implementation)

### UI Components

- [ ] T045 [US4] Integrate "Delete queue" button into `QueueDialogManager.java` (modify `showEditQueueDialog()`):
  - Show delete button only if queue count > 1 (pass count from `QueueViewModel`)
  - Delete button tap → confirmation dialog
  - Confirmation dialog: "Delete queue? Episodes will be removed from this queue only."
  - Confirm → call `QueueViewModel.deleteQueue(queueId)`

- [ ] T046 [P] [US4] Create confirmation dialog layout in `ui/common/src/main/res/layout/`:
  - `queue_deletion_confirmation_dialog.xml` - Warning message, confirm/cancel buttons

### Tests

- [ ] T047 [P] [US4] Unit test: `QueueViewModelTest.java` - Test deleteQueue() only allows if count > 1, handles auto-switch
- [ ] T048 [P] [US4] Unit test: `QueueDialogManagerTest.java` - Test delete button visibility logic
- [ ] T049 [US4] Integration test: `QueueDeletionIntegrationTest.java` (Espresso) - Test: tap delete → confirm → verify queue removed and active queue switched

### Strings & Localization

- [ ] T050 [US4] Add English strings to `ui/common/src/main/res/values/strings.xml`:
  - `queue_delete_title` - "Delete Queue"
  - `queue_delete_message` - "Delete this queue? Episodes will be removed from this queue only."
  - `queue_delete_button` - "Delete"
  - `queue_cannot_delete_last` - "Cannot delete the last queue"

### Acceptance Criteria Fulfillment

- ✅ AC 4.1: Delete button visible only if count > 1 (T045, T048)
- ✅ AC 4.2: Deletion works and auto-switches queue (T043, T049)
- ✅ AC 4.3: Confirmation dialog (T045, T046)
- ✅ AC 4.4: Deleted queue removed from UI (T049)

---

## Phase 7: User Story 5 - Copy and Move Episodes Between Queues (P3)

### Business Logic

- [ ] T051 [P] [US5] Create `QueueDragActionHandler.java` in `ui/common/src/main/java/de/danoeh/antennapod/ui/common/` to manage drag actions:
  - Method `onCopyToQueueSelected(episodeId, destinationQueueId)` that calls `DBWriter.addQueueItem(destinationQueueId, episode)`
  - Method `onMoveToQueueSelected(episodeId, sourceQueueId, destinationQueueId)` that calls `DBWriter.removeQueueItem()` then `addQueueItem()`
  - Filter queue list: only show queues NOT containing the episode

- [ ] T052 [P] [US5] Verify `DBWriter.addQueueItem()` and `removeQueueItem()` support queueId parameter (from Phase 1-5 implementation)

### UI Components

- [ ] T053 [US5] Integrate copy/move actions into existing drag-action system:
  - Locate queue item drag handler in `QueueFragment.java` (or adapter)
  - Add "Copy to queue" and "Move to queue" to action menu (alongside existing actions)

- [ ] T054 [P] [US5] Create `QueueSelectorDialog.java` in `ui/common/src/main/java/de/danoeh/antennapod/ui/common/` for copy/move destination selection:
  - Display list of queues NOT containing the episode
  - Implement filtering logic
  - Callback on queue selection

- [ ] T055 [P] [US5] Create queue selector dialog layout in `ui/common/src/main/res/layout/`:
  - `queue_selector_dialog.xml` - List of filtered queues, cancel button

- [ ] T056 [US5] Handle drag action callback:
  - On "Copy to queue": show `QueueSelectorDialog` → on selection call `QueueDragActionHandler.onCopyToQueueSelected()`
  - On "Move to queue": show filtered `QueueSelectorDialog` → on selection call `QueueDragActionHandler.onMoveToQueueSelected()`

### Tests

- [ ] T057 [P] [US5] Unit test: `QueueDragActionHandlerTest.java` - Test copy/move logic and queue filtering
- [ ] T058 [P] [US5] Unit test: `QueueSelectorDialogTest.java` - Test queue filtering (exclude queues containing episode)
- [ ] T059 [US5] Integration test: `QueueCopyMoveIntegrationTest.java` (Espresso) - Test: drag episode → select copy/move → choose destination → verify episode in both/new queue

### Strings & Localization

- [ ] T060 [US5] Add English strings to `ui/common/src/main/res/values/strings.xml`:
  - `copy_to_queue` - "Copy to Queue"
  - `move_to_queue` - "Move to Queue"
  - `queue_select_destination` - "Select Destination Queue"
  - `queue_already_contains` - "Already in this queue"

### Acceptance Criteria Fulfillment

- ✅ AC 5.1: Drag menu shows copy/move actions (T053)
- ✅ AC 5.2: Queue selector filters correctly (T054, T058)
- ✅ AC 5.3: Copy adds to destination (T051, T059)
- ✅ AC 5.4: Move removes from source and adds to destination (T051, T059)
- ✅ AC 5.5: Playback status syncs globally (existing Phase 5 design)

---

## Phase 8: Polish & Cross-Cutting Concerns

### Code Quality

- [ ] T061 Run `./gradlew checkstyle` on all new code in `ui/common/` and verify zero violations per AntennaPod constitution
- [ ] T062 Run `./gradlew spotbugsPlayDebug spotbugsDebug` and resolve any medium/high priority bugs
- [ ] T063 Run `./gradlew :app:lintPlayDebug` and resolve all lint errors (warnings treated as errors)
- [ ] T064 Format all new XML layout files using android-xml-formatter tool

### Integration & System Tests

- [ ] T065 [P] End-to-end test: Create 3 queues → switch between them → verify title/gradient updates and episode positions restore
- [ ] T066 [P] End-to-end test: Copy episode across queues → mark as played in one queue → switch to another queue containing same episode → verify auto-skip to next unplayed
- [ ] T067 End-to-end test: Delete active queue → verify auto-switch to another queue and UI consistency

### Documentation

- [ ] T068 Add JavaDoc comments to all public methods in QueueViewModel, QueueSwitchBottomSheet, QueueDialogManager, QueueDragActionHandler
- [ ] T069 Add inline comments in complex logic (queue filtering, playback state restoration, auto-skip logic)
- [ ] T070 Update project README or architecture documentation if new patterns are introduced

### Performance Verification

- [ ] T071 Verify queue switching completes within 500ms (success criterion SC-002)
- [ ] T072 Verify queue creation completes within 10 seconds (success criterion SC-001)
- [ ] T073 Verify playback transition (pause-load-restore) completes within 1 second (success criterion SC-004)

### Database Cleanup

- [ ] T074 Ensure all new Robolectric tests call `DBWriter.tearDownTests()` in `tearDown()` method to prevent "Illegal connection pointer" errors

---

## Dependencies & Execution Order

### Critical Path (MVP - Stories 1-2)

```
Phase 1: Setup & Infrastructure
  ↓
Phase 2: Foundational Components (T005-T012)
  ↓
Phase 3: User Story 1 (T013-T023) [Provides testable queue switching]
  ↓
Phase 4: User Story 2 (T024-T032) [Provides testable queue creation]
  ↓
[MVP COMPLETE - Ready for user testing]
```

### Extended Scope (Stories 3-5)

```
Phase 5: User Story 3 (T033-T042)
  ↓
Phase 6: User Story 4 (T043-T050)
  ↓
Phase 7: User Story 5 (T051-T060)
  ↓
Phase 8: Polish & System Tests (T061-T074)
```

### Task Dependencies Within Each Phase

**Phase 2**: T005, T006, T008 can run in parallel (no dependencies). T007 depends on Phase 5 completion check. T009, T010-T012 depend on T005-T008 being available.

**Phase 3**: T013-T014 (business logic) can start immediately. T015-T017 depend on T005-T009. T019-T020 depend on T015-T018. T021-T023 depend on T013-T020.

**Phase 4**: T024-T025 can start immediately. T026-T027 depend on T005-T009 existing. T028 depends on T026-T027. T029-T032 depend on T024-T028.

**Phase 5-7**: Similar pattern - business logic first, then UI, then tests.

---

## Parallel Execution Opportunities

### By Component (Within Same Phase)

**Phase 2**:
```
Developer A: T005 (QueueViewModel)
Developer B: T006 (QueueListAdapter) [parallel with A]
Developer C: T008 (QueueColorPalette) [parallel with A, B]
Developer D: T009 (Layout files) [can start once T008 done]
```

**Phase 3**:
```
Developer A: T013-T014 (Business logic - QueueViewModel, QueuePlaybackManager)
Developer B: T015, T016, T017 (UI - QueueSwitchBottomSheet, navigation, QueueFragment) [parallel with A]
Developer C: T018, T038 (Gradient layout) [parallel with A, B]
Developer D: T021-T023 (Tests) [can start once A, B done]
```

---

## Testing Strategy

### Unit Tests (Per Story)

- T021, T029, T039, T047, T057: ViewModel/Manager logic (boundary conditions, state transitions, error handling)
- T022, T030, T040, T048, T058: Dialog/Handler logic (input validation, queue filtering)

### Integration Tests (Per Story)

- T023, T031, T041, T049, T059: End-to-end UI flows (Espresso, multi-step user journeys)

### System Tests (Phase 8)

- T065-T067: Cross-story scenarios (multi-queue interactions, edge cases)

---

## Success Metrics

| Task | Acceptance Criteria |
|------|-------------------|
| Phase 1 | Setup complete; no errors in Phase 2 dependency checks |
| Phase 2 | All foundational components compile; unit tests T010-T012 pass |
| Phase 3 (MVP) | T023 integration test passes; queue switching works end-to-end |
| Phase 4 (MVP+1) | T031 integration test passes; queue creation works end-to-end |
| Phase 5-7 | All story integration tests pass; editing, deletion, copy/move workflows complete |
| Phase 8 | Code quality gates pass; end-to-end tests pass; performance metrics within targets |

---

## Notes

- **Constitution Compliance**: All tasks follow AntennaPod principles (Code Quality, Test Coverage, Modular Architecture, Event-Driven, Database Integrity)
- **Database Pattern**: All write operations use `DBWriter` (single-threaded executor); all reads use `DBReader` (synchronous)
- **EventBus Pattern**: All queue changes post `QueueEvent`; all UI subscriptions use `ThreadMode.MAIN` with proper lifecycle registration
- **MVP Definition**: MVP = User Stories 1-2 (switching and creating queues) provide core value; can be deployed, tested, and demonstrated independently
- **No New Dependencies**: Uses only existing AntennaPod patterns (Android Framework, EventBus, Robolectric, Espresso)
