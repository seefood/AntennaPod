# Phase 6: Queue Feature Completion - CORRECTED Tasks
**Status**: PROPOSED (corrected after user review)
**Duration**: 12-18 hours (DOWN from original 21-29 hour estimate)
**Reason for Correction**: Original spec incorrectly assumed T024-T026 were missing. They were actually implemented by enhancing existing infrastructure (UserPreferences, QueueEvent).

---

## What's NOT Missing (User Correction)

❌ **REMOVED FROM PHASE 6**:
- ~~T001: Create QueuePreferences.java~~ → ALREADY EXISTS as UserPreferences.getCurrentQueueId/setCurrentQueueId
- ~~T002: Modify PlaybackPreferences~~ → INTENTIONALLY NOT MIGRATED (correct design - global vs per-queue state)
- ~~T003: Enhance QueueEvent~~ → ALREADY HAS all action types (QUEUE_CREATED, QUEUE_RENAMED, etc.)
- ~~T004: Update DBWriter event posting~~ → ALREADY POSTS events with correct actions
- ~~T005: Run initial code quality checks~~ → Moved to end of phase (T013)

**Time Saved**: 9-11 hours by not duplicating existing infrastructure

---

## Phase 6.1: MainActivity Integration (3-4 hours)

**Goal**: Connect queue UI to MainActivity so users can access queue management.

### T001: Add Queue Button to Bottom Navigation

**Files to Create/Modify**:
1. Check if `app/src/main/res/menu/bottom_navigation.xml` exists
   - If YES: Add queue item
   - If NO: Find correct menu file (might be drawer_menu.xml or navigation_drawer_items.xml)

2. Add queue menu item:
```xml
<item
    android:id="@+id/bottom_navigation_queue"
    android:icon="@drawable/ic_queue_music_24dp"
    android:title="@string/queue_label" />
```

3. Verify icon exists:
   - Check for `ic_queue_music_24dp.xml` in `app/src/main/res/drawable/`
   - If missing: create bookshelf icon or use existing queue icon

**Verification**:
- [ ] Queue button visible in bottom navigation
- [ ] Button shows correct icon
- [ ] Button label is "Queues" or equivalent

---

### T002: Add NavigationNames Entry

**File to Modify**:
- `app/src/main/java/de/danoeh/antennapod/ui/screen/drawer/NavigationNames.java`

**Add**:
```java
public static final String QUEUE = "queue";

// In NAMES array:
{QUEUE, R.string.queue_label, R.drawable.ic_queue_music_24dp}
```

**Verification**:
- [ ] QUEUE constant defined
- [ ] Entry added to NAMES array
- [ ] String resource exists (queue_label)

---

### T003: Add Queue to Default Visible Items

**File to Modify**:
- `app/src/main/java/de/danoeh/antennapod/storage/preferences/UserPreferences.java`

**Check Method**:
- Find `getVisibleDrawerItemOrder()` or similar
- Add `"queue"` to default visible items (position 2 or 3)

**Verification**:
- [ ] Queue appears in default navigation order
- [ ] Position is 2nd or 3rd in nav bar

---

### T004: Integrate QueueSwitchBottomSheet into MainActivity

**File to Modify**:
- `app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java`

**Implementation**:

1. Import QueueSwitchBottomSheet:
```java
import de.danoeh.antennapod.ui.common.QueueSwitchBottomSheet;
```

2. Find navigation item click handler (likely in onCreate() or setupBottomNavigation())

3. Add queue button handler:
```java
if (itemId == R.id.bottom_navigation_queue) {
    showQueueSwitcher();
    return true;
}
```

4. Add showQueueSwitcher() method:
```java
private void showQueueSwitcher() {
    QueueSwitchBottomSheet queueSheet = new QueueSwitchBottomSheet();
    queueSheet.show(getSupportFragmentManager(), "queue_switch");
}
```

**Verification**:
- [ ] QueueSwitchBottomSheet imported
- [ ] Handler added for R.id.bottom_navigation_queue
- [ ] Tapping queue button opens bottom sheet
- [ ] Bottom sheet shows all queues
- [ ] Bottom sheet dismisses when queue selected

---

## Phase 6.2: Verify UI Flows (4-6 hours)

**Goal**: Verify that queue creation, editing, and switching flows work end-to-end.

### T005: Verify Queue Creation Flow

**Files to Check**:
1. `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSwitchBottomSheet.java`
   - Does it have a "Create Queue" button?
   - Is the button click handler wired to QueueDialogManager?

2. `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueDialogManager.java`
   - Does `showCreateQueueDialog()` exist?
   - Does it show name input + 12-color picker?

