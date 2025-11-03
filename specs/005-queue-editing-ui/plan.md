# Phase 5: Queue Editing UI

## Objectives

Implement complete queue editing UI that allows users to:
1. Create new queues with name and color selection
2. Rename existing queues
3. Change queue color (12-color palette)
4. Delete queues (with confirmation)
5. Reorder queues (drag & drop or menu)

**Note:** Backend database layer and ViewModel already implemented in Phase 4.
This phase focuses exclusively on UI/UX components.

## Backend Status (Already Implemented)

**ViewModel Methods Ready:**
- `QueueViewModel.createQueue(name, color)`
- `QueueViewModel.renameQueue(queueId, newName)`
- `QueueViewModel.changeQueueColor(queueId, color)`
- `QueueViewModel.deleteQueue(queueId)`
- `QueueViewModel.switchActiveQueue(queueId)`

**Database Methods Ready:**
- `DBWriter.createQueue(name, color)`
- `DBWriter.renameQueue(queueId, newName)`
- `DBWriter.changeQueueColor(queueId, color)`
- `DBWriter.deleteQueue(queueId)`
- `DBWriter.updateQueuePlaybackState(queueId, feedMediaId)`

**Events Ready:**
- `QueueEvent.QUEUE_CREATED`
- `QueueEvent.QUEUE_RENAMED`
- `QueueEvent.QUEUE_COLOR_CHANGED`
- `QueueEvent.QUEUE_DELETED`
- `QueueEvent.QUEUE_SWITCHED`

## Implementation Strategy

### T001: Create QueueDialogManager

**Task**: Factory class to show dialogs for queue creation/editing

**Approach**:
- Static factory methods for each dialog type
- Handle dialog lifecycle and callbacks
- Manage TextInputLayout for validation
- Color picker with visual feedback

**Files to Create**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueDialogManager.java`

---

### T002: Create Queue Creation Dialog

**Task**: Dialog for creating new queue with name + color

**Approach**:
- Material AlertDialog with custom view
- TextInputEditText for queue name
- Color palette picker (12 colors, with selection indicator)
- Validation: reject empty names
- Callback to `QueueViewModel.createQueue(name, color)`

**Layout Files to Create**:
- `ui/common/src/main/res/layout/queue_creation_dialog.xml`
- `ui/common/src/main/res/layout/queue_color_picker.xml`

**Features**:
- Real-time name validation
- Color preview in button
- Error message for empty name
- Success/cancel callbacks

---

### T003: Create Queue Rename Dialog

**Task**: Dialog for renaming existing queue

**Approach**:
- Similar to creation but with pre-filled current name
- Validation: non-empty, different from current name
- Callback to `QueueViewModel.renameQueue(queueId, newName)`
- Show current name in title/hint

**Layout Files to Create**:
- `ui/common/src/main/res/layout/queue_rename_dialog.xml`

---

### T004: Create Queue Color Picker Dialog

**Task**: Standalone color picker dialog

**Approach**:
- 12-color palette matching design system
- Grid layout with color circles
- Selected color has checkmark overlay
- Callback returns selected color
- Can be reused for creation, rename, or standalone

**Layout Files to Create**:
- `ui/common/src/main/res/layout/queue_color_picker_dialog.xml`

---

### T005: Create Queue Deletion Confirmation Dialog

**Task**: Confirmation dialog before deleting queue

**Approach**:
- Confirm user really wants to delete
- Show queue name in message
- Warning: "Episodes stay in queue, episodes themselves not deleted"
- Two buttons: Cancel, Delete
- Callback to `QueueViewModel.deleteQueue(queueId)`

**Layout**: Reuse standard Material AlertDialog

---

### T006: Integrate into QueueSwitchBottomSheet

**Task**: Add edit buttons/menu to queue list items

**Approach**:
- Each queue item in list has action buttons OR
- Long-press opens context menu with: Edit, Rename, Change Color, Delete, Reorder

**Options**:
- A) Add 3-dot menu button to each queue item → shows PopupMenu
- B) Long-press queue item → shows BottomSheetDialog with options

**Recommended**: Option A (3-dot menu) for clarity

**Files to Modify**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSwitchBottomSheet.java`
- `ui/common/src/main/res/layout/queue_switch_item.xml` (add menu button)

---

### T007: Create QueueEditMenuAdapter

**Task**: Adapter/helper for showing context menu on queue items

**Approach**:
- Show menu with: Rename, Change Color, Delete, (Reorder if multiple queues)
- Handle click callbacks to open appropriate dialogs
- Disable delete if only 1 queue remains

**Files to Create**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueEditMenuHelper.java`

---

### T008: Add String Resources

**Task**: Add i18n strings for all dialogs

**Strings to Add**:
```
queue_creation_title = "Create New Queue"
queue_name_label = "Queue Name"
queue_name_hint = "Enter queue name (max 100 chars)"
queue_color_label = "Choose Color"
queue_create_button = "Create"
queue_cancel_button = "Cancel"

queue_rename_title = "Rename Queue"
queue_rename_hint = "Enter new name"
queue_rename_button = "Rename"

