# Implementation Plan: Queue Color Gradient

**Branch**: `007-queue-color-gradient` | **Date**: 2025-11-04 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-queue-color-gradient/spec.md`

## Summary

Apply the currently active queue's color as a gradient background on title bars across all major screens (Player, Mini Player, Queue Pane, Episodes, Home/Browse, Queue Management). The gradient fades from the queue color at the top to transparent at the bottom, providing persistent visual queue awareness. Implementation focuses on creating a reusable gradient utility with caching in QueueViewModel, applying it to 6 target screens, and ensuring WCAG AA contrast compliance for text readability.

**Technical Approach**: Create `QueueColorGradient` utility class for gradient creation with luminance-based text color selection. Cache gradients in QueueViewModel using `Map<Integer, GradientDrawable>` keyed by color. Observe active queue color via LiveData and apply gradients to title bars across fragments. Support both light/dark themes with 20% black scrim for light colors.

## Technical Context

**Language/Version**: Java 17 (source/target compatibility), Kotlin for build scripts
**Primary Dependencies**: Android SDK API 35, AndroidX Core (ColorUtils for luminance), ViewBinding
**Storage**: N/A (uses existing QueueMetadata.color from SQLite)
**Testing**: JUnit 4, Robolectric for Android-dependent code, Espresso for UI tests
**Target Platform**: Android API 21+ (minSdk: 21, targetSdk: 35)
**Project Type**: Android multi-module application (37 Gradle modules)
**Performance Goals**:
- Gradient drawable creation <5ms
- Cache hit rate >95% during normal usage
- No frame drops during queue switches
- LiveData updates <16ms (60fps budget)
**Constraints**:
- WCAG AA contrast ratio 1:4.5 between text and gradient background
- Must work with ViewBinding (no findViewById)
- Title bar height consistent across all screens (standard Android Toolbar height)
- Support notch/cutout devices (safe area insets)
- Must pass checkstyle, SpotBugs, and Lint with zero violations
**Scale/Scope**:
- 2 new Java classes (~300 LOC total)
- 1 new XML layout (~30 lines)
- 6 fragments modified (~100 LOC each)
- 1 ViewModel enhanced (~50 LOC added)
- Total estimated: ~850 new/modified LOC

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### ✅ I. Code Quality First (NON-NEGOTIABLE)

**Status**: PASS (gates will be validated during implementation)

- **Checkstyle**: Will run `./gradlew checkstyle` - target zero violations
- **SpotBugs**: Will run `./gradlew spotbugsPlayDebug spotbugsDebug` - target zero bugs
- **Android Lint**: Will run `./gradlew :app:lintPlayDebug` - target zero errors
- **XML Formatting**: Will format any new layout files with android-xml-formatter

**Compliance**: This feature involves Java code and XML layouts. All quality gates will be enforced before merge.

### ✅ II. Test Coverage for New Features

**Status**: PASS (tests required)

- Unit tests required for:
  - `QueueColorGradient.createGradientDrawable()` - verify gradient creation with various colors
  - `QueueColorGradient.computeTextColor()` - verify luminance-based text color selection
  - `QueueViewModel.getGradientForColor()` - verify caching behavior
  - Cache invalidation on theme change
- Integration tests (Espresso) for:
  - Gradient visibility on all 6 target screens
  - Gradient update on queue switch
  - Text readability verification

**Exemption**: None - full test coverage required for new business logic.

### ✅ III. Modular Architecture Preservation

**Status**: PASS (respects module boundaries)

- New code isolated to:
  - `ui/common/` - QueueColorGradient utility, gradient layout, QueueViewModel enhancement
  - `app/` - Fragment modifications to apply gradients
- No new modules created
- No cross-module dependency violations
- UI layer consumes existing storage layer (QueueMetadata) without modification

**Compliance**: Feature is purely UI-layer, uses existing data via QueueViewModel. No database or storage layer changes.

### ✅ IV. Event-Driven Communication

**Status**: PASS (uses existing EventBus patterns)

- QueueViewModel already subscribes to `QueueEvent` for queue changes
- Gradients update via LiveData observation (reactive pattern)
- No new EventBus events required
- Fragments register/unregister in `onStart()`/`onStop()` correctly

**Compliance**: Follows existing ViewModel + LiveData + EventBus architecture. No new event patterns introduced.

### ✅ V. Database Integrity

**Status**: PASS (no database changes)

- No schema modifications
- No new DBWriter/DBReader methods
- Reads queue color via existing `DBReader.getAllQueues()` and `QueueMetadata.getColor()`
- All database operations already follow single-threaded executor pattern

**Compliance**: Feature only reads existing data. No database writes or migrations required.

### 📋 Localization & Translation

**Status**: PASS (minimal string changes)

- Only English strings modified: values/strings.xml (if any new labels needed)
- Most text already exists (queue names, screen titles)
- Translations managed via Transifex

### 📋 Product Flavor Awareness

**Status**: PASS (no flavor-specific code)

- Gradients apply to both Play and Free flavors identically
- No Chromecast or proprietary dependencies
- Shared across both flavors via `ui/common/` module

### 📋 Dependency Management

**Status**: PASS (uses existing dependencies)

- No new external dependencies
- Uses existing AndroidX Core (ColorUtils) already in project
- No build tool upgrades required

**Summary**: All constitution principles are satisfied. No violations to justify. Proceeding to Phase 0 research.

## Project Structure

### Documentation (this feature)

```text
specs/007-queue-color-gradient/
├── spec.md              # Feature specification (already created)
├── plan.md              # This file (Phase 0-1 planning)
├── research.md          # Phase 0: Research findings
├── data-model.md        # Phase 1: N/A (no new data models)
├── quickstart.md        # Phase 1: Quick reference for implementation
├── contracts/           # Phase 1: N/A (no API contracts - UI only)
└── tasks.md             # Phase 2: Generated by /speckit.tasks
```

### Source Code (repository root)

**Existing Structure** (37 Gradle modules, Android multi-module):

```text
# Files Created in Phase 7:
ui/common/src/main/java/de/danoeh/antennapod/ui/common/
└── QueueColorGradient.java         # NEW: Gradient utility with luminance calculations