**Manual Test**:
1. Open app → tap queue button
2. Tap "Create Queue" button
3. Enter name "Test Queue"
4. Select color (e.g., red)
5. Tap "Create"
6. **Expected**: Dialog dismisses, "Test Queue" appears in list with red color

**If Test Fails**:
- Find where "Create Queue" button should be
- Wire it to `QueueDialogManager.showCreateQueueDialog()`
- Verify dialog calls `QueueViewModel.createQueue(name, color)`

**Verification**:
- [ ] "Create Queue" button exists
- [ ] Button opens creation dialog
- [ ] Dialog has name input field
- [ ] Dialog has 12-color picker
- [ ] Creating queue adds it to list
- [ ] New queue auto-switches to active

---

### T006: Verify Queue Switching Flow

**Manual Test**:
1. Create 2+ queues (e.g., "Work", "Leisure")
2. Add episodes to each queue
3. Tap queue button
4. Select "Work" queue
5. **Expected**: Episode list updates to show "Work" queue episodes
6. Tap queue button again
7. Select "Leisure" queue
8. **Expected**: Episode list updates to show "Leisure" queue episodes

**If Test Fails**:
- Check if QueueSwitchBottomSheet calls `QueueViewModel.switchActiveQueue(queueId)`
- Check if QueueFragment subscribes to QueueEvent.QUEUE_SWITCHED
- Verify episode list reloads on queue switch

**Verification**:
- [ ] Tapping queue in list dismisses sheet
- [ ] Episode list updates to show selected queue
- [ ] Active queue ID persists across app restarts
- [ ] Switching is instant (<500ms)

---

### T007: Verify Queue Edit Menu Integration

**Files to Check**:
1. `ui/common/src/main/res/layout/queue_switch_item.xml`
   - Does queue item layout have a 3-dot menu button or long-press support?

2. `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueSwitchBottomSheet.java` or `QueueListAdapter.java`
   - Is there a menu click handler?
   - Does it show menu with: Rename, Change Color, Delete?

**Manual Test**:
1. Open queue switcher
2. Long-press on a queue OR tap 3-dot menu button
3. **Expected**: Menu appears with options: Rename, Change Color, Delete
4. **Expected**: Delete option hidden if only 1 queue exists

**If Test Fails**:
- Add menu button to queue_switch_item.xml layout
- Add click handler to show PopupMenu
- Wire menu items to QueueDialogManager methods

**Verification**:
- [ ] Menu button or long-press works
- [ ] Menu shows Rename, Change Color, Delete
- [ ] Delete hidden when only 1 queue

---

### T008: Verify Rename Queue Flow

**Manual Test**:
1. Open queue menu for a queue
2. Tap "Rename"
3. **Expected**: Dialog appears with current name pre-filled
4. Change name to "Updated Name"
5. Tap "Rename"
6. **Expected**: Dialog dismisses, queue name updates in list

**If Test Fails**:
- Check if QueueDialogManager has `showRenameQueueDialog(queueId, currentName)`
- Verify it calls `QueueViewModel.renameQueue(queueId, newName)`

**Verification**:
- [ ] Rename dialog shows current name
- [ ] Name change persists
- [ ] Name updates immediately in list
- [ ] Emoji in names works

---

### T009: Verify Change Color Flow

**Manual Test**:
1. Open queue menu for a queue
2. Tap "Change Color"
3. **Expected**: Color picker appears with 12 colors
4. **Expected**: Current color has tick mark
5. Tap different color
6. Tap "Confirm"
7. **Expected**: Dialog dismisses, queue color updates in list

**If Test Fails**:
- Check if QueueDialogManager has `showColorPickerDialog(queueId, currentColor)`
- Verify it calls `QueueViewModel.changeQueueColor(queueId, newColor)`

**Verification**:
- [ ] Color picker shows 12 colors
- [ ] Current color indicated with tick
- [ ] Color change persists
- [ ] Color updates immediately in list

---

### T010: Verify Delete Queue Flow

**Manual Test**:
1. Create 2+ queues
2. Open queue menu for a queue
3. Tap "Delete"
4. **Expected**: Confirmation dialog appears
5. **Expected**: Dialog shows queue name ("Delete 'Work Podcasts'?")
6. Tap "Delete"
7. **Expected**: Dialog dismisses, queue removed from list
8. **Expected**: Active queue switches to another if deleted queue was active
9. Create queues until only 1 remains
10. Open menu
11. **Expected**: Delete option is hidden/disabled

**If Test Fails**:
- Check if QueueDialogManager has `showDeleteConfirmationDialog(queueId, queueName)`
- Verify it calls `QueueViewModel.deleteQueue(queueId)`
- Add logic to hide delete button when queue count == 1