queue_delete_title = "Delete Queue?"
queue_delete_message = "Delete \"%s\"? Episodes will remain in library."
queue_delete_confirm = "Delete"
queue_last_queue_error = "Cannot delete the last queue"

queue_color_picker_title = "Pick Color"
queue_empty_name_error = "Queue name cannot be empty"
queue_name_too_long_error = "Name too long (max 100 chars)"
```

**Files to Modify**:
- `ui/i18n/src/main/res/values/strings.xml`

---

### T009: Design Color Palette

**Task**: Define 12 standard colors for queue customization

**Approach**:
- Use Material Design color palette
- Define in colors.xml
- Ensure sufficient contrast for text overlay
- Create visual indicators for selection

**Files to Modify**:
- `ui/common/src/main/res/values/colors.xml` (add queue colors)
- Create `queue_color_definitions.xml` with array

**Suggested Colors**:
- Red, Pink, Purple, Deep Purple, Indigo, Blue
- Cyan, Teal, Green, Light Green, Lime, Amber

---

### T010: Create QueueListViewHolder with Edit Button

**Task**: Update queue list item layout to include edit menu

**Approach**:
- Add 3-dot menu button to right side of queue item
- Show queue name, current episode (if any), color indicator
- Click edit button → show PopupMenu or launch dialogs

**Layout Update**:
- `ui/common/src/main/res/layout/queue_switch_item.xml`

**Files to Modify**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSwitchBottomSheet.java` (ViewHolder)

---

### T011: Handle Edit Menu Actions

**Task**: Wire up menu clicks to appropriate dialogs

**Flow**:
1. User clicks 3-dot menu on queue item
2. PopupMenu shows: Rename, Change Color, Delete
3. Click Rename → show rename dialog
4. Click Change Color → show color picker dialog
5. Click Delete → show confirmation dialog
6. Each action calls ViewModel method via callback

**Files to Modify**:
- `QueueSwitchBottomSheet.java` (menu click handler)

---

### T012: Test Complete Queue Editing Flow

**Task**: Manual testing of all queue management features

**Test Cases**:

**Creation**:
- [ ] Tap "Create" button → shows creation dialog
- [ ] Enter name and select color → Create enabled
- [ ] Tap Create → dialog closes, new queue added, auto-switched
- [ ] Empty name shows error
- [ ] Very long name truncates with message

**Renaming**:
- [ ] Right-click/menu queue → Rename option
- [ ] Shows current name in input
- [ ] Change name → Rename button enabled
- [ ] Tap Rename → list updates immediately
- [ ] Same name as current shows error

**Color Change**:
- [ ] Right-click/menu queue → Change Color option
- [ ] Color picker shows with current selection
- [ ] Select new color → indicator shows change
- [ ] Color persists on list and mini player

**Deletion**:
- [ ] Right-click/menu queue → Delete option
- [ ] Confirmation shows queue name
- [ ] Tap Delete → queue removed from list
- [ ] Episodes remain in library
- [ ] Can't delete if only 1 queue
- [ ] Currently active queue can be deleted (switches to another)

**Edge Cases**:
- [ ] Emoji in queue name
- [ ] Unicode characters
- [ ] Very long names (250+ chars)
- [ ] Create/rename/delete with many queues
- [ ] Color persists across app restart
- [ ] Delete while queue is active

---

## Success Criteria

✅ QueueDialogManager created with static factory methods
✅ Queue creation dialog fully functional with validation
✅ Queue rename dialog implemented and working
✅ Queue color picker shows 12-color palette
✅ Queue deletion with confirmation dialog
✅ Context menu (3-dot) on queue items in list
✅ All ViewModel callbacks properly wired
✅ All string resources added and localized
✅ Color palette defined and visually distinct
✅ ViewHolders updated with edit buttons
✅ All dialogs dismiss and list updates on success
✅ Proper error messages for validation failures
✅ Build passes with no warnings
✅ All test cases pass

---

## Estimated Time

- T001 (QueueDialogManager): 1 hour
- T002 (Creation dialog): 2 hours
- T003 (Rename dialog): 1 hour
- T004 (Color picker): 1.5 hours
- T005 (Delete confirmation): 0.5 hours
- T006 (Integration into bottom sheet): 2 hours
- T007 (Edit menu helper): 1 hour
- T008 (String resources): 0.5 hours
- T009 (Color palette design): 0.5 hours
- T010 (ViewHolder updates): 1 hour
- T011 (Menu action handlers): 1.5 hours
- T012 (Testing): 2 hours

**Total**: ~15 hours of development

---

## Implementation Notes

**Important**: All backend methods already exist and are ready to use.
This phase is purely UI implementation.

**Architecture**:
- Follow Material Design guidelines
- Use existing Material AlertDialog components
- Keep dialogs lightweight (no heavy processing)
- Use ViewModel for state management
- Listen to QueueEvent for real-time updates

**Error Handling**:
- Show toast for errors (name validation, DB failures)
- Use error LiveData from ViewModel for message display
- Log all exceptions for debugging

**Performance**:
- Load colors from resources (not runtime)
- Cache dialog instances where possible
- Avoid blocking main thread
- Use ViewBinding for all dialogs
