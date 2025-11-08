# Implementation Plan: Smart Queues

**Branch**: `001-smart-queues` | **Date**: 2025-11-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-smart-queues/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Smart Queues enables automatic refilling of podcast queues based on user-defined rules. Users can configure rules that select episodes from feeds, tags, or inbox using oldest/newest/random selection methods. The system automatically refills queues when they run out of episodes, eliminating manual queue management.

**Technical Approach**: Reuse existing queue management (DBWriter/DBReader), episode filtering (FeedItemFilter), and EventBus patterns. Add new database tables for rulesets and rules. Implement rule engine that processes rules in order, selecting episodes and adding them to queues via existing queue operations.

## Technical Context

**Language/Version**: Java 17 (source/target compatibility), Android API 21-35
**Primary Dependencies**: Android SDK, SQLite (via PodDBAdapter), EventBus (GreenRobot), ExoPlayer
**Storage**: SQLite database (new tables: QueueRuleset, RefillRule) - no modifications to existing tables
**Testing**: Robolectric for unit tests, Espresso for integration tests, JUnit 4
**Target Platform**: Android (API 21-35, 10+ years of Android versions)
**Project Type**: Mobile (Android app with 37-module architecture)
**Performance Goals**: Queue refill operations complete in under 5 seconds for queues with up to 100 episodes (SC-002)
**Constraints**:
- Must reuse existing code and data structures (FR-024)
- Only add new rules tables to database schema (FR-025)
- No modifications to existing tables (Feeds, FeedItems, FeedMedia, Queue, QueueMetadata, etc.)
- Commit at end of every stable stage with tests passing (FR-026)
**Scale/Scope**:
- Multiple queues per user (existing multi-queue support)
- Zero or more rules per queue
- Episode selection from feeds, tags, or inbox
- Support for partial fulfillment, deleted feed/tag handling, empty queue scenarios

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verify compliance with AntennaPod Development Constitution (`.specify/memory/constitution.md`):

- **Code Quality First (I)**: ✅ Plan includes checkstyle, SpotBugs, and Android Lint checks - all code must pass pre-commit hooks (FR-026)
- **Test Coverage (II)**: ✅ Plan includes unit tests for rule engine, episode selection logic, and ruleset management. Database operations will call `DBWriter.tearDownTests()` in test teardown
- **Modular Architecture (III)**: ✅ Plan respects 37-module dependency hierarchy - new code in `storage/database/` (rules tables), `model/` (rule entities), and UI modules (rule editing UI)
- **Event-Driven Communication (IV)**: ✅ Plan uses EventBus for cross-component communication - refill operations will post QueueEvent, ruleset changes will post appropriate events
- **Database Integrity (V)**: ✅ Plan uses DBWriter/DBReader pattern, no raw SQL writes - all database operations go through DBWriter/DBReader static methods

**Violations**: None - all constitution principles are satisfied.

## Project Structure

### Documentation (this feature)

```text
specs/001-smart-queues/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
storage/database/src/main/java/de/danoeh/antennapod/storage/database/
├── DBWriter.java        # Add ruleset/rule CRUD operations
├── DBReader.java        # Add ruleset/rule read operations
├── PodDBAdapter.java    # Add new tables: QueueRuleset, RefillRule
└── DBUpgrader.java      # Add migration for new tables

model/src/main/java/de/danoeh/antennapod/model/feed/
├── QueueRuleset.java    # New entity: ruleset per queue
└── RefillRule.java      # New entity: individual rule

storage/database/src/main/java/de/danoeh/antennapod/storage/database/
└── QueueRefillEngine.java  # New: rule processing engine

ui/common/src/main/java/de/danoeh/antennapod/ui/common/
└── QueueRulesetViewModel.java  # New: ViewModel for ruleset editing

app/src/main/java/de/danoeh/antennapod/ui/screen/queue/
├── QueueFragment.java    # Add refill button, ruleset edit access
└── QueueRulesetEditFragment.java  # New: ruleset editing UI

storage/database/src/test/java/de/danoeh/antennapod/storage/database/
├── DBWriterQueueRulesetTest.java  # New: ruleset CRUD tests
├── DBReaderQueueRulesetTest.java  # New: ruleset read tests
└── QueueRefillEngineTest.java     # New: rule engine tests
```

**Structure Decision**: Follow existing AntennaPod architecture:
- **Model layer** (`model/`): New entities (QueueRuleset, RefillRule) - NO Android dependencies
- **Storage layer** (`storage/database/`): New tables, DBWriter/DBReader methods, rule engine
- **UI layer** (`app/`, `ui/common/`): New fragments and ViewModels for ruleset editing
- **Tests**: Unit tests in `storage/database/src/test/` using Robolectric

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations - all constitution principles are satisfied.