**Verification**:
- [ ] Confirmation dialog appears
- [ ] Queue removed on confirm
- [ ] Active queue switches if needed
- [ ] Delete hidden when only 1 queue

---

## Phase 6.3: Playback Integration Testing (2-3 hours)

**Goal**: Verify queue switching properly saves/restores playback state.

### T011: Test Playback State Save/Restore

**Manual Test**:
1. Create 2 queues: "Queue A", "Queue B"
2. Add episodes to both
3. Select Queue A
4. Play episode 1 in Queue A
5. Seek to 5:30 timestamp
6. **Switch to Queue B** (while playing)
7. **Expected**: Playback pauses
8. **Expected**: Queue B episodes appear
9. **Switch back to Queue A**
10. **Expected**: Episode 1 still selected
11. **Expected**: Playback position at 5:30 (saved position)
12. Press play
13. **Expected**: Playback resumes from 5:30

**If Test Fails**:
- Check QueueViewModel.switchActiveQueue() implementation (line 136-150 in QueueViewModel.java)
- Verify it calls `DBWriter.updateQueuePlaybackState(currentQueueId, feedMediaId)`
- Verify it loads new queue's saved state from `QueueMetadata.currently_playing_feedmedia_id`

**Verification**:
- [ ] Switching pauses playback
- [ ] Playback position saved to old queue
- [ ] Playback position restored from new queue
- [ ] Resume works from saved position

---

### T012: Test Edge Cases

**Test Scenarios**:
1. **Switch while paused**:
   - Pause episode at 3:00
   - Switch to different queue
   - Switch back
   - **Expected**: Position at 3:00, paused state maintained

2. **Rotate device during switch**:
   - Start switching queues
   - Rotate device mid-operation
   - **Expected**: No crash, switch completes

3. **Rapid queue switching**:
   - Switch between 3 queues rapidly (tap queue A, B, C, A in quick succession)
   - **Expected**: No ANR, no crashes, correct queue ends up active

4. **Empty queue**:
   - Switch to queue with no episodes
   - **Expected**: Empty state displayed (not a crash)

5. **10+ queues**:
   - Create 10 queues
   - **Expected**: Queue list scrollable, all queues visible

**Verification**:
- [ ] All edge cases handled gracefully
- [ ] No crashes
- [ ] No ANR (Application Not Responding)
- [ ] No memory leaks (optional: run LeakCanary)

---

## Phase 6.4: Quality & Documentation (3-5 hours)

**Goal**: Complete code quality checks and update documentation.

### T013: Run Code Quality Checks

**Commands**:
```bash
# Checkstyle
./gradlew checkstyle

# SpotBugs
./gradlew spotbugsPlayDebug spotbugsDebug

# Lint
./gradlew :app:lintPlayDebug

# All database tests
./gradlew :storage:database:testPlayDebugUnitTest
```

**Verification**:
- [ ] Checkstyle: ZERO violations
- [ ] SpotBugs: ZERO high/medium findings
- [ ] Lint: ZERO errors
- [ ] All tests pass

**Action on Failure**: Fix all violations before proceeding

---

### T014: Update JavaDoc

**Files to Update**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java`
- `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java` (queue methods)
- `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java` (queue methods)
- `storage/preferences/src/main/java/de/danoeh/antennapod/storage/preferences/UserPreferences.java` (getCurrentQueueId/setCurrentQueueId)

**Requirements**:
- [ ] All public methods have @param, @return annotations
- [ ] Complex methods have usage examples
- [ ] 100% JavaDoc coverage for queue-related methods

---

### T015: Update CLAUDE.md

**File to Modify**:
- `CLAUDE.md` (root level)

**Add Section** (after "Modifying Queue" section):

```markdown
### Active Queue Tracking (v3080100+)
- Active queue ID stored in UserPreferences: `UserPreferences.getCurrentQueueId()` / `setCurrentQueueId(long)`
- Defaults to queue ID 1 if not set
- Queue operations default to current active queue if queueId parameter not specified

**Switching Queues**:
```java
// Via ViewModel (recommended - handles playback state)
QueueViewModel viewModel = ...;
viewModel.switchActiveQueue(queueId);

// Via preferences directly (if no playback state to save)
UserPreferences.setCurrentQueueId(queueId);
```

**Getting Current Queue**:
```java
long currentQueueId = UserPreferences.getCurrentQueueId();
List<FeedItem> episodes = DBReader.getQueue(currentQueueId);
```

