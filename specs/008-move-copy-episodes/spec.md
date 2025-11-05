# Phase 8: Queue Episode Transfer Operations

**Feature**: Move and Copy Episodes Between Queues
**Status**: Specification
**Dependencies**: Phases 1-7 (Multiple Queues, Queue UI, Queue Color Gradient)
**Related**: [tasks.md](./tasks.md) | [plan.md](./plan.md)

## Overview

Enable users to move or copy episodes from one queue to another, supporting both single-episode and batch operations. This completes the queue management feature set by allowing flexible episode organization across multiple queues.

## Clarifications

### Session 2025-11-05

- Q: When moving/copying episodes to a target queue, where should they be inserted? → A: Always append to end of target queue, preserving source order
- Q: For batch move/copy operations, should they be atomic (all-or-nothing) or best-effort (partial success allowed)? → A: Best-effort: Move/copy what's possible, report skipped items with reasons
- Q: When an episode is copied to another queue, should it maintain its current playback position or reset to unplayed state? → A: Maintain playback position (shared state across all queues)
- Q: Should the queue selection dialog remember the last used destination queue for convenience? → A: Remember last destination per session only (cleared on app restart)
- Q: Should there be a maximum queue size limit to prevent performance issues? → A: No limit (match existing addQueueItem behavior)

## User Stories

### US-1: Move Single Episode to Another Queue
**As a** podcast listener
**I want to** move an episode from the current queue to a different queue
**So that** I can reorganize my listening schedule without losing my place

**Acceptance Criteria:**
- Episode is removed from source queue
- Episode is appended to end of target queue
- Original queue position is preserved for other episodes in source queue
- Operation is reversible (can move back)
- Works from episode context menu in queue view
- Works from episode detail screen

### US-2: Copy Single Episode to Another Queue
**As a** podcast listener
**I want to** copy an episode to multiple queues
**So that** I can have the same episode in different listening contexts (e.g., "Work" and "Favorites")

**Acceptance Criteria:**
- Episode remains in source queue
- Episode is appended to end of target queue
- Episode playback position is maintained (shared state across all queues)
- Episode can exist in multiple queues simultaneously
- Duplicate detection prevents adding to same queue twice
- Works from episode context menu

### US-3: Batch Move Episodes
**As a** podcast listener
**I want to** select multiple episodes and move them all to another queue
**So that** I can quickly reorganize large groups of episodes

**Acceptance Criteria:**
- Multi-select mode allows selecting multiple episodes
- All selected episodes are moved to target queue
- Original queue positions are maintained for remaining episodes
- Order of moved episodes is preserved in target queue
- Progress indicator for large batches (>50 episodes)

### US-4: Batch Copy Episodes
**As a** podcast listener
**I want to** copy multiple episodes to another queue at once
**So that** I can quickly build themed playlists

**Acceptance Criteria:**
- Multi-select mode allows selecting multiple episodes
- All selected episodes are copied to target queue
- Episodes already in target queue are skipped (no duplicates)
- User is notified of skipped duplicates
- Order is preserved in target queue

### US-5: Queue Selection Dialog
**As a** podcast listener
**I want to** see all available queues when moving/copying
**So that** I can easily choose the correct destination

**Acceptance Criteria:**
- Dialog shows all queues except source queue (for move operations)
- Dialog shows all queues except current queue (for copy operations)
- Each queue shows: name, color indicator, episode count
- Queues sorted by name or creation order, with last-used destination (in current session) highlighted at top
- "Create New Queue" option available in dialog
- Search/filter for large queue lists (>10 queues)
- Last destination memory cleared on app restart

## Technical Specification

**Note on Playback State**: Episode playback position and play/unplayed status are stored at the episode level (FeedMedia table), not queue-specific. When episodes are moved or copied between queues, they maintain their current playback state. This ensures consistent listening experience regardless of which queue the episode is played from.

**Implementation Strategy**: Maximize code reuse from existing DBWriter queue methods:
- **Copy operation** = existing `addQueueItem()` logic with target queueId parameter
- **Move operation** = existing `removeQueueItem()` + `addQueueItem()` with queueIds
- **Batch operations** = iterate using single-item methods (proven patterns)
- No queue size limits (matches existing `addQueueItem` behavior)

### Database Layer

#### New DBWriter Methods

