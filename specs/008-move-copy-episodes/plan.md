# Implementation Plan: Queue Episode Transfer Operations

**Branch**: `008-move-copy-episodes` | **Date**: 2025-11-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/008-move-copy-episodes/spec.md`

## Summary

Enable users to move or copy episodes between queues with single-episode and batch operations. The feature provides a queue selection dialog with session-based last-destination memory, context menu integration across multiple screens, and best-effort batch processing with detailed result reporting. Implementation maximizes code reuse from existing DBWriter methods (move = removeQueueItem + addQueueItem, copy = addQueueItem).

**Technical Approach**: Extend DBWriter with move/copy methods that delegate to existing queue operations. Create QueueSelectionDialog with RecyclerView showing all queues (filtered based on operation type). Integrate into QueueFragment, EpisodeItemListAdapter, and ItemPagerFragment context menus. Use best-effort batch processing with MoveResult/CopyResult classes to track successes and skipped items with reasons.

## Technical Context

**Language/Version**: Java 17 (source/target compatibility), Kotlin for build scripts
**Primary Dependencies**: Android SDK API 35, AndroidX RecyclerView, Material Design 3 dialogs, ViewBinding
**Storage**: SQLite via PodDBAdapter (Queue table with queue_id, existing DBWriter/DBReader patterns)
**Testing**: JUnit 4, Robolectric for Android-dependent code, Espresso for UI tests
**Target Platform**: Android API 21+ (minSdk: 21, targetSdk: 35)
**Project Type**: Android multi-module application (37 Gradle modules)
**Performance Goals**:
- Single move: <50ms (DELETE + INSERT + position renumbering)
- Single copy: <30ms (INSERT only)
- Batch move (100 episodes): <500ms (transaction with bulk operations)
- Batch copy (100 episodes): <300ms (bulk INSERT)
**Constraints**:
- Episodes can only exist in one queue at a time (enforced by existing addQueueItem logic)
- Playback position maintained when copying (FeedMedia table stores position, not queue-specific)
- Session-based last-destination memory only (cleared on app restart)
- No queue size limits (matches existing addQueueItem behavior)
- Must pass checkstyle, SpotBugs, and Lint with zero violations
**Scale/Scope**:
- 2 new result classes (~60 LOC)
- 4 new DBWriter methods (~200 LOC) - delegates to existing methods
- 1 new dialog fragment (~300 LOC)
- 2 new layout files (~100 lines XML)
- 1 new RecyclerView adapter (~200 LOC)
- Context menu updates in 3 fragments (~50 LOC each)
- Event updates (~20 LOC)
- Total estimated: ~950 new/modified LOC

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### ✅ I. Code Quality First (NON-NEGOTIABLE)

**Status**: PASS (gates will be validated during implementation)

- **Checkstyle**: Will run `./gradlew checkstyle` - target zero violations
- **SpotBugs**: Will run `./gradlew spotbugsPlayDebug spotbugsDebug` - target zero bugs
- **Android Lint**: Will run `./gradlew :app:lintPlayDebug` - target zero errors
- **XML Formatting**: Will format all new layout files with android-xml-formatter

**Compliance**: Feature involves Java code (DBWriter, dialog, adapters) and XML layouts (dialog, list items). All quality gates will be enforced before merge.

### ✅ II. Test Coverage for New Features

**Status**: PASS (tests required)

- Unit tests required for:
  - `DBWriter.moveQueueItem()` - verify episode removed from source, added to target
  - `DBWriter.copyQueueItem()` - verify episode added to target, remains in source
  - `DBWriter.moveQueueItems()` - verify batch operations with partial failures
  - `DBWriter.copyQueueItems()` - verify duplicate detection and skipping
  - `MoveResult` and `CopyResult` classes - verify result aggregation
- Integration tests (Espresso) for:
  - QueueSelectionDialog display and filtering
  - Context menu integration (move/copy actions)
  - Multi-select batch operations
  - Undo functionality

**Exemption**: None - full test coverage required for new business logic and UI.

### ✅ III. Modular Architecture Preservation

**Status**: PASS (respects module boundaries)

- New code isolated to:
  - `model/` - MoveResult, CopyResult result classes
  - `storage/database/` - DBWriter methods (delegates to existing operations)
  - `ui/common/` - QueueSelectionDialog, QueueSelectionAdapter
  - `app/` - Context menu integration in fragments
- No new modules created
- No cross-module dependency violations
- UI layer invokes storage layer via DBWriter (existing pattern)
- Database layer posts QueueEvent (existing pattern)

**Compliance**: Feature follows existing architecture - storage operations in DBWriter, UI in fragments/dialogs, events via EventBus.

### ✅ IV. Event-Driven Communication

**Status**: PASS (uses existing EventBus patterns)

- Extends `QueueEvent` with new action types (ITEM_MOVED, ITEM_COPIED, ITEMS_BATCH_MOVED, ITEMS_BATCH_COPIED)
- DBWriter operations post QueueEvent automatically
- Fragments subscribe to QueueEvent for UI updates
- Dialog uses fragment callbacks, not direct EventBus posting
- All subscriptions registered in `onStart()`, unregistered in `onStop()`

**Compliance**: Follows existing ViewModel + EventBus architecture. Extends QueueEvent rather than creating new event types.

### ✅ V. Database Integrity

**Status**: PASS (uses existing DBWriter patterns)

- No schema modifications required
- All writes via DBWriter methods (single-threaded executor)
- Move operation: `removeQueueItem(feedItemId, sourceQueueId)` + `addQueueItem(feedItemId, targetQueueId)` (atomic via single DB call)
- Copy operation: delegates to existing `addQueueItem()` logic with queueId parameter
- Duplicate detection reuses existing `itemListContains()` from addQueueItem
- All operations post QueueEvent via existing patterns

**Compliance**: Feature only adds DBWriter methods that delegate to existing operations. No direct SQL writes, no schema changes. Follows single-threaded executor pattern.

### 📋 Localization & Translation

**Status**: PASS (new English strings only)

- New strings added to values/strings.xml:
  - `move_to_queue`, `copy_to_queue`, `select_queue`
  - Success/error messages for operations
- Only English strings modified
- Translations managed via Transifex

### 📋 Product Flavor Awareness

**Status**: PASS (no flavor-specific code)

- Move/copy operations apply to both Play and Free flavors identically
- No Chromecast or proprietary dependencies
- Shared across both flavors via `ui/common/` and `storage/database/` modules

### 📋 Dependency Management

**Status**: PASS (uses existing dependencies)

- No new external dependencies
- Uses existing AndroidX RecyclerView, Material Design components
- No build tool upgrades required

**Summary**: All constitution principles are satisfied. No violations to justify. Proceeding to Phase 0 research.

## Project Structure

### Documentation (this feature)

```text
specs/008-move-copy-episodes/
├── spec.md              # Feature specification (already created with clarifications)
├── plan.md              # This file (Phase 0-1 planning)
├── research.md          # Phase 0: Research findings
├── data-model.md        # Phase 1: Result classes data model
├── quickstart.md        # Phase 1: Quick reference for implementation
├── contracts/           # Phase 1: DBWriter API contracts
└── tasks.md             # Phase 2: Generated by /speckit.tasks
```

### Source Code (repository root)

**Existing Structure** (37 Gradle modules, Android multi-module):

```text
# Files Created in Phase 8:
model/src/main/java/de/danoeh/antennapod/model/
├── MoveResult.java                  # NEW: Result class for move operations
└── CopyResult.java                  # NEW: Result class for copy operations

