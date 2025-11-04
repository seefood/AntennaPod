# Phase 6: Queue Feature Completion - Implementation Tasks

**Status**: PROPOSED (pending user approval)
**Purpose**: Consolidate all incomplete work from Phases 1-5 into a single completion phase
**Priority**: P0 - CRITICAL (blocks Phase 7 gradient feature)

---

## Overview

This phase completes the multiple queues feature by finishing all incomplete tasks from Phases 1-5. Upon completion, users will have a fully functional queue management system with switching, creation, editing, and deletion capabilities.

**Iron Rule**: NO task from Phases 1-5 left incomplete. Phase 7 (gradient) cannot start until this phase is 100% complete.

---

## Phase 6.1: Critical Blockers (3-4 hours)

**Goal**: Unblock all UI work by completing missing preferences and event infrastructure.

### T001: Create QueuePreferences.java
**Blocker Level**: CRITICAL (blocks ALL UI work)
**Source**: Phase 1, T024

**File to Create**:
- `storage/preferences/src/main/java/de/danoeh/antennapod/storage/preferences/QueuePreferences.java`

**Implementation**:
```java
public class QueuePreferences {
    private static final String PREF_NAME = "QueuePreferences";
    private static final String PREF_CURRENT_QUEUE_ID = "current_queue_id";
    private static final long DEFAULT_QUEUE_ID = 1L;

    public static long getCurrentQueueId() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getLong(PREF_CURRENT_QUEUE_ID, DEFAULT_QUEUE_ID);
    }

    public static void setCurrentQueueId(long queueId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putLong(PREF_CURRENT_QUEUE_ID, queueId).apply();
    }
}
```

**Verification**:
- [ ] File compiles without errors
- [ ] getCurrentQueueId() returns 1 by default
- [ ] setCurrentQueueId() persists value across app restarts
- [ ] Used in DBWriter for default queueId parameter resolution

---

### T002: Modify PlaybackPreferences.java
**Blocker Level**: CRITICAL
**Source**: Phase 1, T025

**File to Modify**:
- `storage/preferences/src/main/java/de/danoeh/antennapod/storage/preferences/PlaybackPreferences.java`

**Changes Required**:
1. Remove or deprecate `PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID`
2. Remove or deprecate `PREF_CURRENTLY_PLAYING_FEED_ID`
3. Update JavaDoc to reference QueueMetadata table instead
4. Keep method signatures but update implementation to query QueueMetadata

**Migration Strategy**:
- On first launch after upgrade, copy old pref values to default queue's QueueMetadata record
- Then delete old prefs

**Verification**:
- [ ] Old preferences removed or marked @Deprecated
- [ ] getCurrentlyPlayingFeedMedia() now queries QueueMetadata
- [ ] Migration tested with simulated old preference data

---

### T003: Enhance QueueEvent.java
**Blocker Level**: HIGH
**Source**: Phase 1, T026

**File to Modify**:
- `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java`

**Add Action Enum**:
```java
public enum Action {
    QUEUE_CREATED,
    QUEUE_RENAMED,
    QUEUE_COLOR_CHANGED,
    QUEUE_DELETED,
    QUEUES_REORDERED,
    QUEUE_ITEM_MOVED,
    QUEUE_SWITCHED,
    CURRENTLY_PLAYING_UPDATED
}
```

**Add Fields**:
```java
private final Action action;
private final long queueId;  // -1 if not applicable
```

**Update Constructor**:
```java
public QueueEvent(Action action, long queueId) {
    this.action = action;
    this.queueId = queueId;
}
```

**Verification**:
- [ ] All action types defined
- [ ] Fields accessible via getters
- [ ] JavaDoc explains each action type

---

### T004: Update DBWriter Event Posting
**Blocker Level**: HIGH
**Source**: Phase 1, T027

**File to Modify**:
- `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java`

**Changes Required**:
Ensure all queue methods post QueueEvent with correct action:
- `createQueue()` → `QueueEvent(QUEUE_CREATED, queueId)`
- `renameQueue()` → `QueueEvent(QUEUE_RENAMED, queueId)`
- `changeQueueColor()` → `QueueEvent(QUEUE_COLOR_CHANGED, queueId)`
- `deleteQueue()` → `QueueEvent(QUEUE_DELETED, queueId)`
- `reorderQueues()` → `QueueEvent(QUEUES_REORDERED, -1)`
- `moveQueueItem()` → `QueueEvent(QUEUE_ITEM_MOVED, queueId)`
- `setCurrentlyPlaying()` → `QueueEvent(CURRENTLY_PLAYING_UPDATED, queueId)`

