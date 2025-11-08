# Feature Specification: Smart Queues

**Feature Branch**: `001-smart-queues`
**Created**: 2025-11-08
**Status**: Draft
**Input**: User description: "Antennapod is a podcast player, it has multiple queues the users can switch between but each time they are done playing the entire queue, they need to refill it manually. Goal of this feature is to refill a queue automatically according to a set of rules the user can edit."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Edit Queue Rules (Priority: P1)

A user wants to customize the rules that determine how their queue is automatically refilled, including adding, removing, changing, and reordering rules.

**Why this priority**: This is the foundational functionality that must be implemented first. User Stories 2 and 3 depend on the ability to create and edit rules - you cannot test refilling with rules or automatic refill without first being able to configure rules. This enables users to define their queue refill behavior.

**Independent Test**: Can be fully tested by entering edit mode for a queue's ruleset, making changes (add/remove/change/reorder), saving, and verifying the rules are persisted. This delivers value by giving users control over their listening experience and is required for testing subsequent user stories.

**Acceptance Scenarios**:

1. **Given** a queue with an existing ruleset, **When** the user enters edit mode, **Then** they can view all current rules in order
2. **Given** a queue in edit mode, **When** the user adds a new rule, **Then** the rule is added to the ruleset and can be positioned anywhere in the order
3. **Given** a queue in edit mode, **When** the user removes a rule, **Then** the rule is removed from the ruleset
4. **Given** a queue in edit mode, **When** the user changes a rule's parameters (count, source, selection method), **Then** the rule is updated with the new parameters
5. **Given** a queue in edit mode, **When** the user reorders rules, **Then** the rules are applied in the new order during refill
6. **Given** a queue with no rules configured, **When** the user enters edit mode, **Then** they can add the first rule to the ruleset

---

### User Story 2 - Manual Queue Refill with Rules (Priority: P2)

A user wants to refill their podcast queue automatically using predefined rules instead of manually selecting episodes one by one.

**Why this priority**: This is the core functionality that delivers the primary value - eliminating manual queue refilling. However, it depends on User Story 1 (Edit Queue Rules) being implemented first, as you cannot test refilling without the ability to create rules.

**Independent Test**: Can be fully tested by creating a queue with rules (using US1), pressing the refill button, and verifying the queue is populated according to the rules. This delivers immediate value by automating queue management.

**Acceptance Scenarios**:

1. **Given** a queue with no episodes and rules configured (via US1), **When** the user presses the refill button, **Then** the queue is populated with episodes matching the rules and playback starts from the first episode
2. **Given** a queue with existing episodes and a "Clear queue" rule as the first rule, **When** the user presses the refill button, **Then** all existing episodes are removed and the queue is repopulated according to the remaining rules
3. **Given** a queue with rules that add episodes from multiple sources (feeds, tags, inbox), **When** the user presses the refill button, **Then** episodes are added in rule order with no duplicates even if an episode matches multiple rules
4. **Given** a queue with rules configured, **When** the user presses the refill button, **Then** only episodes that are not 100% played are selected (partially played episodes can be included)

---

### User Story 3 - Automatic Refill When Queue Runs Out (Priority: P3)

A user wants their queue to automatically refill when they finish playing all episodes, so they don't need to manually trigger refill.

**Why this priority**: This enhances the core functionality by eliminating the need to manually press the refill button, providing a seamless continuous playback experience. It depends on User Stories 1 and 2 being implemented first, as it requires both rule configuration and manual refill functionality.

**Independent Test**: Can be fully tested by configuring rules (US1), playing through all episodes in a queue, and verifying the queue automatically refills and continues playing. This delivers value by enabling uninterrupted listening sessions.

**Acceptance Scenarios**:

1. **Given** a queue with rules configured (via US1) and the last episode is currently playing, **When** the last episode finishes playing, **Then** the queue automatically refills according to the rules and playback continues from the first new episode
2. **Given** a queue that runs out of episodes during playback, **When** the queue becomes empty, **Then** the queue automatically refills according to the rules without user intervention
3. **Given** a queue with no rules configured, **When** the queue runs out of episodes, **Then** no automatic refill occurs (user must manually add episodes)

---

### Edge Cases

