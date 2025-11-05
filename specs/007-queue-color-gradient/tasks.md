---

description: "Task list for Phase 7: Queue Color Gradient - Title Bar Styling"
---

# Tasks: Queue Color Gradient

**Input**: Design documents from `/specs/007-queue-color-gradient/`
**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: Unit tests and UI tests are REQUIRED per AntennaPod constitution (Principle II: Test Coverage for New Features)

**Organization**: Tasks organized by implementation phase - foundational infrastructure, then user story implementation, then polish

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1 = Visual Queue Awareness)
- Include exact file paths in descriptions

## Path Conventions

- **Android multi-module**: `ui/common/`, `app/`, paths relative to repository root
- Repository root: `/home/ira/src/AntennaPod/`

---

## Phase 1: Setup & Branch Initialization

**Purpose**: Prepare development environment and verify prerequisites

- [X] T001 Create feature branch `007-queue-color-gradient` from `develop`
- [X] T002 Verify all 6 target fragments exist at paths specified in research.md
- [X] T003 Verify QueueViewModel exists at ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java
- [X] T004 Verify QueueMetadata.getColor() method exists for reading queue colors

**Checkpoint**: Branch ready, all prerequisite files confirmed

---

## Phase 2: Foundational Infrastructure (Blocking Prerequisites)

**Purpose**: Create core gradient utility and ViewModel enhancements that ALL fragments depend on

**⚠️ CRITICAL**: No fragment integration can begin until this phase is complete

### Unit Tests (Write First - TDD)

- [X] T005 [P] Create test file ui/common/src/test/java/de/danoeh/antennapod/ui/common/QueueColorGradientTest.java
- [X] T006 [P] Write test: `testCreateGradientDrawable_ValidColor()` - verify gradient creation with various colors
- [X] T007 [P] Write test: `testComputeTextColor_LightBackground()` - verify black text for luminance > 0.5
- [X] T008 [P] Write test: `testComputeTextColor_DarkBackground()` - verify white text for luminance ≤ 0.5
- [X] T009 [P] Write test: `testApplyScrim_LightColor()` - verify 20% darkening when luminance > 0.5
- [X] T010 [P] Write test: `testApplyScrim_DarkColor()` - verify no change when luminance ≤ 0.5
- [X] T011 [P] Write test: `testContrastRatio_MeetsWCAG_AA()` - verify 4.5:1 minimum contrast

### Infrastructure Implementation

- [X] T012 Create ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueColorGradient.java utility class
- [X] T013 Implement `createGradientDrawable(int queueColor, int endColor)` method with TOP_BOTTOM orientation
- [X] T014 Implement `computeTextColor(int backgroundColor)` using ColorUtils.calculateLuminance() with 0.5 threshold
- [X] T015 Implement `applyScrim(int color)` method multiplying RGB by 0.8 for light colors (luminance > 0.5)
- [X] T016 Add JavaDoc to all public methods in QueueColorGradient with @param, @return annotations
- [X] T017 Run unit tests: `./gradlew :ui:common:testDebugUnitTest --tests "*QueueColorGradient*"` - verify all tests pass

### ViewModel Enhancement