**Verification**:
- [ ] All methods post events
- [ ] Events include correct action type
- [ ] Events include correct queueId

---

### T005: Run Code Quality Checks
**Blocker Level**: HIGH
**Source**: Phase 1, T034-T036

**Commands to Run**:
```bash
# Checkstyle
./gradlew checkstyle

# SpotBugs
./gradlew spotbugsPlayDebug spotbugsDebug

# Lint
./gradlew :storage:database:lintPlayDebug :storage:preferences:lintPlayDebug :model:lintDebug
```

**Verification**:
- [ ] Checkstyle: ZERO violations
- [ ] SpotBugs: ZERO high/medium findings
- [ ] Lint: ZERO errors (warnings OK)

**Action on Failure**: Fix all violations before proceeding to 6.2

---

## Phase 6.2: MainActivity Integration (6-8 hours)

**Goal**: Connect queue UI to MainActivity so users can access queue management.

### T006: Add Queue Navigation Item
**Source**: Phase 3, T001

**Files to Modify**:
1. `app/src/main/java/de/danoeh/antennapod/ui/screen/drawer/NavigationNames.java`
   - Add `QUEUE = "queue"` tag constant
   - Add entry to `NAMES` array: `{QUEUE, R.string.queue_label, R.drawable.ic_queue}`

2. `app/src/main/java/de/danoeh/antennapod/storage/preferences/UserPreferences.java`
   - Add `"queue"` to default visible drawer items order (position 2 or 3)

**Icon to Use**: `ic_queue_music_24dp` (bookshelf with 3 books) or create new icon

**Verification**:
- [ ] Queue button appears in bottom navigation
- [ ] Button shows correct icon
- [ ] Button position is 2nd or 3rd in nav bar

---

### T007: Integrate QueueSwitchBottomSheet into MainActivity
**Source**: Phase 3, T002

**File to Modify**:
- `app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java`

**Implementation**:
In `onCreate()` or navigation setup:
```java
if (itemId == R.id.bottom_navigation_queue) {
    QueueSwitchBottomSheet queueSheet = new QueueSwitchBottomSheet();
    queueSheet.setOnQueueEditListener(queueId -> {
        // Handle edit request (Phase 5 verification)
        showQueueEditDialog(queueId);
    });
    queueSheet.show(getSupportFragmentManager(), "queue_switch");
    return true;
}
```

**Verification**:
- [ ] Tapping queue button opens QueueSwitchBottomSheet
- [ ] Bottom sheet shows all queues
- [ ] Bottom sheet has "Create Queue" button
- [ ] Tapping queue in list dismisses sheet

---

### T008: Verify Queue Creation Dialog Integration
**Source**: Phase 4, T002-T003

**Files to Check**:
1. Does `QueueDialogManager.showCreateQueueDialog()` exist?
2. Is it called from QueueSwitchBottomSheet's create button?
3. Does it show name input + 12-color picker?
4. Does it call `QueueViewModel.createQueue(name, color)`?

**Verification Steps**:
- [ ] Tap "Create Queue" button
- [ ] Dialog appears with name field and color picker
- [ ] Enter name and select color
- [ ] Tap "Create" → dialog dismisses
- [ ] New queue appears in list
- [ ] New queue auto-switches to active

**If Missing**: Implement T002-T003 from Phase 4 plan

---

### T009: Verify Queue Edit Menu Integration
**Source**: Phase 5, T006-T011

**Files to Check**:
1. Does `queue_switch_item.xml` have 3-dot menu button?
2. Does QueueSwitchBottomSheet handle menu button clicks?
3. Does menu show: Rename, Change Color, Delete?
4. Is "Delete" disabled when only 1 queue exists?

**Verification Steps**:
- [ ] Long-press or tap menu button on queue item
- [ ] Menu shows: Rename, Change Color, Delete
- [ ] Delete hidden if only 1 queue
- [ ] Tap Rename → shows rename dialog
- [ ] Tap Change Color → shows color picker
- [ ] Tap Delete → shows confirmation dialog