ui/common/src/main/java/de/danoeh/antennapod/ui/common/
├── QueueSelectionDialog.java        # NEW: Dialog for selecting destination queue
└── QueueSelectionAdapter.java       # NEW: RecyclerView adapter for queue list

ui/common/src/main/res/layout/
├── dialog_queue_selection.xml       # NEW: Queue selection dialog layout
└── queue_selection_item.xml         # NEW: Queue list item layout

ui/common/src/test/java/de/danoeh/antennapod/ui/common/
└── QueueSelectionDialogTest.java    # NEW: Unit tests for dialog

storage/database/src/test/java/de/danoeh/antennapod/storage/
└── DBWriterQueueTransferTest.java   # NEW: Unit tests for move/copy operations

# Files Modified in Phase 8:
storage/database/src/main/java/de/danoeh/antennapod/storage/database/
└── DBWriter.java                    # ADD: moveQueueItem(), copyQueueItem(), batch methods

event/src/main/java/de/danoeh/antennapod/event/
└── QueueEvent.java                  # ADD: ITEM_MOVED, ITEM_COPIED action types

app/src/main/java/de/danoeh/antennapod/ui/screen/queue/
└── QueueFragment.java               # ADD: Move/Copy context menu items

app/src/main/java/de/danoeh/antennapod/ui/screen/episodes/
└── EpisodeItemListAdapter.java      # ADD: Move/Copy swipe menu items