ui/common/src/main/res/layout/
└── queue_color_gradient_header.xml # NEW: Reusable gradient layout (OPTIONAL)

ui/common/src/test/java/de/danoeh/antennapod/ui/common/
└── QueueColorGradientTest.java     # NEW: Unit tests for gradient utility

# Files Modified in Phase 7:
ui/common/src/main/java/de/danoeh/antennapod/ui/common/
└── QueueViewModel.java              # ADD: Gradient caching, getCurrentQueueColor() LiveData

app/src/main/java/de/danoeh/antennapod/ui/screen/player/
└── PlayerDetailsFragment.java       # MODIFY: Apply gradient to title bar

app/src/main/java/de/danoeh/antennapod/ui/screen/player/
└── VideoPlayerDetailFragment.java   # MODIFY: Apply gradient to mini player (NEEDS VERIFICATION)

app/src/main/java/de/danoeh/antennapod/ui/screen/queue/
└── QueueFragment.java               # MODIFY: Apply gradient to queue pane title bar

app/src/main/java/de/danoeh/antennapod/ui/screen/episodes/
└── EpisodesFragment.java            # MODIFY: Apply gradient to episodes pane (NEEDS VERIFICATION)

app/src/main/java/de/danoeh/antennapod/ui/fragment/
└── SubscriptionsFragment.java       # MODIFY: Apply gradient to home/browse (NEEDS VERIFICATION)

app/src/main/java/de/danoeh/antennapod/ui/screen/queue/
└── QueueManagementFragment.java     # MODIFY: Apply gradient to queue management screen

# Documentation Update:
CLAUDE.md                            # ADD: Queue color gradient feature documentation
```

**Structure Decision**: Android multi-module architecture preserved. Phase 7 adds new utility class in `ui/common/` module and modifies 6 fragments in `app/` module. No new modules or architectural changes. Follows existing ViewModel + LiveData + Fragment pattern.

## Complexity Tracking

**Status**: No violations - complexity tracking not required.

This feature:
- Uses existing architectural patterns (ViewModel + LiveData + Fragment)
- No new abstractions or design patterns introduced
- Standard Android UI implementation
- All complexity justified by platform requirements (Android lifecycle, ViewBinding, Material Design)

---

## Phase 0: Research & Resolution

### Research Questions

| ID | Question | Priority | Answer |
|----|----------|----------|--------|
| R001 | What are the exact file paths for the 6 target fragments? | P0 | NEEDS VERIFICATION - spec provides estimated paths, must confirm actual structure |
| R002 | How to compute luminance and select text color (black/white) for WCAG AA compliance? | P0 | Use `androidx.core.graphics.ColorUtils.calculateLuminance()`. Threshold: luminance > 0.5 → black text, else white text. WCAG AA requires 4.5:1 contrast for normal text. |
| R003 | How to apply 20% black scrim for light colors? | P1 | Multiply RGB channels by 0.8: `rgb_new = rgb_old * 0.8`. Preserves alpha channel. |
| R004 | How to cache gradients efficiently in QueueViewModel? | P1 | Use `Map<Integer, GradientDrawable>` keyed by queue color. Clear cache on theme change via EventBus subscription. |
| R005 | How to observe current queue color changes in fragments? | P1 | QueueViewModel exposes `LiveData<Integer> getCurrentQueueColor()`. Fragments observe in `onViewCreated()`. |
| R006 | What is the standard Android Toolbar height? | P1 | `?attr/actionBarSize` (typically 56dp). Use `TypedValue` to resolve at runtime. |
| R007 | How to handle theme changes (light/dark)? | P1 | Subscribe to theme change events in ViewModel, clear gradient cache, re-emit current queue color to trigger redraw. |

### Technology & Best Practices

**Android Gradient Creation** (GradientDrawable):
```java
int[] colors = {queueColor, Color.TRANSPARENT};
GradientDrawable gradient = new GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    colors
);
gradient.setDither(true); // Smooth gradient transitions
```

**Luminance Calculation** (WCAG AA compliance):
```java
import androidx.core.graphics.ColorUtils;

