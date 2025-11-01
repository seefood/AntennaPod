# Phase 3: Queue UI Integration Plan

## Objectives
Integrate the foundational queue UI components (QueueViewModel, QueueSwitchBottomSheet, QueueListAdapter) into the MainActivity and bottom navigation so users can:
1. See a queue button in the bottom navigation
2. Tap it to open the queue switch dialog
3. Select a different queue
4. See the episode list update
5. Playback continue from where they left off in that queue

## Implementation Strategy

### T001: Add Queue Navigation Item
**Task**: Make the queue button appear in the bottom navigation
**Approach**:
- The bottom navigation automatically builds from UserPreferences.getVisibleDrawerItemOrder()
- Add a queue navigation tag to the drawer items list
- Create NavigationNames entry for queue (tag, icon, label)
- Queue item should appear as 2nd or 3rd nav item (after Inbox/Subscriptions)

**Files Modified**:
- app/src/main/java/de/danoeh/antennapod/ui/screen/drawer/NavigationNames.java (add queue navigation)
- app/src/main/java/de/danoeh/antennapod/storage/preferences/UserPreferences.java (ensure queue in default order)

### T002: Integrate QueueSwitchBottomSheet into MainActivity
**Task**: Show the queue bottom sheet when queue nav item is tapped
**Approach**:
- Override onItemSelected() in MainActivity.bottomNavigation subclass
- Check if selected item is queue
- If queue: instantiate and show QueueSwitchBottomSheet
- If queue edit request: show queue edit dialog (Phase 5)

**Implementation**:
```java
if (itemId == R.id.bottom_navigation_queue) {
    QueueSwitchBottomSheet queueSheet = new QueueSwitchBottomSheet();
    queueSheet.setOnQueueEditListener(queueId -> {
        // Phase 5: Show edit dialog
    });
    queueSheet.show(getSupportFragmentManager(), "queue_switch");
    return;
}
```

**Files Modified**:
- app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java (handle queue navigation)

### T003: Connect QueueViewModel to PlaybackController
**Task**: Ensure switching queues properly saves/restores playback state
**Approach**:
- When user switches queues, get the queue's last playing position
- Call PlaybackController.seekTo() with that position
- Load the queue's episodes
- Start playback if was playing before

**Files Modified**:
- playback/src/main/java/de/danoeh/antennapod/playback/PlaybackController.java (add queue awareness)
- ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java (enhance switchActiveQueue)

### T004: Add Visual Feedback for Active Queue
**Task**: Highlight the currently active queue in the bottom navigation
**Approach**:
- Subscribe to queue switch events in BottomNavigation
- On switch event, find the queue nav item and call updateSelectedItem()
- Update the badge to show current queue info (optional)

**Files Modified**:
- app/src/main/java/de/danoeh/antennapod/ui/screen/drawer/BottomNavigation.java (listen for queue events)

### T005: Handle Bottom Sheet Dismissal
**Task**: Properly manage lifecycle when switching queues
**Approach**:
- When queue is selected, dismiss the bottom sheet
- Already implemented in QueueSwitchBottomSheet.onViewCreated()
- Ensure no state leaks or dangling listeners

**Testing**: Verify no ANR or memory leaks when repeatedly switching

### T006: Verify Playback State Preservation
**Task**: Test that playback resumes from saved position when switching back to queue
**Approach**:
- Play episode in Queue A
- Switch to Queue B (should pause playback, load Queue B)
- Switch back to Queue A (should resume from saved position)
- Verify episode list matches saved state

## Success Criteria

✅ Queue button appears in bottom navigation (2nd or 3rd item)
✅ Tapping queue button shows QueueSwitchBottomSheet
✅ Selecting a queue switches to it
✅ Episode list updates to show selected queue's episodes
✅ Active queue is visually highlighted
✅ Playback state is preserved when switching (Phase 3+ only)
✅ No ANR or crashes during queue switching
✅ Proper lifecycle management (no memory leaks)

## Testing Plan

1. **Manual UI Testing**:
   - Install debug build
   - Verify queue button appears in bottom nav
   - Tap queue button → sheet appears
   - Select queue → sheet dismisses, episodes update
   - Verify active queue is highlighted

2. **Playback Testing** (Phase 3+):
   - Play episode in Queue A
   - Switch to Queue B
   - Switch back to Queue A
   - Verify playback resumes at saved position

3. **Edge Cases**:
   - Only 1 queue exists (button should still appear)
   - Rapidly switching queues
   - Switching while playing
   - Switching while audio paused
   - Rotating device during switch

## Estimated Implementation Time
- T001 (Navigation setup): 1-2 hours
- T002 (Integration): 2-3 hours
- T003 (Playback sync): 2-3 hours
- T004 (Visual feedback): 1 hour
- T005 (Lifecycle): 1 hour
- T006 (Testing): 2 hours

**Total**: ~10 hours of development