app/src/main/java/de/danoeh/antennapod/ui/fragment/
└── ItemPagerFragment.java           # ADD: Move/Copy overflow menu items

app/src/main/java/de/danoeh/antennapod/ui/
└── EpisodeMultiSelectActionHandler.java  # ADD: Batch move/copy actions

ui/common/src/main/res/values/
└── strings.xml                      # ADD: Move/copy operation strings

# Documentation Update:
CLAUDE.md                            # ADD: Move/copy operation documentation
```

**Structure Decision**: Android multi-module architecture preserved. Phase 8 adds result classes to `model/` module, DBWriter methods to `storage/database/`, dialog components to `ui/common/`, and context menu integration to `app/` module. Follows existing ViewModel + DBWriter + Fragment + EventBus pattern.

## Complexity Tracking

**Status**: No violations - complexity tracking not required.

This feature:
- Uses existing architectural patterns (DBWriter delegation, EventBus, Fragment dialogs)
- No new abstractions or design patterns introduced
- Standard Android UI implementation (RecyclerView, Material Dialog)
- All complexity justified by platform requirements (Android lifecycle, multi-select, batch operations)
- Code reuse maximized (move/copy delegate to existing queue operations)

---

## Phase 0: Research & Resolution

### Research Questions

| ID | Question | Priority | Answer |
|----|----------|----------|--------|
| R001 | How to implement move operation using existing DBWriter methods? | P0 | RESOLVED: `removeQueueItem(feedItemId, sourceQueueId)` + `addQueueItem(feedItemId, targetQueueId)` in single DB transaction |
| R002 | How to implement copy operation using existing DBWriter methods? | P0 | RESOLVED: Delegate to existing `addQueueItem(feedItemId, targetQueueId)` - duplicate detection already built-in |
| R003 | How does existing addQueueItem handle duplicates? | P0 | NEEDS VERIFICATION: Check DBWriter.addQueueItem implementation for `itemListContains()` logic |
| R004 | Where is episode playback position stored (queue-specific or episode-level)? | P0 | RESOLVED: FeedMedia table stores position/progress (not queue-specific). Position maintained across queues. |
| R005 | How to implement session-based last-destination memory? | P1 | Use ViewModel with MutableLiveData<Long> for lastDestinationQueueId (cleared on ViewModel destroy) |
| R006 | Best practice for Material Design 3 dialogs in AntennaPod? | P1 | NEEDS VERIFICATION: Check existing dialog patterns (e.g., QueueSwitchBottomSheet) for styling and lifecycle |
| R007 | How to implement best-effort batch operations? | P1 | Iterate single-item methods, catch exceptions per item, aggregate results in MoveResult/CopyResult |
| R008 | How to implement undo for move operations? | P2 | Store source queue ID and position, show Snackbar with undo action, on undo call moveQueueItem in reverse |

### Technology & Best Practices

**DBWriter Delegation Pattern** (Code Reuse):
```java
// Move = Remove + Add (atomic via single DB transaction)
public static Future<Void> moveQueueItem(long feedItemId, long sourceQueueId, long targetQueueId) {
    return dbExec.submit(() -> {
        synchronized (DBWriter.class) {
            // Single transaction ensures atomicity
            adapter.open();
            adapter.beginTransaction();
            try {
                // Reuse existing methods
                removeQueueItemInternal(adapter, feedItemId, sourceQueueId);
                addQueueItemInternal(adapter, feedItemId, targetQueueId);
                adapter.setTransactionSuccessful();
                EventBus.getDefault().post(QueueEvent.moved(sourceQueueId, targetQueueId));
            } finally {
                adapter.endTransaction();
                adapter.close();
            }
        }
        return null;
    });
}