```java
/**
 * Move episode from one queue to another.
 * Implementation: removeQueueItem(feedItemId, sourceQueueId) + addQueueItem(feedItemId, targetQueueId)
 *
 * @param feedItemId Episode to move
 * @param sourceQueueId Source queue ID
 * @param targetQueueId Target queue ID
 * @return Future that completes when operation finishes
 * @throws IllegalArgumentException if episode not in source queue
 */
public static Future<Void> moveQueueItem(long feedItemId, long sourceQueueId, long targetQueueId);

/**
 * Copy episode to another queue.
 * Implementation: delegates to existing addQueueItem() with target queueId parameter
 *
 * @param feedItemId Episode to copy
 * @param targetQueueId Target queue ID
 * @return Future that completes when operation finishes
 * @throws IllegalStateException if episode already in target queue
 */
public static Future<Void> copyQueueItem(long feedItemId, long targetQueueId);

/**
 * Move multiple episodes (best-effort).
 * Implementation: iterates moveQueueItem() for each episode
 *
 * @param feedItemIds List of episode IDs to move
 * @param sourceQueueId Source queue ID
 * @param targetQueueId Target queue ID
 * @return Future<MoveResult> with counts of moved/skipped episodes and skip reasons
 */
public static Future<MoveResult> moveQueueItems(List<Long> feedItemIds, long sourceQueueId, long targetQueueId);

/**
 * Copy multiple episodes (best-effort).
 * Implementation: iterates copyQueueItem() for each episode
 *
 * @param feedItemIds List of episode IDs to copy
 * @param targetQueueId Target queue ID
 * @return Future<CopyResult> with counts of copied/skipped episodes and skip reasons
 */
public static Future<CopyResult> copyQueueItems(List<Long> feedItemIds, long targetQueueId);
```

#### Result Classes

```java
public class MoveResult {
    public final int movedCount;
    public final int skippedCount;
    public final List<Long> skippedItemIds;
    public final Map<Long, String> skipReasons; // Episode ID -> reason (e.g., "not in source queue", "already in target")

    public MoveResult(int movedCount, int skippedCount, List<Long> skippedItemIds, Map<Long, String> skipReasons) {
        this.movedCount = movedCount;
        this.skippedCount = skippedCount;
        this.skippedItemIds = skippedItemIds;
        this.skipReasons = skipReasons;
    }
}

public class CopyResult {
    public final int copiedCount;
    public final int skippedCount;
    public final List<Long> skippedItemIds;
    public final Map<Long, String> skipReasons; // Episode ID -> reason (e.g., "already in target queue")

    public CopyResult(int copiedCount, int skippedCount, List<Long> skippedItemIds, Map<Long, String> skipReasons) {
        this.copiedCount = copiedCount;
        this.skippedCount = skippedCount;
        this.skippedItemIds = skippedItemIds;
        this.skipReasons = skipReasons;
    }
}
```

### UI Layer

#### QueueSelectionDialog

New dialog fragment for selecting destination queue:

**Location**: `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSelectionDialog.java`

**Features:**
- RecyclerView showing all available queues
- Each item shows: queue name, color badge, episode count
- Filter out source queue for move operations
- Last-used destination (in current session) highlighted at top of list
- Session-based memory only (cleared on app restart)
- "Create New Queue" button at bottom
- Search bar for filtering (if >10 queues)
- Material Design 3 dialog styling

**Layout**: `ui/common/src/main/res/layout/dialog_queue_selection.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/dialog_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/select_queue"
        android:textAppearance="?attr/textAppearanceHeadline6"
        android:layout_marginBottom="16dp"/>

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/search_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:visibility="gone"
        android:layout_marginBottom="8dp">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/search_field"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="@string/search_queues"/>
    </com.google.android.material.textfield.TextInputLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/queue_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:maxHeight="400dp"/>

    <Button
        android:id="@+id/create_new_queue_button"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/create_new_queue"
        android:layout_marginTop="16dp"/>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="end"
        android:layout_marginTop="16dp">

        <Button
            android:id="@+id/cancel_button"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@android:string/cancel"/>
    </LinearLayout>
</LinearLayout>
```

#### Context Menu Updates

Update episode context menus in:
- `QueueFragment` - episode long-press menu
- `EpisodeItemListAdapter` - swipe action menu
- `ItemPagerFragment` - episode detail overflow menu

