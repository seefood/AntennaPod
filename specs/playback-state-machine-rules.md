# Playback State Machine Rules

## Overview
These rules define how the player should behave when:
1. Episodes are removed from queue
2. Episodes finish playing
3. Queues are switched

These rules ensure consistent playback behavior across all scenarios.

## Rule 1: Episode Removal (Currently Playing Episode)

When an episode is removed from the queue while it is the active (currently playing) episode:

### 1a. Save Playing State
- Record whether player was in PLAYING or PAUSED state
- This state will be restored after episode transition

### 1b. Select Next Active Episode
- Find the next **unfinished** episode in the queue
- "Unfinished" = episode.position < episode.duration (not fully played)

### 1c. Loop to First on No Next
- If there is no next episode (removed episode was at bottom)
- Loop back to the first episode in the queue
- This becomes the new active episode

### 1d. Stop if Last Episode and No Loop Target
- If the removed episode was the last episode in the queue
- AND there are no other episodes to loop to or skip forward to
- Stop playback entirely

### 1e. Remove from Player
- Clear the episode from the player's currently-playing slot
- Update player UI to reflect removal

### 1f. Empty Player on No Episodes
- If result is that queue is completely empty (case 1d)
- Player object must be empty
- Waiting for user to: add episodes, run refill, or switch queues
- Do NOT auto-refill on removal (only on finish)

### 1g. Restore Playing State
- If queue not empty AND there is a new active episode
- Check state saved in 1a
- If was PLAYING: resume playback of new episode
- If was PAUSED: move to new episode but stay paused

## Rule 2: Episode Finishes Playing (Last Episode Completes)

When an episode reaches the end and finishes playing:

### 2a. Non-Empty Queue Path
- If queue is NOT empty after this episode finishes
- Loop back to the top of the queue
- Continue playing the first episode
- Maintain PLAYING state

### 2b. Empty Queue - Attempt Refill
- If queue IS empty after this episode finishes
- Trigger automatic queue refill using configured rules
- Wait for refill to complete

### 2c. Post-Refill Behavior
- If refill added episodes: play first episode, maintain PLAYING state
- If refill added nothing (no rules OR rules found no episodes): STOP
- Player should be empty, waiting for user action

### Race Condition Note
- `removeQueueItem()` is asynchronous
- Must not check queue state until removal is complete
- Refill trigger must happen AFTER removal completes

## Rule 3: Queue Switching (Queue A → Queue B)

When user switches from one queue to another:

### 3a. Save Playing State
- Record current playback state (PLAYING or PAUSED)
- This will be restored after switching

### 3b. Save Last Played Episode to Queue Metadata
- Store currently playing episode ID to QueueMetadata.currently_playing_feedmedia_id
- Save the position to QueueMetadata.currently_playing_feed_id (or similar field)
- This preserves context when switching back to Queue A later

### 3c. Change Active Queue
- Update UserPreferences.setCurrentQueueId(queueB.id)
- This makes Queue B the active queue

### 3d-1. Restore Last Played Episode
- Read from QueueMetadata.currently_playing_feedmedia_id for Queue B
- Load that episode as the active episode in the player

### 3d-2. Handle Missing or Finished Episode
- If saved episode is no longer in Queue B:
  - Apply Rule 2 logic: find next unfinished episode
  - If no next: loop back to first episode
  - If no episodes at all: stop (empty queue case)
- If saved episode is fully played:
  - Same as above - find next unfinished episode

### 3e. Restore Playing State
- Check state saved in 3a
- If was PLAYING: resume playback of selected episode
- If was PAUSED: move to episode but stay paused

## Implementation Notes

### Key Components Affected

1. **PlaybackService.onQueueEvent()** (Rule 1)
   - Handle REMOVED action for currently playing episode
   - Implement 1a-1g logic
   - Save and restore pause/play state

2. **PlaybackService.onPlaybackHistoryEvent()** (Rule 2)
   - Wait for removeQueueItem() to complete
   - Check if queue empty AFTER removal
   - Trigger refill if empty

3. **QueueViewModel.switchActiveQueue()** (Rule 3)
   - Implement 3a-3e
   - Save metadata before switch
   - Restore episode and state after switch

### Database Schema Requirements

QueueMetadata table must have fields:
- `currently_playing_feedmedia_id`: ID of last played episode in this queue
- `currently_playing_feed_id`: Feed ID (secondary identifier)

These are already present in current schema.

### State Preservation

All playback state transitions must:
1. Save pause/play state
2. Select next episode
3. Clear old episode
4. Load new episode
5. Restore pause/play state
6. Notify listeners

## Test Scenarios

**Scenario 1: Remove bottom episode while playing**
- Expected: Loop to first, continue playing
- Verify: Pause state preserved

**Scenario 2: Remove only remaining episode**
- Expected: Player empty, playback stops
- Verify: No crash, UI shows empty player

**Scenario 3: Last episode finishes, queue empty, refill adds episodes**
- Expected: First refilled episode plays
- Verify: No old episode lingers in player

**Scenario 4: Last episode finishes, queue empty, no refill**
- Expected: Playback stops, player empty
- Verify: No crash

**Scenario 5: Switch queues while paused**
- Expected: New queue's last played episode selected, paused
- Verify: No auto-play on switch

**Scenario 6: Switch queues, saved episode no longer exists**
- Expected: Next unfinished episode selected
- Verify: Correct episode chosen per Rule 2 logic

**Scenario 7: Switch queues, saved episode fully played**
- Expected: Next unfinished episode selected
- Verify: Same as Scenario 6