// Copy = Add to target (duplicate detection built into addQueueItem)
public static Future<Void> copyQueueItem(long feedItemId, long targetQueueId) {
    // Delegate to existing addQueueItem - it already handles duplicates
    return addQueueItem(feedItemId, targetQueueId);
}
```

**Best-Effort Batch Processing**:
```java
public static Future<MoveResult> moveQueueItems(List<Long> feedItemIds, long sourceQueueId, long targetQueueId) {
    return dbExec.submit(() -> {
        int movedCount = 0;
        int skippedCount = 0;
        List<Long> skippedItemIds = new ArrayList<>();
        Map<Long, String> skipReasons = new HashMap<>();

        for (long feedItemId : feedItemIds) {
            try {
                moveQueueItem(feedItemId, sourceQueueId, targetQueueId).get();
                movedCount++;
            } catch (Exception e) {
                skippedCount++;
                skippedItemIds.add(feedItemId);
                skipReasons.put(feedItemId, e.getMessage());
            }
        }

        return new MoveResult(movedCount, skippedCount, skippedItemIds, skipReasons);
    });
}
```

**Session-Based Last Destination Memory**:
```java
// In QueueSelectionDialog or shared ViewModel
public class QueueSelectionViewModel extends ViewModel {
    private MutableLiveData<Long> lastDestinationQueueId = new MutableLiveData<>(null);

    public void setLastDestination(long queueId) {
        lastDestinationQueueId.setValue(queueId);
    }

    public LiveData<Long> getLastDestination() {
        return lastDestinationQueueId;
    }
}
// Cleared automatically when ViewModel destroyed (app restart)
```

**Material Design 3 Dialog Pattern** (Based on QueueSwitchBottomSheet):
```java
public class QueueSelectionDialog extends DialogFragment {
    private DialogQueueSelectionBinding binding;
    private QueueSelectionAdapter adapter;
    private OnQueueSelectedListener listener;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        binding = DialogQueueSelectionBinding.inflate(getLayoutInflater());

        // Load queues, filter out source/current queue
        List<QueueMetadata> queues = DBReader.getAllQueues();
        // Filter logic here