**If Missing**: Implement T006-T011 from Phase 5 plan

---

## Phase 6.3: Playback Integration (4-6 hours)

**Goal**: Ensure queue switching properly saves/restores playback state.

### T010: Connect QueueViewModel to PlaybackController
**Source**: Phase 3, T003

**File to Modify**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java`

**Enhance `switchActiveQueue(queueId)` method**:
```java
public void switchActiveQueue(long newQueueId) {
    long currentQueueId = QueuePreferences.getCurrentQueueId();

    if (currentQueueId == newQueueId) {
        return; // Already on this queue
    }

    // Save current playback state to current queue
    saveCurrentPlaybackState(currentQueueId);

    // Update active queue
    QueuePreferences.setCurrentQueueId(newQueueId);

    // Load new queue's episodes
    loadQueueEpisodes(newQueueId);

    // Restore new queue's playback state
    restorePlaybackState(newQueueId);

    // Post event
    EventBus.getDefault().post(new QueueEvent(Action.QUEUE_SWITCHED, newQueueId));
}

private void saveCurrentPlaybackState(long queueId) {
    if (PlaybackController.isPlaying()) {
        long feedMediaId = PlaybackController.getCurrentFeedMediaId();
        long position = PlaybackController.getCurrentPosition();
        DBWriter.setCurrentlyPlaying(queueId, feedMediaId, position);
    }
}

private void restorePlaybackState(long queueId) {
    QueueMetadata queue = DBReader.getQueueMetadataById(queueId);
    if (queue != null && queue.isEpisodePlaying()) {
        long feedMediaId = queue.getCurrentlyPlayingFeedMediaId();
        // Load episode and seek to position
        PlaybackController.playEpisode(feedMediaId);
    }
}
```

**Verification**:
- [ ] Play episode in Queue A
- [ ] Switch to Queue B → playback pauses
- [ ] Queue B episodes load
- [ ] Switch back to Queue A → playback resumes at saved position

---

### T011: Add Visual Feedback for Active Queue
**Source**: Phase 3, T004

**File to Modify**:
- `app/src/main/java/de/danoeh/antennapod/ui/screen/drawer/BottomNavigation.java`

**Implementation**:
Subscribe to QueueEvent in BottomNavigation:
```java
@Subscribe(threadMode = ThreadMode.MAIN)
public void onQueueEvent(QueueEvent event) {
    if (event.getAction() == Action.QUEUE_SWITCHED) {
        // Update visual indicator
        updateSelectedItem(R.id.bottom_navigation_queue);
        // Optionally show badge with queue name/color
    }
}
```

**Verification**:
- [ ] Active queue visually highlighted in bottom nav
- [ ] Highlight updates when switching queues
- [ ] Highlight persists across screen rotations

---

### T012: Handle Bottom Sheet Lifecycle
**Source**: Phase 3, T005

**File to Check**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSwitchBottomSheet.java`

**Verification**:
- [ ] Sheet dismisses when queue selected
- [ ] No memory leaks (verify with LeakCanary if available)
- [ ] No ANR when rapidly switching queues
- [ ] Handles orientation changes gracefully

**If Issues Found**: Fix lifecycle bugs, add proper cleanup in onDestroyView()

---

## Phase 6.4: End-to-End Testing (6-8 hours)

**Goal**: Validate all Phase 2 user stories work correctly.

### T013: Test Queue Switching (User Story 1, P1)

**Test Scenarios**:
1. **Given** user has multiple queues with episodes
   **When** user taps queue button in bottom navigation
   **Then** QueueSwitchBottomSheet appears with all queues

2. **Given** bottom sheet is open
   **When** user taps a different queue
   **Then** sheet dismisses, episode list updates, queue becomes active

3. **Given** user switches away from a queue while episode playing
   **When** user switches back to that queue
   **Then** queue restores to same episode position

4. **Given** queue has no episodes
   **When** user switches to that queue
   **Then** empty state is displayed

**Pass Criteria**: All 4 scenarios pass

---

### T014: Test Queue Creation (User Story 2, P1)

**Test Scenarios**:
1. **Given** user is on queue switcher
   **When** user taps "Create new queue" button
   **Then** creation dialog appears with name input and color picker

