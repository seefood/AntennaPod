# Feature Specification: Queue UI for Multiple Queues

**Feature Branch**: `queue-ui/001-specification`
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

Users need to customize queue appearance with colors and optionally reorder queues to match their mental model of importance or usage frequency. Color selection uses a visual palette with 12 theme-matched colors, and names can include emoji for additional personalization.

**Why this priority**: Customization improves usability by helping users visually distinguish queues at a glance, reducing cognitive load. Less critical than switching/creating but important for usability.

**Independent Test**: Can be fully tested by editing queue name, color, and order; verifying changes persist and are reflected in title bar gradient and throughout the app.

**Acceptance Scenarios**:

1. **Given** user has a queue, **When** user taps edit/settings on that queue, **Then** edit dialog opens showing current name and color with tick mark on selected color
2. **Given** edit dialog is open with 12-color palette visible, **When** user taps a different color, **Then** tick mark moves to new color and dialog updates preview
3. **Given** edit dialog is open, **When** user changes queue name (including adding emoji) and confirms, **Then** name change persists and displays in queue list
4. **Given** queue name is changed, **When** user navigates to any main pane, **Then** new queue name is visible and title bar shows updated gradient using queue's color
5. **Given** multiple queues exist, **When** user reorders queues in queue list, **Then** new order is persisted and reflected in queue selection pane
6. **Given** queue color is changed, **When** user switches to that queue or views its entry in list, **Then** new color appears immediately in gradient and queue item display

---

### User Story 4 - Delete Queues with Safeguards (Priority: P2)

Users need to remove queues they no longer need, with appropriate safeguards to prevent accidental data loss. The delete button is only visible when multiple queues exist, preventing accidental deletion of the last queue.

**Why this priority**: Cleanup capability is important for long-term usability but less critical than core queue management. Safeguards are essential to prevent user frustration.

**Independent Test**: Can be fully tested by deleting a queue, verifying confirmation dialog, and confirming queue is removed from UI and persistence layer.

**Acceptance Scenarios**:

1. **Given** user has a queue with episodes, **When** user opens edit dialog for that queue, **Then** "Delete queue" button is visible (assuming multiple queues exist)
2. **Given** "Delete queue" button is tapped, **When** user confirms deletion, **Then** queue is removed from system and active queue switches to another available queue
3. **Given** only one queue remains, **When** user opens edit dialog, **Then** "Delete queue" button is hidden and delete action is not available
4. **Given** queue is deleted, **When** user navigates to any screen, **Then** deleted queue no longer appears in queue selector and title bar gradient reflects active queue

---

### User Story 5 - Copy and Move Episodes Between Queues (Priority: P3)

Users need to organize episodes across multiple queues by copying episodes to additional queues or moving them between queues. This is accessed via drag actions on queue items.

**Why this priority**: Episode organization across queues enhances workflow flexibility but is less critical than core queue switching/creation. Complements existing drag-action system.

**Independent Test**: Can be fully tested by dragging queue items left/right, selecting "Copy to queue" or "Move to queue", choosing destination queue, and verifying episode appears in correct queue(s).

**Acceptance Scenarios**:

1. **Given** user has episodes in a queue, **When** user drags an episode left or right, **Then** action menu appears including "Copy to queue" and "Move to queue" options
2. **Given** "Copy to queue" is selected, **When** user selects a destination queue from dialog, **Then** episode is added to destination queue without being removed from current queue
3. **Given** "Move to queue" is selected, **When** user selects a destination queue from dialog, **Then** episode is removed from current queue and added to destination queue
4. **Given** user cancels the queue selector dialog, **When** dialog closes, **Then** no changes are made to episode queue assignments
5. **Given** episode is copied to multiple queues, **When** user views that episode's details, **Then** it shows queues where episode appears

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
- **FR-016**: Queue button MUST be customizable and added to bottom navigation bar with bookshelf icon (three books of different sizes)
- **FR-017**: System MUST display queue's assigned color as a gradient background on title bar (queue color fading to default theme background) in all main panes
- **FR-018**: Title bar gradient MUST be visible in Play screen, Queue pane, Inbox pane, Episodes pane, and Queue selection/management pane
- **FR-019**: Queue creation dialog MUST accept alphanumeric characters and emoji in queue name field
- **FR-020**: Queue color picker MUST display 12 colors matching the app theme palette
- **FR-021**: Selected color in color picker MUST be indicated with a visual tick mark
- **FR-022**: Queue edit dialog MUST display "Delete queue" button only when more than one queue exists
- **FR-023**: System MUST add "Copy to queue" and "Move to queue" as configurable drag-action options for queue items
- **FR-024**: When user invokes "Copy to queue" or "Move to queue" action on a queue item, system MUST display queue selector dialog
- **FR-025**: "Copy to queue" action MUST add episode to selected queue without removing it from current queue
- **FR-026**: "Move to queue" action MUST remove episode from current queue and add it to selected queue

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

## UI Design Specifications

### Bottom Navigation Bar
- **Queue Button**: Add customizable button to bottom navigation bar alongside existing tabs (Play, Queue, Inbox, Episodes)
- **Icon**: Bookshelf with three books of different sizes (visual indicator of queue organization)
- **Color Indicator**: Active queue's color shown as gradient background on top pane (title area), transitioning from queue color at top to default theme background color
- **Behavior**: Tapping queue button opens queue selector/management pane

### Title Bar Gradient
- **Implementation**: Apply to all main panes (Play screen, Queue, Inbox, Episodes panes) AND new Queue selection/management pane
- **Visual**: Gradient background on title (top element) using active queue's color fading to default theme background
- **Purpose**: Persistent visual indicator of active queue throughout the app

### Queue Selection/Management Pane
- **Access**: Via queue button in bottom navigation bar
- **Display**: Shows all available queues in a list
- **Selection**: Tapping a queue in list switches active queue (triggers pause-load-restore if playing)
- **Queue List Item**: Shows queue name and associated color

### Queue Creation Dialog
- **Trigger**: "Create new queue" button in queue selection pane
- **Name Input**: Text field supporting alphanumeric characters and emoji
- **Color Picker**: Visual palette of 12 colors matching app theme palette
- **Color Selection**: Tapped color is indicated with a tick mark
- **Confirm Action**: Creates new queue, persists it, sets as active queue

### Queue Editing Dialog
- **Trigger**: Long-press or edit action on queue in list
- **Name Input**: Text field with current name, supporting alphanumeric and emoji characters
- **Color Picker**: Palette of 12 theme-matched colors with tick mark showing current selection
- **Delete Button**: Visible only when more than one queue exists (prevents deletion of last queue)
- **Confirm Actions**: Name and color changes persist immediately; delete removes queue and switches to another available queue

### Queue Item Drag Actions
- **Existing Feature**: Queue items can be dragged left or right to trigger configurable functions
- **New Options**: Add "Copy to queue" and "Move to queue" to drag action options
- **Queue Selector Dialog**: When either action is invoked, a queue selector dialog appears
- **Copy to queue**: Adds episode to selected queue (does not remove from current queue)
- **Move to queue**: Removes episode from current queue and adds to selected queue

## Assumptions

- Queue switching during playback uses pause-load-restore pattern as confirmed by user
- Bottom navigation bar accepts custom button integration
- Color palette: 12 theme-matched colors (will be defined during design phase)
- Queue names support alphanumeric characters + emoji (no length limit specified, recommend 256 chars max)
- Active queue color uses gradient: queue color → default theme background
- Gradient applied consistently across all main panes for visual cohesion
- At least one queue must always exist (cannot delete all queues)
- Theme palette colors are accessible via theme system
- Drag-to-action feature already exists; "Copy to queue" and "Move to queue" are new options to that existing system