        adapter = new QueueSelectionAdapter(queues, this::onQueueSelected);
        binding.queueList.setAdapter(adapter);
        binding.queueList.setLayoutManager(new LinearLayoutManager(requireContext()));

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot())
                .create();
    }

    private void onQueueSelected(QueueMetadata queue) {
        if (listener != null) {
            listener.onQueueSelected(queue);
        }
        dismiss();
    }

    public interface OnQueueSelectedListener {
        void onQueueSelected(QueueMetadata queue);
    }
}
```

### Critical Behavioral Requirement

**Queue Removal Semantics** (CRITICAL - affects implementation):
- **User-initiated removal**: When user deletes or moves episode from source queue, it only removes from that queue. Episode remains in any OTHER queues it exists in.
- **Playback completion**: When episode finishes playing, it is removed from ALL queues automatically (existing playback layer behavior).
- **Implication for implementation**: `removeQueueItem()` MUST only affect the specified queue, NOT delete the episode globally. Must verify existing implementation supports this.
- **Test requirement**: Must verify move/copy operations do NOT affect other queues containing the episode.

**Example**:
- Episode E exists in Q1, Q2, Q3
- User moves E from Q1 → Q2: E removed from Q1, remains in Q2 and Q3 ✓
- User deletes E from Q1: E removed from Q1 only, remains in Q2 and Q3 ✓
- User copies E to Q4: E added to Q4, remains in Q1, Q2, Q3 ✓
- Episode E finishes playing: E removed from ALL queues (Q1, Q2, Q3, Q4) ✓

### Unknowns Resolution

**R003 - Duplicate Detection**: NEEDS VERIFICATION - Must read DBWriter.addQueueItem to confirm itemListContains() behavior and that it prevents adding same episode to same queue.

**R006 - Dialog Patterns**: NEEDS VERIFICATION - Must read QueueSwitchBottomSheet to understand existing dialog styling and lifecycle.

**R009 - Queue Removal Behavior**: NEEDS VERIFICATION - Must confirm `removeQueueItem()` only removes from specified queue. Check if global remove exists and when it's called (likely during playback completion).

**All other questions resolved** - proceeding to Phase 1 after verification.

---

## Phase 1: Design Artifacts

### Data Model

See [data-model.md](./data-model.md) for:
- `MoveResult` class (movedCount, skippedCount, skippedItemIds, skipReasons)
- `CopyResult` class (copiedCount, skippedCount, skippedItemIds, skipReasons)
- Relationship to existing QueueMetadata, FeedItem models

### API Contracts

See [contracts/](./contracts/) for:
- `DBWriter.moveQueueItem(feedItemId, sourceQueueId, targetQueueId)` → Future<Void>
- `DBWriter.copyQueueItem(feedItemId, targetQueueId)` → Future<Void>
- `DBWriter.moveQueueItems(List<Long>, sourceQueueId, targetQueueId)` → Future<MoveResult>
- `DBWriter.copyQueueItems(List<Long>, targetQueueId)` → Future<CopyResult>
- QueueEvent action types: ITEM_MOVED, ITEM_COPIED, ITEMS_BATCH_MOVED, ITEMS_BATCH_COPIED

### Quickstart Reference

See [quickstart.md](./quickstart.md) for:
- Setting up test queues for move/copy operations
- Testing single episode move/copy from context menu
- Testing batch operations with multi-select
- Verifying Snackbar messages and undo functionality
- Performance validation (batch operation timing)

---

## Phase 2: Task Generation

**Next Step**: Run `/speckit.tasks` to generate `tasks.md` with dependency-ordered implementation tasks.

**Note**: Phase 2 planning stops here. The `/speckit.plan` command completes after Phase 1 artifacts are generated.

---

## Artifacts Generated

- ✅ `plan.md` - This implementation plan
- ✅ `spec.md` - Feature specification (already created with clarifications)
- ⏳ `research.md` - Research findings (Phase 0 - in progress)
- ⏳ `data-model.md` - Result classes data model (Phase 1 - pending)
- ⏳ `quickstart.md` - Quick reference guide (Phase 1 - pending)
- ⏳ `contracts/` - DBWriter API contracts (Phase 1 - pending)
- ⏳ `tasks.md` - Generated by `/speckit.tasks` (Phase 2)

**Branch**: `008-move-copy-episodes` (created, tracking origin/008-move-copy-episodes)
**Dependencies**: Forked from `007-queue-color-gradient` (Phase 7 complete)
**Ready for**: Research completion (Phase 0), then design artifacts (Phase 1)