2. **Given** creation dialog is open
   **When** user enters name and selects color, then confirms
   **Then** new queue is created, persisted, and becomes active

3. **Given** multiple queues exist
   **When** user views queue list
   **Then** all queues displayed with names

4. **Given** user leaves queue name blank
   **When** user attempts to create queue
   **Then** validation error shown, queue not created

**Pass Criteria**: All 4 scenarios pass

---

### T015: Test Queue Editing (User Story 3, P2)

**Test Scenarios**:
1. **Given** user has a queue
   **When** user taps edit/menu on that queue
   **Then** edit menu opens with Rename, Change Color, Delete options

2. **Given** edit menu open
   **When** user selects Change Color
   **Then** color picker shows with 12 colors, current color has tick mark

3. **Given** color picker open
   **When** user taps different color and confirms
   **Then** color change persists, visible in queue list

4. **Given** edit menu open
   **When** user selects Rename
   **Then** rename dialog shows current name

5. **Given** rename dialog open
   **When** user changes name (including emoji) and confirms
   **Then** name change persists, displays in queue list

**Pass Criteria**: All 5 scenarios pass

---

### T016: Test Queue Deletion (User Story 4, P2)

**Test Scenarios**:
1. **Given** user has multiple queues
   **When** user opens edit menu for a queue
   **Then** "Delete queue" option is visible

2. **Given** "Delete queue" tapped
   **When** user confirms deletion
   **Then** queue removed, active queue switches to another available queue

3. **Given** only one queue remains
   **When** user opens edit menu
   **Then** "Delete queue" option is hidden

4. **Given** queue is deleted
   **When** user navigates to any screen
   **Then** deleted queue no longer appears

**Pass Criteria**: All 4 scenarios pass

---

### T017: Test Edge Cases

**Scenarios**:
- [ ] Empty queue name validation
- [ ] Very long queue name (250+ chars) truncates
- [ ] Emoji in queue names displays correctly
- [ ] Rapidly switching queues doesn't crash
- [ ] Switching while playing transitions smoothly
- [ ] Switching while paused preserves position
- [ ] Rotating device during switch maintains state
- [ ] App restart preserves active queue
- [ ] Deleting currently active queue auto-switches
- [ ] 10+ queues scrollable in list

**Pass Criteria**: All edge cases handled gracefully

---

## Phase 6.5: Documentation & Polish (2-3 hours)

**Goal**: Complete all documentation and finalize code quality.

### T018: Update JavaDoc
**Source**: Phase 1, T037

**Files to Update**:
- All new classes: QueueMetadata, QueueMetadataCursor, QueuePreferences
- All modified classes: DBReader, DBWriter, QueueEvent, PlaybackPreferences
- QueueViewModel, QueueSwitchBottomSheet, QueueDialogManager

**Requirements**:
- [ ] All public methods have @param, @return, @throws annotations
- [ ] Examples for complex methods (e.g., switchActiveQueue)
- [ ] 100% JavaDoc coverage for new classes

---

### T019: Update CLAUDE.md
**Source**: Phase 1, T039

**File to Modify**:
- `CLAUDE.md` (root level)

**Add Section**: "Modifying Queue (Multiple Queues v3080100+)"

**Content to Add**:
```markdown
### Multiple Queues (v3080100+)
- Each episode can exist in at most one queue at a time
- Active queue tracked in SharedPreferences: `QueuePreferences.getCurrentQueueId()`
- Queue operations default to current active queue if queueId not specified
- Queue methods accept optional `queueId` parameter

**Creating Queue**:
```java
long queueId = DBWriter.createQueue("Work Podcasts", 0xFFFF6B6B).get();
QueuePreferences.setCurrentQueueId(queueId);
```

**Switching Queue**:
```java
QueueViewModel.switchActiveQueue(queueId);
// Automatically saves/restores playback state
```

**Adding Episode to Specific Queue**:
```java
DBWriter.addQueueItem(feedItem, queueId);
```

**Getting Episodes from Queue**:
```java
List<FeedItem> episodes = DBReader.getQueue(queueId);
```
```

---

### T020: Create Migration Troubleshooting Guide
**Source**: Phase 1, T040

**File to Create**:
- `specs/001-multiple-queues/migration-troubleshooting.md`

**Content**:
- Common migration errors and solutions
- Step-by-step migration validation checklist
- Rollback procedures if needed
- Database integrity verification queries