Add menu items:
```xml
<item
    android:id="@+id/move_to_queue"
    android:title="@string/move_to_queue"
    android:icon="@drawable/ic_move"/>

<item
    android:id="@+id/copy_to_queue"
    android:title="@string/copy_to_queue"
    android:icon="@drawable/ic_copy"/>
```

#### Multi-Select Action Bar

Update `EpisodeMultiSelectActionHandler` with new actions:
- Move Selected to Queue
- Copy Selected to Queue

### Event Updates

Extend `QueueEvent` with new action types:

```java
public enum Action {
    // ... existing actions ...
    ITEM_MOVED,         // Episode moved between queues
    ITEM_COPIED,        // Episode copied to another queue
    ITEMS_BATCH_MOVED,  // Multiple episodes moved
    ITEMS_BATCH_COPIED  // Multiple episodes copied
}
```

### String Resources

Add to `ui/common/src/main/res/values/strings.xml`:

```xml
<!-- Phase 8: Queue Transfer Operations -->
<string name="move_to_queue">Move to Queue</string>
<string name="copy_to_queue">Copy to Queue</string>
<string name="select_queue">Select Queue</string>
<string name="search_queues">Search queues…</string>
<string name="episode_moved">Episode moved to %s</string>
<string name="episodes_moved">%d episodes moved to %s</string>
<string name="episode_copied">Episode copied to %s</string>
<string name="episodes_copied">%d episodes copied to %s</string>
<string name="episodes_skipped">%d episodes already in queue</string>
<string name="episode_already_in_queue">Episode already in this queue</string>
<string name="move_operation_failed">Failed to move episode</string>
<string name="copy_operation_failed">Failed to copy episode</string>
```

## Implementation Tasks

### Database Layer (Priority: High)
- [ ] **T079**: Implement `moveQueueItem()` - delegates to existing `removeQueueItem()` + `addQueueItem()` with queueId
- [ ] **T080**: Implement `copyQueueItem()` - delegates to existing `addQueueItem()` logic with target queueId
- [ ] **T081**: Implement `moveQueueItems()` - iterates `moveQueueItem()` with result aggregation
- [ ] **T082**: Implement `copyQueueItems()` - iterates `copyQueueItem()` with result aggregation
- [ ] **T083**: Create `MoveResult` and `CopyResult` classes in model module
- [ ] **T084**: Verify duplicate detection reuses existing `itemListContains()` logic from `addQueueItem()`
- [ ] **T085**: Update QueueEvent with new action types (ITEM_MOVED, ITEM_COPIED, etc.)

### UI Components (Priority: High)
- [ ] **T086**: Create `QueueSelectionDialog.java` dialog fragment with session-based last-destination memory
- [ ] **T087**: Create `dialog_queue_selection.xml` layout
- [ ] **T088**: Create `QueueSelectionAdapter` RecyclerView adapter with last-used highlighting
- [ ] **T089**: Create `queue_selection_item.xml` list item layout with color badge and count
- [ ] **T090**: Implement search/filter functionality for queue list

### Context Menu Integration (Priority: Medium)
- [ ] **T091**: Add "Move to Queue" and "Copy to Queue" to QueueFragment context menu
- [ ] **T092**: Add "Move to Queue" and "Copy to Queue" to EpisodeItemListAdapter swipe menu
- [ ] **T093**: Add "Move to Queue" and "Copy to Queue" to ItemPagerFragment overflow menu
- [ ] **T094**: Update `EpisodeMultiSelectActionHandler` with batch move/copy actions

### User Feedback (Priority: Medium)
- [ ] **T095**: Show Snackbar with success message after move operation
- [ ] **T096**: Show Snackbar with success message after copy operation
- [ ] **T097**: Show Snackbar with duplicate warning when copying to queue already containing episode
- [ ] **T098**: Show progress dialog for batch operations (>50 episodes)
- [ ] **T099**: Add undo action to Snackbar for move operations

### Testing (Priority: Medium)
- [ ] **T100**: Unit tests for DBWriter move/copy methods
- [ ] **T101**: Unit tests for duplicate detection
- [ ] **T102**: Unit tests for batch operations (edge cases: empty list, all duplicates, partial duplicates)
- [ ] **T103**: Espresso UI tests for QueueSelectionDialog
- [ ] **T104**: Espresso UI tests for context menu integration
- [ ] **T105**: Integration tests for move/copy with EventBus verification

