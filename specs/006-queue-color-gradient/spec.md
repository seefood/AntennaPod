# Phase 6: Queue Color Gradient - Title Bar Styling

**Status**: Design Complete, Ready for Implementation
**Priority**: P2 (Enhancement - improves visual UX)
**Scope**: Apply queue's color as gradient background on title bars across relevant screens

## Overview

Display the currently active queue's color as a visual indicator on all major screen title bars. The gradient fades from the queue's color at the top to the default app background color, providing a subtle but persistent visual connection to the current queue context.

## User Stories

### Story 1: Visual Queue Awareness
**As a** user managing multiple queues,
**I want** to see a visual indication of which queue is currently active
**So that** I always know my current context without checking the queue selector

**Acceptance Criteria**:
- Active queue's color appears as gradient in title bar of every major screen
- Gradient is subtle (not overwhelming) but visually distinct
- Gradient changes immediately when switching queues
- Matches queue color displayed in queue management screen

---

## Requirements

### Functional Requirements

- **FR-001**: Queue color gradient MUST appear on title bar of:
  - Player screen (primary playback screen)
  - Mini player (collapsed playback view)
  - Queue pane (episode queue display)
  - Episodes pane (podcast episode list)
  - Home/Browse screen (if using title bar)
  - Queue management screen (queue selector/editor)

- **FR-002**: Gradient MUST fade from queue color (top) to default app background (bottom)

- **FR-003**: Gradient MUST update immediately when user switches queues

- **FR-004**: Gradient MUST persist when navigating between screens with same queue active

- **FR-005**: If no queue is selected (edge case), gradient MUST show default app theme color

- **FR-006**: Gradient styling MUST NOT impact text readability on title bar (sufficient contrast)

- **FR-007**: Title bar text and icons MUST remain readable at all times (dynamic contrast if needed)

### Design Requirements

- **DR-001**: Use linear gradient (top to bottom)
- **DR-002**: Gradient should be ~80px tall (standard toolbar height + some extension)
- **DR-003**: Apply Material Design guidelines for gradient intensity
- **DR-004**: Support both light and dark theme variations
- **DR-005**: Ensure gradient works with safe area insets on notch devices

## Implementation Plan

### Phase 1: Infrastructure Setup

1. **Create gradient utility class**
   - File: `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueColorGradient.java`
   - Method: `createQueueGradientDrawable(queueColor, contextColor)` → Drawable
   - Handles color conversion and gradient creation

2. **Create gradient layout helper**
   - File: `ui/common/src/main/res/layout/queue_color_gradient_header.xml`
   - Reusable layout with gradient background
   - Can be included in any screen's title bar

3. **Extend QueueViewModel with LiveData**
   - Add `getCurrentQueueColor()` LiveData
   - Exposes active queue's color for binding in views

### Phase 2: Screen Integration

Apply gradient to each screen:

4. **Player Screen** (primary focus)
   - File: `app/src/main/java/de/danoeh/antennapod/ui/screen/player/PlayerDetailsFragment.java`
   - Find: Title bar/AppBar component
   - Apply: Queue color gradient background

5. **Mini Player**
   - File: `app/src/main/java/de/danoeh/antennapod/ui/screen/player/VideoPlayerDetailFragment.java` (or similar)
   - Find: Collapsed player toolbar
   - Apply: Queue color gradient background

6. **Queue Pane**
   - File: `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueFragment.java`
   - Find: Title bar with "Queue" label
   - Apply: Queue color gradient background
   - Update title: Display queue name instead of "Queue"

7. **Episodes Pane**
   - File: `app/src/main/java/de/danoeh/antennapod/ui/screen/episodes/EpisodesFragment.java` (or equivalent)
   - Find: Title bar
   - Apply: Queue color gradient background

8. **Home/Browse Screen**
   - File: `app/src/main/java/de/danoeh/antennapod/ui/fragment/SubscriptionsFragment.java` (or main fragment)
   - Find: Title bar (if present)
   - Apply: Queue color gradient background

9. **Queue Management Screen**
   - File: `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueManagementFragment.java`
   - Find: Title bar
   - Apply: Queue color gradient background
   - Optional: Highlight current queue with different styling

### Phase 3: Testing & Polish

10. **Visual Testing**
    - Test gradient visibility on all screens
    - Verify color accuracy matches queue list
    - Check readability of text on gradient
    - Test dark/light theme switching
    - Verify behavior on different screen sizes

11. **Edge Case Testing**
    - Queue with very light color + light theme
    - Queue with very dark color + dark theme
    - Queue deletion while on that queue's screen
    - Rapid queue switching
    - Orientation change

## Technical Details

### Gradient Creation

```java
// Example implementation
int queueColor = queue.getColor();
int backgroundColor = context.getColor(R.color.background_light);
int[] colors = {queueColor, backgroundColor};
float[] positions = {0f, 1f};

GradientDrawable gradient = new GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    colors);
gradient.setDither(true);

view.setBackground(gradient);
```

### View Binding

```java
// In each fragment
queueViewModel.getCurrentQueueColor().observe(getViewLifecycleOwner(), color -> {
    int bgColor = getContext().getColor(R.color.background_light);
    applyGradient(titleBar, color, bgColor);
});
```

### Kotlin Alternative (if project uses Kotlin)

```kotlin
queueViewModel.getCurrentQueueColor().observe(viewLifecycleOwner) { color ->
    val bgColor = ContextCompat.getColor(requireContext(), R.color.background_light)
    titleBar.background = createGradientDrawable(color, bgColor)
}
```

## Considerations

### Performance
- Gradient creation should be cached (not recreated on every frame)
- LiveData subscription prevents excessive drawable recreation
- Consider using hardware acceleration for smooth animations

### Accessibility
- Ensure sufficient contrast between text and gradient
- Don't rely solely on color to convey information
- Maintain clear visual hierarchy on title bar

### Compatibility
- Test on API 21+ (minimum supported version)
- Ensure gradient works with ViewBinding
- Verify gradient doesn't break existing Material Design components

## Success Criteria

✅ Gradient visible on all target screens
✅ Colors match queue management display exactly
✅ Updates immediately when switching queues
✅ Text remains readable on all gradient variations
✅ No visual glitches when navigating between screens
✅ Works correctly in light and dark themes
✅ No performance degradation
✅ Code builds with no warnings

## Files to Create/Modify

**Create**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueColorGradient.java`
- `ui/common/src/main/res/layout/queue_color_gradient_header.xml`

**Modify**:
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java` (add LiveData)
- `app/src/main/java/de/danoeh/antennapod/ui/screen/player/PlayerDetailsFragment.java`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueFragment.java`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/episodes/EpisodesFragment.java`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueManagementFragment.java`
- Other screen fragments as identified
- `CLAUDE.md` (document this feature)

## Timeline Estimate

- Phase 1 (Infrastructure): 2 hours
- Phase 2 (Integration): 4-6 hours (depends on number of screens)
- Phase 3 (Testing & Polish): 2-3 hours

**Total**: 8-11 hours

## Notes

- This is a visual enhancement with no backend changes required
- QueueViewModel already supports getting current queue color
- All database operations already exist
- Focus is purely on UI binding and gradient styling
- Can be implemented incrementally (one screen at a time)