- [X] T018 Add `Map<Integer, GradientDrawable> gradientCache` field to ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java
- [X] T019 Add `MutableLiveData<Integer> currentQueueColor` field to QueueViewModel
- [X] T020 Implement `getCurrentQueueColor()` method returning LiveData<Integer>
- [X] T021 Implement `getGradientForColor(int color)` method with cache check, creation, and storage
- [X] T022 Implement `clearGradientCache()` method
- [X] T023 Add EventBus subscription in QueueViewModel constructor: `EventBus.getDefault().register(this)`
- [X] T024 Implement `onQueueEvent(QueueEvent event)` method to emit queue color on SWITCHED/QUEUE_COLOR_CHANGED events
- [X] T025 Implement `onThemeChanged(ThemeChangedEvent event)` method to clear cache and re-emit color (or use Configuration.uiMode if ThemeChangedEvent doesn't exist)
- [X] T026 Override `onCleared()` to unregister from EventBus
- [X] T027 Add `loadCurrentQueueColor()` initialization in constructor to emit initial queue color
- [X] T028 Add JavaDoc to all new public methods in QueueViewModel

**Checkpoint**: Foundation ready - QueueColorGradient utility tested and working, QueueViewModel caching functional

---

## Phase 3: User Story 1 - Visual Queue Awareness (Priority: P1) 🎯 MVP

**Goal**: Display active queue's color as gradient in title bar of all 6 major screens with automatic text color adaptation

**Independent Test**: Switch between queues with different colors (blue, orange, light yellow, dark purple) and verify gradient appears on all 6 screens with readable text

### Fragment Integration (Can work in parallel after T028 complete)

- [X] T029 [P] [US1] Apply gradient to AudioPlayerFragment title bar in app/src/main/java/de/danoeh/antennapod/ui/screen/playback/audio/AudioPlayerFragment.java
- [X] T030 [P] [US1] Add LiveData observation for getCurrentQueueColor() in AudioPlayerFragment.onViewCreated()
- [X] T031 [P] [US1] Set gradient background and compute text color on color change in AudioPlayerFragment
- [X] T032 [P] [US1] Apply gradient to QueueFragment title bar in app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueFragment.java
- [X] T033 [P] [US1] Add LiveData observation for getCurrentQueueColor() in QueueFragment.onViewCreated()
- [X] T034 [P] [US1] Set gradient background and compute text color on color change in QueueFragment
- [X] T035 [P] [US1] Apply gradient to AllEpisodesFragment title bar in app/src/main/java/de/danoeh/antennapod/ui/screen/AllEpisodesFragment.java
- [X] T036 [P] [US1] Add LiveData observation for getCurrentQueueColor() in AllEpisodesFragment.onViewCreated()
- [X] T037 [P] [US1] Set gradient background and compute text color on color change in AllEpisodesFragment
- [X] T038 [P] [US1] Apply gradient to HomeFragment title bar in app/src/main/java/de/danoeh/antennapod/ui/screen/home/HomeFragment.java
- [X] T039 [P] [US1] Add LiveData observation for getCurrentQueueColor() in HomeFragment.onViewCreated()
- [X] T040 [P] [US1] Set gradient background and compute text color on color change in HomeFragment
- [X] T041 [P] [US1] Apply gradient to SubscriptionFragment title bar in app/src/main/java/de/danoeh/antennapod/ui/screen/subscriptions/SubscriptionFragment.java
- [X] T042 [P] [US1] Add LiveData observation for getCurrentQueueColor() in SubscriptionFragment.onViewCreated()
- [X] T043 [P] [US1] Set gradient background and compute text color on color change in SubscriptionFragment
- [X] T044 [P] [US1] Apply gradient to QueueManagementFragment title bar in app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueManagementFragment.java
- [X] T045 [P] [US1] Add LiveData observation for getCurrentQueueColor() in QueueManagementFragment.onViewCreated()
- [X] T046 [P] [US1] Set gradient background and compute text color on color change in QueueManagementFragment

### Verification

- [X] T047 [US1] Build app: `./gradlew assemblePlayDebug`
- [ ] T048 [US1] Manual test: Create 4 test queues (blue, orange, light yellow, dark purple) per quickstart.md
- [ ] T049 [US1] Manual test: Verify gradient visible on all 6 screens
- [ ] T050 [US1] Manual test: Switch queues and verify immediate gradient update
- [ ] T051 [US1] Manual test: Verify text readable (black on light, white on dark)

**Checkpoint**: User Story 1 complete - all 6 screens display queue color gradient with readable text

---

## Phase 4: Polish & Quality Assurance

**Purpose**: Testing, quality checks, documentation, and edge case handling

### UI Tests (Espresso)

- [ ] T052 [P] Create Espresso test for gradient visibility on AudioPlayerFragment
- [ ] T053 [P] Create Espresso test for gradient update on queue switch
- [ ] T054 [P] Create Espresso test for text color adaptation (light vs dark gradients)
- [ ] T055 Run UI tests: `./gradlew connectedPlayDebugAndroidTest` - verify all tests pass

### Theme & Edge Case Testing

- [ ] T056 [P] Manual test: Switch from Light to Dark theme - verify gradients update
- [ ] T057 [P] Manual test: Test very light color (white/pale yellow) in Light theme - verify scrim applied
- [ ] T058 [P] Manual test: Test very dark color (black/dark purple) in Dark theme - verify white text
- [ ] T059 [P] Manual test: Rapid queue switching - verify no visual glitches or stale gradients
- [ ] T060 [P] Manual test: Orientation change (portrait/landscape) - verify gradient reapplied
- [ ] T061 [P] Manual test: Navigate between all 6 screens - verify gradient persists

### Code Quality Gates

- [ ] T062 Run checkstyle: `./gradlew checkstyle` - fix any violations
- [ ] T063 Run SpotBugs: `./gradlew spotbugsPlayDebug spotbugsDebug` - fix any bugs
- [ ] T064 Run Android Lint: `./gradlew :app:lintPlayDebug` - fix any errors
- [ ] T065 Re-run all quality checks to verify zero violations

### Documentation

- [ ] T066 [P] Add "Queue Color Gradient" section to CLAUDE.md documenting feature behavior
- [ ] T067 [P] Add code examples to CLAUDE.md showing how fragments observe queue color
- [ ] T068 [P] Document performance characteristics (caching strategy) in CLAUDE.md
- [ ] T069 [P] Add troubleshooting section to CLAUDE.md for gradient not showing / text unreadable

### Performance Validation

- [ ] T070 Add cache hit/miss logging to QueueViewModel.getGradientForColor() temporarily
- [ ] T071 Manual test: Switch between queues 10 times - verify >95% cache hit rate (9+ hits)
- [ ] T072 Manual test: Measure perceived queue switch latency - verify <100ms (feels instant)
- [ ] T073 Remove temporary logging from T070

### Final Verification

- [ ] T074 Run all unit tests: `./gradlew testPlayDebugUnitTest testDebugUnitTest`
- [ ] T075 Run all UI tests: `./gradlew connectedPlayDebugAndroidTest`
- [ ] T076 Run full quality check suite: `./gradlew checkstyle spotbugsPlayDebug spotbugsDebug :app:lintPlayDebug`
- [ ] T077 Verify all 8 success criteria from spec.md (gradient visible, colors match, updates immediately, text readable, no glitches, theme support, no performance impact, builds clean)
- [ ] T078 Create completion summary in specs/007-queue-color-gradient/completion-summary.md with screenshots

**Checkpoint**: All quality gates passed, feature ready for PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies - can start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 completion - BLOCKS all user story work
- **Phase 3 (User Story 1)**: Depends on Phase 2 completion (T028 done) - foundation must be ready
- **Phase 4 (Polish)**: Depends on Phase 3 completion (all fragments integrated)

### Task Dependencies Within Phases

**Phase 1**: Sequential (T001 → T002 → T003 → T004)

**Phase 2**:
- **Unit Tests (T005-T011)**: All [P] can run in parallel (different test methods)
- **Implementation (T012-T017)**: Sequential, but T013-T015 can be done in any order after T012
- **ViewModel (T018-T028)**: Sequential dependencies (need fields before methods)
- T017 (run tests) must wait for T005-T016 complete

**Phase 3**:
- All fragment tasks (T029-T046) can run in parallel [P] after T028 complete (different files)
- Each fragment has 3-task pattern: Apply gradient → Add observation → Set background
- Verification tasks (T047-T051) sequential after all fragments complete

**Phase 4**:
- UI tests (T052-T055) can run in parallel [P] with manual tests (T056-T061)
- Quality gates (T062-T065) sequential
- Documentation (T066-T069) can run in parallel [P]
- Performance (T070-T073) sequential
- Final verification (T074-T078) sequential at end

### Parallel Opportunities

**Maximum parallelization:**

1. **Phase 2 Unit Tests**: All 7 tests (T005-T011) in parallel
2. **Phase 3 Fragment Integration**: All 6 fragments (T029-T046) in parallel after foundation ready
   - AudioPlayerFragment (T029-T031)
   - QueueFragment (T032-T034)
   - AllEpisodesFragment (T035-T037)
   - HomeFragment (T038-T040)
   - SubscriptionFragment (T041-T043)
   - QueueManagementFragment (T044-T046)
3. **Phase 4 Testing**: UI tests (T052-T055) + Manual tests (T056-T061) + Documentation (T066-T069) all in parallel

---

## Parallel Example: Phase 3 Fragment Integration

```bash
# After Phase 2 foundation complete (T028 done), launch all 6 fragments in parallel:

Task: "Apply gradient to AudioPlayerFragment in AudioPlayerFragment.java"
Task: "Apply gradient to QueueFragment in QueueFragment.java"
Task: "Apply gradient to AllEpisodesFragment in AllEpisodesFragment.java"
Task: "Apply gradient to HomeFragment in HomeFragment.java"
Task: "Apply gradient to SubscriptionFragment in SubscriptionFragment.java"
Task: "Apply gradient to QueueManagementFragment in QueueManagementFragment.java"

# Each fragment follows same 3-step pattern:
1. Apply gradient to title bar (get ViewBinding reference)
2. Add LiveData observation in onViewCreated()
3. Set gradient background + text color on color change
```

---

## Implementation Strategy

### Sequential Approach (Single Developer)

1. **Phase 1**: Setup branch and verify prerequisites (15 min)
2. **Phase 2**: Build foundation - TDD unit tests, utility class, ViewModel caching (2-3 hours)
3. **Phase 3**: Integrate 6 fragments one by one (4-6 hours, ~45 min each)
4. **Phase 4**: Test, polish, quality checks, documentation (2-3 hours)

**Estimated total**: 8-11 hours

### Parallel Approach (Efficient Single Developer with Agents)

1. **Phase 1**: Setup (15 min)
2. **Phase 2**: Foundation sequential (2-3 hours)
3. **Phase 3**: Launch 6 fragment agents in parallel (1-2 hours wall time, 6 hours agent time)
4. **Phase 4**: Testing/polish sequential (2-3 hours)

**Estimated total**: 5-8 hours wall time

### MVP-First Approach (Minimal Viable)

1. Phase 1 + Phase 2 (foundation)
2. Phase 3: Implement ONLY AudioPlayerFragment + QueueFragment (2 critical screens)
3. Verify gradient works, get user feedback
4. Add remaining 4 fragments in follow-up
5. Phase 4 polish

**MVP time**: 4-5 hours

---

## Success Criteria

- ✅ **SC-001**: Gradient visible on all 6 target screens (T049)
- ✅ **SC-002**: Colors match queue management display exactly (T050)
- ✅ **SC-003**: Gradient updates immediately when switching queues (T050)
- ✅ **SC-004**: Text remains readable on all gradient variations - WCAG AA 4.5:1 contrast (T051, T054)
- ✅ **SC-005**: No visual glitches when navigating between screens (T061)
- ✅ **SC-006**: Works correctly in light and dark themes (T056-T058)
- ✅ **SC-007**: No performance degradation - cache hit rate >95%, switch <100ms (T071-T072)
- ✅ **SC-008**: Code builds with no warnings - all quality gates pass (T065, T076)

---

## Notes

- **[P]** tasks = different files/methods, no dependencies, safe to parallelize
- **[US1]** label = User Story 1 (Visual Queue Awareness) - single story for this feature
- Tests written FIRST per TDD (T005-T011 before T012-T017)
- All fragments follow identical 3-step integration pattern
- Commit after each phase completion or logical group
- **Mini player**: Not included (research.md noted needs verification) - can add in follow-up
- **Total**: 78 tasks across 4 phases
- **Parallelizable**: 38 tasks marked [P] (49% parallel efficiency)
- **Test coverage**: 7 unit tests + 3 UI tests = 10 automated tests + extensive manual testing
