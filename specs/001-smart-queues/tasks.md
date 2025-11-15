# Tasks: Smart Queues

**Input**: Design documents from `/specs/001-smart-queues/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are included for all business logic as required by Constitution Principle II.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Mobile**: Follow existing AntennaPod structure:
  - `model/src/main/java/...` - Domain entities (NO Android dependencies)
  - `storage/database/src/main/java/...` - Database operations
  - `ui/common/src/main/java/...` - Shared UI components
  - `app/src/main/java/...` - App-specific UI
  - `storage/database/src/test/java/...` - Unit tests

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Database schema setup and migration

- [X] T001 Add QueueRuleset table schema to PodDBAdapter.java in storage/database/src/main/java/de/danoeh/antennapod/storage/database/PodDBAdapter.java
- [X] T002 Add RefillRule table schema to PodDBAdapter.java in storage/database/src/main/java/de/danoeh/antennapod/storage/database/PodDBAdapter.java
- [X] T003 Add database migration for QueueRuleset and RefillRule tables in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBUpgrader.java
- [X] T004 [P] Add indexes for QueueRuleset and RefillRule tables in PodDBAdapter.java (idx_queue_ruleset_queue_id, idx_refill_rule_ruleset_id, idx_refill_rule_ruleset_position)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 [P] Create QueueRuleset entity class in model/src/main/java/de/danoeh/antennapod/model/feed/QueueRuleset.java
- [X] T006 [P] Create RefillRule entity class in model/src/main/java/de/danoeh/antennapod/model/feed/RefillRule.java
- [X] T007 [P] Create RefillResult class in storage/database/src/main/java/de/danoeh/antennapod/storage/database/RefillResult.java
- [X] T008 Add QueueRuleset and RefillRule table creation methods to PodDBAdapter.java in storage/database/src/main/java/de/danoeh/antennapod/storage/database/PodDBAdapter.java

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Edit Queue Rules (Priority: P1) 🎯 MVP

**Goal**: Users can customize the rules that determine how their queue is automatically refilled, including adding, removing, changing, and reordering rules.

**Independent Test**: Can be fully tested by entering edit mode for a queue's ruleset, making changes (add/remove/change/reorder), saving, and verifying the rules are persisted. This delivers value by giving users control over their listening experience and is required for testing subsequent user stories.

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T009 [P] [US1] Create unit test for DBWriter.createQueueRuleset() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueRulesetTest.java
- [X] T010 [P] [US1] Create unit test for DBWriter.createRefillRule() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueRulesetTest.java
- [X] T011 [P] [US1] Create unit test for DBWriter.updateRefillRule() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueRulesetTest.java
- [X] T012 [P] [US1] Create unit test for DBWriter.deleteRefillRule() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueRulesetTest.java
- [X] T013 [P] [US1] Create unit test for DBWriter.reorderRefillRules() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueRulesetTest.java
- [X] T014 [P] [US1] Create unit test for DBReader.getQueueRuleset() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBReaderQueueRulesetTest.java
- [X] T015 [P] [US1] Create unit test for DBReader.getRefillRules() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBReaderQueueRulesetTest.java
- [X] T016 [P] [US1] Create unit test for DBReader.hasClearQueueRule() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBReaderQueueRulesetTest.java
- [X] T017 [P] [US1] Create unit test for Clear queue rule validation (only one, must be first) in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueRulesetTest.java

### Implementation for User Story 1

- [X] T018 [US1] Implement DBWriter.createQueueRuleset() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java (post QueueEvent or new RulesetEvent if needed)
- [X] T019 [US1] Implement DBWriter.updateQueueRuleset() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java (post QueueEvent or new RulesetEvent if needed)
- [X] T020 [US1] Implement DBWriter.deleteQueueRuleset() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java (post QueueEvent or new RulesetEvent if needed)
- [X] T021 [US1] Implement DBWriter.createRefillRule() with Clear queue rule validation (FR-030, FR-031) in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [X] T022 [US1] Implement DBWriter.updateRefillRule() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [X] T023 [US1] Implement DBWriter.deleteRefillRule() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [X] T024 [US1] Implement DBWriter.reorderRefillRules() with Clear queue rule position protection (FR-033) in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [X] T025 [US1] Implement DBReader.getQueueRuleset() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [X] T026 [US1] Implement DBReader.hasQueueRuleset() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [X] T027 [US1] Implement DBReader.getRefillRules() ordered by position in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [X] T028 [US1] Implement DBReader.getRefillRule() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [X] T029 [US1] Implement DBReader.hasClearQueueRule() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [X] T030 [US1] Implement DBReader.getClearQueueRule() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [X] T031 [US1] Create QueueRulesetViewModel in ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueRulesetViewModel.java
- [X] T032 [US1] Create QueueRulesetEditFragment layout XML in app/src/main/res/layout/fragment_queue_ruleset_edit.xml
- [X] T033 [US1] Create QueueRulesetEditFragment in app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueRulesetEditFragment.java
- [X] T034 [US1] Implement rule list RecyclerView with drag-to-reorder in QueueRulesetEditFragment.java (prevent Clear queue reorder if at position 1, FR-033)
- [X] T035 [US1] Implement add rule dialog in QueueRulesetEditFragment.java (hide Clear queue option if already exists, FR-032)
- [X] T036 [US1] Implement edit rule dialog in QueueRulesetEditFragment.java
- [X] T037 [US1] Implement remove rule functionality in QueueRulesetEditFragment.java
- [X] T038 [US1] Implement rule reordering with Clear queue protection in QueueRulesetEditFragment.java (FR-033)
- [X] T039 [US1] Implement rule insertion at top (position 0) in QueueRulesetEditFragment.java
- [X] T040 [US1] Implement rule appending at end in QueueRulesetEditFragment.java
- [X] T041 [US1] Add "Edit Rules" button/menu item to QueueFragment in app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueFragment.java
- [X] T042 [US1] Add navigation from QueueFragment to QueueRulesetEditFragment in QueueFragment.java
- [X] T043 [US1] Add string resources for ruleset editing UI in app/src/main/res/values/strings.xml

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Manual Queue Refill with Rules (Priority: P2)

**Goal**: Users can refill their podcast queue automatically using predefined rules instead of manually selecting episodes one by one.

**Independent Test**: Can be fully tested by creating a queue with rules (using US1), pressing the refill button, and verifying the queue is populated according to the rules. This delivers immediate value by automating queue management.

### Tests for User Story 2

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T044 [P] [US2] Create unit test for QueueRefillEngine.processRuleset() with single rule in storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueueRefillEngineTest.java
- [X] T045 [P] [US2] Create unit test for QueueRefillEngine.processRuleset() with multiple rules in storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueueRefillEngineTest.java
- [X] T046 [P] [US2] Create unit test for QueueRefillEngine.processRuleset() with Clear queue rule in storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueueRefillEngineTest.java
- [X] T047 [P] [US2] Create unit test for QueueRefillEngine partial fulfillment (FR-027) in storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueueRefillEngineTest.java
- [X] T048 [P] [US2] Create unit test for QueueRefillEngine deleted feed/tag handling (FR-028) in storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueueRefillEngineTest.java
- [X] T049 [P] [US2] Create unit test for QueueRefillEngine duplicate prevention (FR-013) in storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueueRefillEngineTest.java
- [X] T050 [P] [US2] Create unit test for QueueRefillEngine empty queue scenario (FR-029) in storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueueRefillEngineTest.java
- [ ] T051 [P] [US2] Create unit test for DBReader.getEpisodesForRule() for FEED source in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBReaderQueueRulesetTest.java
- [ ] T052 [P] [US2] Create unit test for DBReader.getEpisodesForRule() for TAG source in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBReaderQueueRulesetTest.java
- [ ] T053 [P] [US2] Create unit test for DBReader.getEpisodesForRule() for INBOX source in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBReaderQueueRulesetTest.java
- [ ] T054 [P] [US2] Create unit test for DBReader.getEpisodesForRule() selection methods (oldest/newest/random) in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBReaderQueueRulesetTest.java
- [ ] T055 [P] [US2] Create unit test for DBReader.getEpisodesForRule() excluding 100% played episodes (FR-012) in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBReaderQueueRulesetTest.java

### Implementation for User Story 2

- [X] T056 [US2] Implement DBReader.getEpisodesForRule() for FEED source in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java (reuse DBReader.getFeedItemList(), FR-024)
- [X] T057 [US2] Implement DBReader.getEpisodesForRule() for TAG source in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java (reuse FeedItemFilter, FR-024)
- [X] T058 [US2] Implement DBReader.getEpisodesForRule() for INBOX source in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java (reuse DBReader.getEpisodes(), FR-024)
- [X] T059 [US2] Implement episode selection method (oldest/newest/random) in DBReader.getEpisodesForRule() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [X] T060 [US2] Implement episode filtering to exclude 100% played episodes (FR-012) in DBReader.getEpisodesForRule() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java
- [X] T061 [US2] Create QueueRefillEngine class in storage/database/src/main/java/de/danoeh/antennapod/storage/database/QueueRefillEngine.java
- [X] T062 [US2] Implement QueueRefillEngine.processRuleset() rule processing loop in storage/database/src/main/java/de/danoeh/antennapod/storage/database/QueueRefillEngine.java
- [X] T063 [US2] Implement QueueRefillEngine Clear queue handling (FR-002, FR-038) in QueueRefillEngine.java
- [X] T064 [US2] Implement QueueRefillEngine episode selection and duplicate prevention (FR-013) in QueueRefillEngine.java
- [X] T065 [US2] Implement QueueRefillEngine partial fulfillment (FR-027) in QueueRefillEngine.java
- [X] T066 [US2] Implement QueueRefillEngine deleted feed/tag handling (FR-028) in QueueRefillEngine.java
- [X] T067 [US2] Implement QueueRefillEngine empty queue scenario (FR-029) in QueueRefillEngine.java
- [X] T068 [US2] Implement DBWriter.refillQueue() wrapper method in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [X] T069 [US2] Add refill button to QueueFragment layout in app/src/main/res/layout/fragment_queue.xml (already exists in queue.xml menu)
- [X] T070 [US2] Implement refill button click handler in QueueFragment.java (calls DBWriter.refillQueue(), starts playback, FR-017)
- [X] T071 [US2] Add string resources for refill button in app/src/main/res/values/strings.xml (already exists)
- [X] T072 [US2] Post QueueEvent.refilled() action after refill completes in DBWriter.refillQueue() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java
- [ ] T073 [US2] Implement edit mode blocking during refill operation in QueueRulesetEditFragment.java (disable edit mode or show message, FR-036)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Automatic Refill When Queue Runs Out (Priority: P3)

**Goal**: Users' queues automatically refill when they finish playing all episodes, so they don't need to manually trigger refill.

**Independent Test**: Can be fully tested by configuring rules (US1), playing through all episodes in a queue, and verifying the queue automatically refills and continues playing. This delivers value by enabling uninterrupted listening sessions.

### Tests for User Story 3

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T074 [P] [US3] Create unit test for automatic refill trigger when queue runs out in playback/service/src/test/java/de/danoeh/antennapod/playback/service/PlaybackServiceQueueRefillTest.java
- [ ] T075 [P] [US3] Create unit test for automatic refill with no rules configured (should not refill) in playback/service/src/test/java/de/danoeh/antennapod/playback/service/PlaybackServiceQueueRefillTest.java
- [ ] T076 [P] [US3] Create unit test for automatic refill playback continuation (FR-017) in playback/service/src/test/java/de/danoeh/antennapod/playback/service/PlaybackServiceQueueRefillTest.java

### Implementation for User Story 3

- [ ] T077 [US3] Add PlaybackHistoryEvent subscriber to PlaybackService in playback/service/src/main/java/de/danoeh/antennapod/playback/service/PlaybackService.java
- [ ] T078 [US3] Implement queue empty check in PlaybackService.onPlaybackHistoryEvent() in playback/service/src/main/java/de/danoeh/antennapod/playback/service/PlaybackService.java
- [ ] T079 [US3] Implement automatic refill trigger when queue runs out (FR-016) in PlaybackService.java
- [ ] T080 [US3] Implement playback continuation after automatic refill (FR-017) in PlaybackService.java
- [ ] T081 [US3] Post QueueEvent.setQueue() or new QueueEvent.REFILLED action after automatic refill completes in PlaybackService.java

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T082 [P] Add integration tests for end-to-end refill workflow in app/src/androidTest/java/de/danoeh/antennapod/ui/screen/queue/QueueRefillIntegrationTest.java
- [ ] T083 [P] Add edge case tests for rule validation in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueRulesetTest.java
- [ ] T084 [P] Add performance tests for refill with large episode counts (<5s for 100 episodes, SC-002) in storage/database/src/test/java/de/danoeh/antennapod/storage/database/QueueRefillEngineTest.java
- [ ] T085 Code cleanup and refactoring across all Smart Queues files
- [ ] T086 [P] Add logging for refill operations in QueueRefillEngine.java
- [ ] T087 [P] Add error handling and user feedback for refill failures in QueueFragment.java
- [ ] T088 [P] Add accessibility labels for ruleset editing UI in QueueRulesetEditFragment.java
- [ ] T089 Run quickstart.md validation (manual testing of example rulesets)
- [ ] T090 [P] Update documentation with Smart Queues usage in README.md or user guide

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Depends on User Story 1 (needs ruleset editing to create rules for testing)
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - Depends on User Stories 1 and 2 (needs ruleset editing and manual refill functionality)

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Models before services
- Services before endpoints/UI
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, User Story 1 can start
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members (after dependencies are met)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Create unit test for DBWriter.createQueueRuleset() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueRulesetTest.java"
Task: "Create unit test for DBWriter.createRefillRule() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBWriterQueueRulesetTest.java"
Task: "Create unit test for DBReader.getQueueRuleset() in storage/database/src/test/java/de/danoeh/antennapod/storage/database/DBReaderQueueRulesetTest.java"

# Launch all DBWriter methods together:
Task: "Implement DBWriter.createQueueRuleset() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java"
Task: "Implement DBWriter.createRefillRule() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java"
Task: "Implement DBWriter.updateRefillRule() in storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBWriter.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (can start immediately)
   - Developer B: User Story 2 (can start after US1 complete)
   - Developer C: User Story 3 (can start after US1 and US2 complete)
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group (FR-026)
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
- All code must pass pre-commit hooks (checkstyle, SpotBugs, Android Lint) before commit (FR-026)
- Database operations must call `DBWriter.tearDownTests()` in test teardown (Constitution Principle II)