---

### T021: Update All Phase Spec Files
**Source**: Phase 1, T041

**Files to Update**:
1. `specs/001-multiple-queues/tasks.md` - Mark T024-T041 complete
2. `specs/003-queue-ui-integration/plan.md` - Mark T001-T006 complete
3. `specs/004-queue-creation/plan.md` - Mark T001-T006 complete/verified
4. `specs/005-queue-editing-ui/plan.md` - Mark T001-T012 complete/verified
5. Create `specs/006-queue-feature-completion/summary.md` - Final status

**Verification**:
- [ ] All task checkboxes updated
- [ ] Completion dates recorded
- [ ] Known issues documented

---

### T022: Final Quality Gate

**Run All Checks**:
```bash
# Build
./gradlew assemblePlayDebug

# Tests
./gradlew :storage:database:testPlayDebugUnitTest

# Code Quality
./gradlew checkstyle spotbugsPlayDebug :app:lintPlayDebug
```

**Pass Criteria**:
- [ ] Build succeeds with ZERO warnings
- [ ] All database tests pass
- [ ] Checkstyle: ZERO violations
- [ ] SpotBugs: ZERO high/medium findings
- [ ] Lint: ZERO errors

---

## Success Criteria - Phase 6 Complete

### Functional Completeness
- [ ] User can see queue button in bottom navigation
- [ ] Tapping queue button shows queue switcher
- [ ] User can create new queue with name and color
- [ ] User can switch between queues
- [ ] Queue switching preserves playback state
- [ ] User can rename existing queues
- [ ] User can change queue colors (12-color palette)
- [ ] User can delete queues (with confirmation)
- [ ] Active queue visually indicated
- [ ] All Phase 2 User Stories P1-P2 scenarios pass

### Technical Completeness
- [ ] QueuePreferences class exists and works
- [ ] PlaybackPreferences migrated
- [ ] QueueEvent has all action types
- [ ] All DBWriter methods post appropriate events
- [ ] Checkstyle passes (zero violations)
- [ ] SpotBugs passes (zero high/medium findings)
- [ ] Lint passes (zero errors)
- [ ] All 6 database test classes pass
- [ ] Build succeeds with no warnings

### Documentation Completeness
- [ ] CLAUDE.md updated with queue examples
- [ ] JavaDoc 100% coverage for new/modified methods
- [ ] Migration troubleshooting guide created
- [ ] All phase spec files updated with actual status

---

## Dependencies & Execution Order

**Critical Path** (must be sequential):
1. T001 (QueuePreferences) → blocks ALL other tasks
2. T002-T005 (Events, preferences, quality) → blocks UI integration
3. T006-T009 (MainActivity integration) → blocks playback integration
4. T010-T012 (Playback integration) → blocks testing
5. T013-T017 (End-to-end testing) → validates everything
6. T018-T022 (Documentation & polish) → final quality gate

**Parallelizable Work**:
- T002-T005 can be done in parallel after T001
- T013-T017 test scenarios can be executed in parallel
- T018-T021 documentation can be done in parallel

---

## Estimated Timeline

| Sub-Phase | Duration | Cumulative |
|-----------|----------|------------|
| 6.1 Critical Blockers | 3-4 hours | 3-4 hours |
| 6.2 MainActivity Integration | 6-8 hours | 9-12 hours |
| 6.3 Playback Integration | 4-6 hours | 13-18 hours |
| 6.4 End-to-End Testing | 6-8 hours | 19-26 hours |
| 6.5 Documentation & Polish | 2-3 hours | 21-29 hours |

**Total**: 21-29 hours (~3-4 full development days)

---

## Next Steps After Phase 6

1. **Validate 100% Completion**: Use checklist in Success Criteria section
2. **Code Review**: Full review of all changes (optional but recommended)
3. **Create Release Notes**: Document new queue feature for users
4. **Start Phase 7**: Queue Color Gradient (only after Phase 6 100% complete)

---

## Notes

- This phase consolidates work from 5 different phases
- Use TodoWrite tool extensively to track progress
- Mark tasks complete immediately after finishing
- Run quality checks frequently (don't wait until end)
- If any task reveals additional missing work, ADD IT IMMEDIATELY to this document
- No shortcuts—100% completion required
