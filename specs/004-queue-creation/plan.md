# Phase 4: Queue Creation and Naming

## Objectives

Implement queue creation UI that allows users to:
1. Tap a "Create Queue" button in the QueueSwitchBottomSheet
2. Enter a queue name (alphanumeric + emoji support)
3. Select a color from the 12-color palette
4. Confirm to create the queue
5. Auto-switch to the newly created queue

## Implementation Strategy

### T001: Implement QueueDialogManager

**Task**: Create a dialog manager to handle queue creation and editing flows

**Approach**:
- Create new class `QueueDialogManager.java` in `ui/common`
- Factory method `showCreateQueueDialog(Fragment, callback)`
- Handle name input validation (not empty)
- Color picker with visual feedback for selection
- Return created queue name and color to callback

**Files to Create**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueDialogManager.java`

**Files to Modify**:
- None (pure addition)

### T002: Create Queue Creation Dialog Layout

**Task**: Design the dialog UI for creating queues

**Approach**:
- Create `queue_creation_dialog.xml` with:
  - TextInputEditText for queue name (alphanumeric + emoji)
  - RecyclerView or GridView showing 12 color options
  - Confirm and Cancel buttons
- Add `queue_color_picker.xml` to show color grid with selected indicator
- Use material design for consistency

**Files to Create**:
- `ui/common/src/main/res/layout/queue_creation_dialog.xml`
- `ui/common/src/main/res/layout/queue_color_picker.xml`

### T003: Add Create Queue Button to QueueSwitchBottomSheet

**Task**: Integrate queue creation into the queue switching UI

**Approach**:
- Modify `QueueSwitchBottomSheet.java` to add create button
- On button tap, call `QueueDialogManager.showCreateQueueDialog()`
- Pass callback to handle queue creation via `QueueViewModel.createQueue()`
- After creation, update queue list via observer

**Files to Modify**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSwitchBottomSheet.java`

### T004: Verify QueueViewModel.createQueue() Implementation

**Task**: Ensure queue creation with color is properly implemented

**Verification**:
- Check that `QueueViewModel.createQueue(name, color)` exists
- Validates name is not empty
- Calls `DBWriter.createQueue(name, color)`
- Posts `QueueEvent` on success
- Auto-switches to newly created queue
- Handles errors appropriately

**Files to Check**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java`

### T005: Add Queue Creation Strings

**Task**: Add string resources for UI labels

**Strings to Add**:
- `queue_creation_title` - "Create New Queue"
- `queue_name_label` - "Queue Name"
- `queue_name_hint` - "Enter queue name"
- `queue_color_label` - "Choose Color"
- `queue_create_button` - "Create"

**Files to Modify**:
- `ui/i18n/src/main/res/values/strings.xml`

### T006: Test Queue Creation Flow

**Task**: Manually test the complete queue creation workflow

**Test Cases**:
1. Tap queue button → shows QueueSwitchBottomSheet
2. Tap create button → shows creation dialog
3. Enter queue name and select color → Create button enabled
4. Tap Create → dialog dismisses, sheet updates, new queue appears in list
5. Verify new queue is auto-selected
6. Switch to another queue, then back to new queue
7. Test invalid input (empty name) → shows error

**Edge Cases**:
- Empty queue name should show error
- Very long queue name (250+ chars) truncates
- Special characters handled correctly
- Emoji support works
- Color selection persists

## Success Criteria

✅ QueueDialogManager created and handles dialog lifecycle
✅ Queue creation dialog shows name input and 12-color picker
✅ Users can create queue with name and color
✅ New queue auto-switches after creation
✅ Newly created queue appears in QueueSwitchBottomSheet list
✅ Queue color displays correctly
✅ Proper validation (name not empty)
✅ Error messages shown for invalid input
✅ Build passes with no warnings

## Estimated Time

- T001 (QueueDialogManager): 2-3 hours
- T002 (Dialog layouts): 1-2 hours
- T003 (Integration into bottom sheet): 1 hour
- T004 (Verification): 30 minutes
- T005 (Strings): 30 minutes
- T006 (Testing): 2 hours

**Total**: ~8 hours of development
