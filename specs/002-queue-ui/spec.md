# Feature Specification: Queue UI for Multiple Queues

**Feature Branch**: `002-queue-ui`
**Created**: 2025-11-01
**Status**: Draft
**Input**: UI for the multiple queue feature with queue switching, creation, editing, and deletion in bottom navigation

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.

  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Switch Between Queues (Priority: P1)

Users need a quick and intuitive way to switch between their queues while browsing podcasts and managing their queue. This is the core interaction that makes multiple queues valuable - allowing users to maintain separate listening contexts (e.g., "Work Podcasts", "Leisure", "Educational").

**Why this priority**: Queue switching is the fundamental interaction that enables the entire multiple queue feature. Without this, multiple queues have no practical value.

**Independent Test**: Can be fully tested by tapping queue indicators in bottom navigation, verifying active queue changes, and confirming that the queue's last-played position is restored.

**Acceptance Scenarios**:

1. **Given** user has multiple queues with episodes, **When** user taps a queue button in bottom navigation, **Then** active queue changes and UI updates to show that queue's episodes
2. **Given** user switches away from a queue while an episode is playing, **When** user switches back to that queue, **Then** the queue restores to the same episode position it had before switching
3. **Given** user has no queues, **When** app initializes, **Then** the default queue is created and active
4. **Given** queue has no episodes, **When** user switches to that queue, **Then** empty state is displayed with option to add episodes

---

### User Story 2 - Create and Name New Queues (Priority: P1)

Users need to create additional queues to organize episodes by category, mood, or listening context. Each queue should be individually identifiable via a name.

**Why this priority**: Creating queues is essential for the feature to deliver value. Without this, users are limited to one queue.

**Independent Test**: Can be fully tested by accessing queue creation UI, creating a new queue with a custom name, and verifying it appears in queue list and can be selected.

**Acceptance Scenarios**:

1. **Given** user is on queue switcher, **When** user taps "create new queue" action, **Then** dialog appears allowing text input for queue name
2. **Given** queue creation dialog is open, **When** user enters a name and confirms, **Then** new queue is created, persisted, and becomes the active queue
3. **Given** multiple queues exist, **When** user views queue list, **Then** all queues are displayed with their names
4. **Given** user leaves queue name blank, **When** user attempts to create queue, **Then** validation error is shown and queue is not created

---

### User Story 3 - Edit Queue Properties (Priority: P2)

Users need to customize queue appearance with colors and optionally reorder queues to match their mental model of importance or usage frequency.

**Why this priority**: Customization improves usability by helping users visually distinguish queues at a glance, reducing cognitive load. Less critical than switching/creating but important for usability.

**Independent Test**: Can be fully tested by editing queue name, color, and order; verifying changes persist and are reflected in UI.

**Acceptance Scenarios**:

1. **Given** user has a queue, **When** user taps edit/settings on that queue, **Then** edit dialog opens showing current name and color
2. **Given** edit dialog is open, **When** user changes queue name or color and confirms, **Then** changes are persisted and UI updates immediately
3. **Given** multiple queues exist, **When** user reorders queues in queue list, **Then** new order is persisted and reflected in bottom navigation
4. **Given** queue has been renamed, **When** user views all queues, **Then** new name appears everywhere queue is displayed

---

### User Story 4 - Delete Queues with Safeguards (Priority: P2)

Users need to remove queues they no longer need, with appropriate safeguards to prevent accidental data loss.

**Why this priority**: Cleanup capability is important for long-term usability but less critical than core queue management. Safeguards are essential to prevent user frustration.

**Independent Test**: Can be fully tested by deleting a queue, verifying confirmation dialog, and confirming queue is removed from UI and persistence layer.

**Acceptance Scenarios**:

1. **Given** user has a queue with episodes, **When** user initiates delete action, **Then** confirmation dialog appears warning about data loss
2. **Given** confirmation dialog shows queue will be deleted, **When** user confirms deletion, **Then** queue is removed from system and active queue defaults to another available queue
3. **Given** only one queue remains, **When** user attempts to delete it, **Then** delete action is disabled with explanation that at least one queue must exist
4. **Given** queue is deleted, **When** user navigates to any screen, **Then** deleted queue no longer appears in queue switcher

### Edge Cases

- What happens when user deletes the currently active queue? → Switch to another available queue automatically
- What happens when user has 10+ queues? → Queue switcher should handle scrolling or pagination gracefully
- What happens if queue name is very long? → Text should be truncated with ellipsis while maintaining readability
- What happens during playback when user switches queues? → Playback pauses, current queue's last position is saved, new queue's last position is restored, ready to resume
- What happens if a queue becomes empty (all episodes removed)? → Queue remains available, empty state is shown instead of episode list

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST display active queue indicator in bottom navigation bar at all times
- **FR-002**: System MUST persist the currently active queue ID and restore it on app startup
- **FR-003**: Users MUST be able to switch to any available queue by tapping queue button in bottom navigation
- **FR-004**: System MUST pause playback when user switches between queues during active playback
- **FR-005**: System MUST store and restore each queue's last-played episode position (episode ID and playback timestamp) when queue becomes active
- **FR-006**: Users MUST be able to create new queues with custom names via bottom navigation UI
- **FR-007**: System MUST prevent queue creation with empty names
- **FR-008**: Users MUST be able to rename existing queues and changes MUST persist immediately
- **FR-009**: Users MUST be able to assign a color to each queue for visual distinction in UI
- **FR-010**: System MUST display all queues in a ranked/ordered list where users can customize order
- **FR-011**: Users MUST be able to delete queues with explicit confirmation dialog warning of data loss
- **FR-012**: System MUST prevent deletion of the last remaining queue
- **FR-013**: When a queue is deleted, system MUST switch active queue to another available queue
- **FR-014**: System MUST display empty state when user switches to a queue with no episodes
- **FR-015**: System MUST support at least 20 queues per user account without UI degradation

### Key Entities *(include if feature involves data)*

- **Queue**: A container for episodes. Attributes: ID (unique), name (user-editable string), color (hex or color enum), sort_order (integer for display ordering), created_at (timestamp), currently_playing_episode_id (episode ID or null), currently_playing_position (playback timestamp in milliseconds or null)
- **Queue Item**: Link between a queue and an episode. Attributes: queue_id (foreign key), episode_id (foreign key), position_in_queue (integer for ordering within queue), added_at (timestamp)

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: Users can create a new queue in under 10 seconds from any screen in the app
- **SC-002**: Switching between queues completes with UI update within 500ms
- **SC-003**: Queue order and properties persist across app restarts with 100% reliability
- **SC-004**: Playback transitions between queues (pause current, restore position) complete within 1 second
- **SC-005**: All queue management actions (create, rename, delete, reorder) respond to user input with visual feedback within 300ms
- **SC-006**: Users successfully complete queue management tasks on first attempt 90% of the time (measured through analytics and user testing)
- **SC-007**: No data loss when user deletes non-active queue (episodes remain in system, only queue metadata is removed)

## Assumptions

- Queue switching during playback uses pause-load-restore pattern as confirmed by user
- Bottom navigation is appropriate location for queue management controls
- Visual distinction via color is sufficient; icons/images are not required
- Maximum 20 queues per user is acceptable limit
- Queue names can be up to 256 characters
- At least one queue must always exist (cannot delete all queues)