- What happens when a rule requests more episodes than are available from the source (e.g., "add 10 newest episodes" but only 5 exist)?
- How does the system handle a queue with rules but no episodes match any rule?
- What happens if a user tries to add a "Clear queue" rule in the middle or end of the ruleset?
- How does the system handle a feed or tag that no longer exists when a rule references it?
- What happens when all available episodes are already in the queue and refill is triggered?
- How does the system handle a queue that runs out of episodes while the user is not actively listening?
- What happens if a user edits rules while a refill is in progress?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow users to configure zero or more rules per queue that define how episodes are selected during refill
- **FR-002**: System MUST support a "Clear queue" rule that removes all existing episodes from the queue before applying other rules
- **FR-003**: System MUST support rules that add N oldest episodes from a specific feed
- **FR-004**: System MUST support rules that add N newest episodes from a specific feed
- **FR-005**: System MUST support rules that add N random episodes from a specific feed
- **FR-006**: System MUST support rules that add N oldest episodes from episodes with a specific tag
- **FR-007**: System MUST support rules that add N newest episodes from episodes with a specific tag
- **FR-008**: System MUST support rules that add N random episodes from episodes with a specific tag
- **FR-009**: System MUST support rules that add N oldest episodes from the inbox
- **FR-010**: System MUST support rules that add N newest episodes from the inbox
- **FR-011**: System MUST support rules that add N random episodes from the inbox
- **FR-012**: System MUST only select episodes that are not 100% played (partially played episodes are eligible)
- **FR-013**: System MUST prevent duplicate episodes in the queue when an episode matches multiple rules during a single refill operation
- **FR-014**: System MUST apply rules in the order they are configured in the ruleset
- **FR-015**: System MUST allow users to manually trigger queue refill via a refill button
- **FR-016**: System MUST automatically trigger queue refill when a queue runs out of episodes during playback
- **FR-017**: System MUST start playback from the first episode in the queue after a refill operation completes
- **FR-018**: System MUST allow users to enter edit mode for a queue's ruleset
- **FR-019**: System MUST allow users to add new rules to a ruleset in edit mode
- **FR-020**: System MUST allow users to remove rules from a ruleset in edit mode
- **FR-021**: System MUST allow users to modify existing rule parameters (count, source, selection method) in edit mode
- **FR-022**: System MUST allow users to reorder rules within a ruleset in edit mode
- **FR-023**: System MUST persist ruleset configuration per queue across app sessions
- **FR-024**: System MUST reuse existing code and data structures where possible (e.g., existing queue management, episode selection, feed/tag filtering)
- **FR-025**: System MUST only add new rules tables to the database schema - no modifications to existing tables
- **FR-026**: Implementation MUST commit code at the end of every stage when the task is stable, tested, and passes pre-commit tests (checkstyle, SpotBugs, Android Lint)

### Key Entities *(include if feature involves data)*

- **Queue Ruleset**: A collection of rules associated with a specific queue that defines how episodes are selected during refill. Contains an ordered list of rules and is stored per queue.

- **Refill Rule**: A single rule that defines episode selection criteria. Contains:
  - Rule type (clear queue, add episodes)
  - Selection method (oldest, newest, random)
  - Count (number of episodes to add)
  - Source (feed, tag, or inbox)
  - Source identifier (feed ID, tag name, or inbox indicator)

- **Episode Selection**: The process of finding episodes that match a rule's criteria from the available episode pool, excluding episodes that are 100% played. Must reuse existing episode filtering and selection mechanisms where possible.

### Implementation Constraints

- **Code Reuse**: The implementation MUST reuse existing code and data structures where possible. This includes:
  - Existing queue management operations (DBWriter, DBReader)
  - Existing episode filtering mechanisms (FeedItemFilter, tag filtering)
  - Existing feed and tag data structures
  - Existing EventBus patterns for queue updates

- **Database Schema**: The ONLY database change allowed is the addition of new rules tables. No modifications to existing tables (Feeds, FeedItems, FeedMedia, Queue, QueueMetadata, etc.) are permitted.

- **Development Workflow**: Code MUST be committed at the end of every stage when:
  - The task is stable (functionally complete for that stage)
  - The task is tested (unit tests pass)
  - Pre-commit tests pass (checkstyle, SpotBugs, Android Lint)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure queue refill rules in under 2 minutes
- **SC-002**: Queue refill operations complete in under 5 seconds for queues with up to 100 episodes
- **SC-003**: 90% of users successfully create and use at least one refill rule without assistance
- **SC-004**: Users reduce manual queue management time by at least 70% compared to manual episode selection
- **SC-005**: Automatic refill triggers successfully 95% of the time when queues run out of episodes
- **SC-006**: Refilled queues contain no duplicate episodes in 100% of refill operations
- **SC-007**: Users can edit and save rule changes in under 1 minute