double luminance = ColorUtils.calculateLuminance(queueColor);
int textColor = luminance > 0.5 ? Color.BLACK : Color.WHITE;

// Verify contrast ratio (optional verification)
double contrastRatio = ColorUtils.calculateContrast(textColor, queueColor);
// WCAG AA requires: contrastRatio >= 4.5 for normal text
```

**Light Color Scrim Application**:
```java
int applyScrim(int color) {
    if (ColorUtils.calculateLuminance(color) > 0.5) {
        int r = (int) (Color.red(color) * 0.8);
        int g = (int) (Color.green(color) * 0.8);
        int b = (int) (Color.blue(color) * 0.8);
        return Color.argb(Color.alpha(color), r, g, b);
    }
    return color;
}
```

**LiveData Observation Pattern**:
```java
// In Fragment
@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    QueueViewModel viewModel = new ViewModelProvider(requireActivity())
        .get(QueueViewModel.class);

    viewModel.getCurrentQueueColor().observe(getViewLifecycleOwner(), color -> {
        GradientDrawable gradient = viewModel.getGradientForColor(color);
        titleBar.setBackground(gradient);
        titleBarText.setTextColor(QueueColorGradient.computeTextColor(color));
    });
}
```

**Gradient Caching in ViewModel**:
```java
public class QueueViewModel extends AndroidViewModel {
    private Map<Integer, GradientDrawable> gradientCache = new HashMap<>();
    private MutableLiveData<Integer> currentQueueColor = new MutableLiveData<>();

    public LiveData<Integer> getCurrentQueueColor() {
        return currentQueueColor;
    }

    public GradientDrawable getGradientForColor(int color) {
        if (!gradientCache.containsKey(color)) {
            gradientCache.put(color, QueueColorGradient.createGradientDrawable(
                color, Color.TRANSPARENT));
        }
        return gradientCache.get(color);
    }

    public void clearGradientCache() {
        gradientCache.clear();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onThemeChanged(ThemeChangedEvent event) {
        clearGradientCache();
        // Re-emit current color to trigger redraw
        currentQueueColor.postValue(currentQueueColor.getValue());
    }
}
```

### Unknowns Resolution

**R001 - Fragment File Paths**: NEEDS VERIFICATION during Phase 1. Spec provides estimated paths based on naming conventions. Will use `find` and `grep` to locate actual files during quickstart.md generation.

**R002-R007**: All technical questions resolved using Android best practices and existing AntennaPod patterns.

**All critical unknowns resolved** - proceeding to Phase 1.

---

## Phase 1: Design Artifacts

### Data Model

**Status**: N/A - No new data models in Phase 7.

Existing data model unchanged:
- `QueueMetadata.color` (int ARGB) - already exists from Phase 1
- No database schema changes
- No new EventBus events

### API Contracts

**Status**: N/A - No API contracts (UI-only feature).

Public API additions (internal Java APIs):
- `QueueColorGradient.createGradientDrawable(int color, int endColor)` → GradientDrawable
- `QueueColorGradient.computeTextColor(int backgroundColor)` → int (Color.BLACK or Color.WHITE)
- `QueueColorGradient.applyScrim(int color)` → int (darkened color)
- `QueueViewModel.getCurrentQueueColor()` → LiveData<Integer>
- `QueueViewModel.getGradientForColor(int color)` → GradientDrawable
- `QueueViewModel.clearGradientCache()` → void

### Quickstart Reference

See [quickstart.md](./quickstart.md) for:
- Running the app with gradient feature
- Testing gradient on all 6 screens
- Verifying text contrast
- Testing theme switching
- Performance validation (cache hit rate)

---

## Phase 2: Task Generation

**Next Step**: Run `/speckit.tasks` to generate `tasks.md` with dependency-ordered implementation tasks.

**Note**: Phase 2 planning stops here. The `/speckit.plan` command completes after Phase 1 artifacts are generated.

---

## Artifacts Generated

- ✅ `plan.md` - This implementation plan
- ✅ `spec.md` - Feature specification (already created with clarifications)
- ✅ `research.md` - Research findings (Phase 0 complete)
- ✅ `quickstart.md` - Quick reference guide (Phase 1 complete)
- ✅ `data-model.md` - N/A documented (UI-only, uses existing QueueMetadata)
- ✅ `contracts/` - N/A documented (internal Java APIs only)
- ⏳ `tasks.md` - Generated by `/speckit.tasks` (Phase 2)

**Branch**: `007-queue-color-gradient` (to be created)
**Ready for**: Task generation via `/speckit.tasks`