### Error Handling (Priority: Low)
- [ ] **T106**: Handle move operation when source queue doesn't contain episode
- [ ] **T107**: Handle database transaction failures with rollback
- [ ] **T108**: Handle concurrent modifications (two users moving same episode)

### Documentation (Priority: Low)
- [ ] **T110**: Update CLAUDE.md with move/copy operation examples
- [ ] **T111**: Add user documentation for queue transfer operations
- [ ] **T112**: Document batch operation performance characteristics

## Performance Considerations

### Database Operations
- **Single Move**: <50ms (DELETE + INSERT + position renumbering)
- **Single Copy**: <30ms (INSERT only)
- **Batch Move (100 episodes)**: <500ms (transaction with bulk operations)
- **Batch Copy (100 episodes)**: <300ms (bulk INSERT)

### Optimizations
1. Use single transaction for batch operations
2. Bulk INSERT for copying multiple episodes
3. Efficient position renumbering using UPDATE with range conditions
4. Index on (queue_id, feeditem) for duplicate detection

### Memory Usage
- Dialog: Load queue list lazily (only when opened)
- Batch operations: Iterate single-item methods (reuse existing patterns, no additional buffering)
- Result objects: Minimal memory footprint

## User Experience

### Happy Path: Move Single Episode
1. User long-presses episode in QueueFragment
2. Context menu shows "Move to Queue"
3. User taps "Move to Queue"
4. QueueSelectionDialog appears with all queues except current
5. User selects destination queue
6. Episode disappears from current queue
7. Snackbar shows "Episode moved to [Queue Name]" with Undo action
8. If user taps Undo, episode moves back to original position

### Happy Path: Copy Multiple Episodes
1. User enters multi-select mode in QueueFragment
2. User selects 5 episodes
3. User taps "Copy to Queue" in action bar
4. QueueSelectionDialog appears with all queues
5. User selects destination queue
6. Snackbar shows "5 episodes copied to [Queue Name]"
7. Episodes remain in current queue

### Edge Cases
- **Copy to Same Queue**: Prevented, show "Episode already in this queue"
- **Move Last Episode from Queue**: Queue becomes empty (valid state)
- **Copy Already Present Episode**: Show "Episode already in [Queue Name], skipped"
- **Batch with Partial Duplicates**: Show "3 copied, 2 skipped (already in queue)"
- **Batch Move with Missing Episodes**: Best-effort: skip episodes not in source queue, report "5 moved, 2 skipped (not found in source)"
- **Batch Operation Errors**: Operations continue despite individual failures; final result shows success/skip counts with reasons

## Accessibility

- Screen reader announces queue selection dialog
- Each queue item has contentDescription: "[Queue Name], [N] episodes"
- Move/Copy actions have descriptive labels
- Snackbar messages are announced by TalkBack
- High contrast mode supported for color badges

## Security & Privacy

- No additional permissions required
- Operations respect existing queue access controls
- Database transactions ensure atomicity
- No data leaves device

## Rollout Strategy

### Phase 8.1: Single Episode Operations (Week 1)
- Implement move/copy for single episodes
- Basic QueueSelectionDialog
- Context menu integration

### Phase 8.2: Batch Operations (Week 2)
- Implement batch move/copy
- Multi-select integration
- Progress indicators

### Phase 8.3: Polish (Week 3)
- Undo functionality
- Search in queue list
- Performance optimization
- Testing and bug fixes

## Success Metrics

- **Functionality**: All 31 tasks completed
- **Performance**: All operations meet target latency (<50ms single, <500ms batch)
- **Quality**: Zero P0/P1 bugs in beta testing
- **Usability**: <5% user confusion rate (measured by support tickets)
- **Adoption**: 30% of users with multiple queues use move/copy within first month

## Future Enhancements (Post-Phase 8)

- Drag-and-drop between queues in queue management screen
- Smart queue suggestions based on podcast, genre, or listening patterns
- Bulk operations: "Move all unplayed episodes from X to Y"
- Queue merge: combine two queues into one
- Queue split: divide queue by criteria (podcast, date, etc.)
