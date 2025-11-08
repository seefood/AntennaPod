# Research: Smart Queues

**Feature**: Smart Queues
**Date**: 2025-11-08
**Phase**: 0 - Outline & Research

## Research Tasks

### 1. Database Schema Design for Rulesets and Rules

**Task**: Design database schema for storing queue rulesets and rules

**Decision**: Use two new tables:
- `QueueRuleset`: One row per queue, stores ruleset metadata
- `RefillRule`: One row per rule, linked to ruleset via foreign key

**Rationale**:
- Follows existing AntennaPod pattern of separate tables for related entities
- Enables efficient querying of rules for a specific queue
- Supports ordered rules via `position` column
- Foreign key constraints ensure data integrity

**Alternatives Considered**:
- Single table with JSON blob for rules → Rejected: Harder to query, violates normalization
- Embedded in QueueMetadata table → Rejected: Violates FR-025 (no modifications to existing tables)

### 2. Rule Engine Architecture

**Task**: Design rule processing engine that selects episodes based on rules

**Decision**: Create `QueueRefillEngine` class that:
- Processes rules in order (from ruleset)
- For each rule, queries episodes using existing `FeedItemFilter` and `DBReader` methods
- Applies selection method (oldest/newest/random) using existing episode sorting
- Tracks selected episodes to prevent duplicates
- Uses existing `DBWriter.addQueueItem()` to add episodes to queue

**Rationale**:
- Reuses existing episode filtering mechanisms (FR-024)
- Leverages existing queue operations (FR-024)
- Maintains separation of concerns (rule logic separate from queue operations)
- Testable in isolation

**Alternatives Considered**:
- Embed rule logic in DBWriter → Rejected: Violates single responsibility, harder to test
- Create separate service → Rejected: Unnecessary complexity for synchronous operation

### 3. Episode Selection from Feeds, Tags, and Inbox

**Task**: Determine how to query episodes from different sources (feed, tag, inbox)

**Decision**: Reuse existing mechanisms:
- **Feed**: Use `DBReader.getFeedItemList()` with feed ID filter
- **Tag**: Use existing tag filtering via `FeedItemFilter` (already supports tag filtering)
- **Inbox**: Use `DBReader.getInboxItemList()` (existing method for inbox episodes)

**Rationale**:
- All three sources already have existing query methods
- Reuses existing code (FR-024)
- Maintains consistency with existing episode selection patterns

**Alternatives Considered**:
- Create new query methods → Rejected: Violates FR-024 (code reuse requirement)

### 4. Random Episode Selection

**Task**: Determine how to select random episodes from a source

**Decision**: Query all matching episodes, then randomly select N using `Collections.shuffle()` or similar

**Rationale**:
- Simple and efficient for typical episode counts
- No need for database-level random selection (SQLite RANDOM() is non-deterministic)
- Works with existing query methods

**Alternatives Considered**:
- SQLite RANDOM() → Rejected: Non-deterministic, harder to test
- Pre-computed random order → Rejected: Unnecessary complexity

### 5. Rule Validation and Error Handling

**Task**: Determine how to handle invalid rules (deleted feed/tag, insufficient episodes)

**Decision**:
- **Deleted feed/tag**: Skip rule silently, continue with next rule (FR-028)
- **Insufficient episodes**: Add all available episodes, continue with next rule (FR-027)
- **No episodes match**: Complete refill with empty queue (FR-029)

**Rationale**:
- Graceful degradation - refill continues even with invalid rules
- User can fix invalid rules later in edit mode
- Prevents refill failures from blocking queue management

**Alternatives Considered**:
- Show errors during refill → Rejected: Interrupts user experience, refill should be seamless
- Validate rules before refill → Rejected: Adds complexity, rules may become invalid between validation and refill

### 6. Automatic Refill Trigger

**Task**: Determine when and how to trigger automatic refill when queue runs out

**Decision**: Hook into existing playback completion logic in `PlaybackService`:
- Listen for `PlaybackHistoryEvent` when episode finishes
- Check if queue is empty
- If empty and rules exist, trigger refill via `QueueRefillEngine`
- Use existing `QueueEvent` to notify UI

**Rationale**:
- Reuses existing playback event system (FR-024)
- Integrates with existing queue management
- Maintains event-driven architecture (Constitution Principle IV)

**Alternatives Considered**:
- Poll queue state periodically → Rejected: Inefficient, violates event-driven pattern
- Check in UI layer → Rejected: UI shouldn't trigger business logic, violates architecture

### 7. Ruleset Editing UI Architecture

**Task**: Design UI for editing rulesets (add, remove, modify, reorder rules)

**Decision**: Create `QueueRulesetEditFragment` with:
- RecyclerView for rule list (drag-to-reorder support)
- Dialog for adding/editing individual rules
- ViewModel (`QueueRulesetViewModel`) to manage ruleset state
- Save button that calls `DBWriter` methods

**Rationale**:
- Follows existing AntennaPod UI patterns (Fragment + ViewModel)
- Reuses existing UI components (RecyclerView, dialogs)
- Maintains separation of concerns (UI vs. business logic)

**Alternatives Considered**:
- Inline editing in QueueFragment → Rejected: Too complex, violates single responsibility
- Separate Activity → Rejected: Unnecessary, Fragment is sufficient

## Technical Decisions Summary

| Decision Area | Decision | Rationale |
|--------------|----------|-----------|
| Database Schema | Two new tables (QueueRuleset, RefillRule) | Normalized design, efficient queries |
| Rule Engine | Separate QueueRefillEngine class | Separation of concerns, testability |
| Episode Selection | Reuse existing DBReader/FeedItemFilter methods | Code reuse (FR-024) |
| Random Selection | In-memory shuffle after query | Simple, testable |
| Error Handling | Skip invalid rules, continue refill | Graceful degradation |
| Auto Refill Trigger | Hook into PlaybackHistoryEvent | Event-driven architecture |
| UI Architecture | Fragment + ViewModel pattern | Follows existing patterns |

## Dependencies

- Existing `DBWriter` and `DBReader` classes (queue operations)
- Existing `FeedItemFilter` and tag filtering mechanisms
- Existing `EventBus` for cross-component communication
- Existing `PlaybackService` for playback completion events
- Existing UI components (RecyclerView, dialogs, ViewModels)

## Open Questions

None - all technical decisions have been made based on existing codebase patterns and requirements.