**Queue Metadata**:
- Each queue tracks: name, color, created_at, sort_order
- Per-queue playback state: currently_playing_feedmedia_id, currently_playing_feed_id
- Playback state saved when switching queues, restored when switching back
```

**Verification**:
- [ ] CLAUDE.md has queue examples
- [ ] Code examples are accurate
- [ ] Section placed in appropriate location

---

### T016: Update Phase Spec Files

**Files to Update**:
1. `specs/001-multiple-queues/tasks.md`
   - Mark T001-T023 as complete
   - Mark T024-T027 as "Implemented via existing infrastructure (not separate classes)"
   - Mark T028-T033 as complete (tests exist)
   - Mark T034-T041 as complete after Phase 6.4

2. `specs/003-queue-ui-integration/plan.md`
   - Mark T001-T006 as complete after Phase 6.1-6.3

3. `specs/004-queue-creation/plan.md`
   - Mark T001-T006 as complete/verified after Phase 6.2

4. `specs/005-queue-editing-ui/plan.md`
   - Mark T001-T012 as complete/verified after Phase 6.2

5. Create `specs/006-queue-feature-completion/summary.md`
   - Document what was actually completed
   - Note corrections to original spec assumptions
   - Final implementation status

**Verification**:
- [ ] All task checkboxes accurate
- [ ] Completion dates recorded
- [ ] Known issues/deviations documented

---

### T017: Final Validation

**Run Complete Build**:
```bash
./gradlew assemblePlayDebug
```

**Verification Checklist**:
- [ ] Build succeeds with ZERO warnings
- [ ] All database tests pass
- [ ] Checkstyle clean
- [ ] SpotBugs clean
- [ ] Lint clean
- [ ] Manual test: Open app → see queue button
- [ ] Manual test: Create queue → works
- [ ] Manual test: Switch queues → works
- [ ] Manual test: Edit queue → works
- [ ] Manual test: Delete queue → works
- [ ] Manual test: Playback state preserved across switches

**If Any Fail**: Go back and fix before marking Phase 6 complete

---

## Success Criteria - Phase 6 Complete

### Functional Completeness
- [ ] Queue button visible in bottom navigation
- [ ] Tapping queue button shows queue switcher
- [ ] User can create new queue with name and color
- [ ] User can switch between queues
- [ ] Queue switching preserves playback state
- [ ] User can rename existing queues
- [ ] User can change queue colors (12-color palette)
- [ ] User can delete queues (with confirmation)
- [ ] All Phase 2 User Stories P1-P2 scenarios pass

### Technical Completeness
- [ ] MainActivity integrated with QueueSwitchBottomSheet
- [ ] Bottom navigation has queue button
- [ ] Checkstyle passes (zero violations)
- [ ] SpotBugs passes (zero high/medium findings)
- [ ] Lint passes (zero errors)
- [ ] All 6 database test classes pass
- [ ] Build succeeds with no warnings

### Documentation Completeness
- [ ] CLAUDE.md updated with queue examples
- [ ] JavaDoc complete for all modified methods
- [ ] Phase spec files updated with actual status

---

## Execution Order (Dependency-Based)

**Sequential (must follow order)**:
1. T001-T004 (MainActivity integration) → blocks all UI testing
2. T005-T010 (Verify UI flows) → blocks playback testing
3. T011-T012 (Playback testing) → validates everything works
4. T013-T017 (Quality & docs) → final gate before Phase 7

**No parallelization possible** - each phase depends on previous completing.

---

## Timeline (CORRECTED)

| Sub-Phase | Tasks | Duration | Cumulative |
|-----------|-------|----------|------------|
| 6.1 MainActivity Integration | T001-T004 | 3-4 hours | 3-4 hours |
| 6.2 Verify UI Flows | T005-T010 | 4-6 hours | 7-10 hours |
| 6.3 Playback Testing | T011-T012 | 2-3 hours | 9-13 hours |
| 6.4 Quality & Docs | T013-T017 | 3-5 hours | 12-18 hours |

**Total**: 12-18 hours (~1.5-2.5 days)

**Savings**: 9-11 hours saved by not duplicating existing infrastructure

---

## Next Steps After Phase 6

1. **Validate 100% Completion**: Use checklist in Success Criteria section
2. **Move gradient spec**: Rename `specs/006-queue-color-gradient/` → `specs/007-queue-color-gradient/`
3. **Start Phase 7**: Queue Color Gradient (ONLY after Phase 6 100% complete)

---

## Notes

- This corrected phase removes 5 unnecessary tasks (T001-T005 from original)
- Focuses on genuine missing work: MainActivity integration, verification, testing
- UserPreferences, QueueEvent already enhanced (no new classes needed)
- PlaybackPreferences intentionally NOT migrated (correct architectural decision)
- 100% completion required before Phase 7
