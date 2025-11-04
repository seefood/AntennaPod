# Queue Switching Behavior Bugs - Analysis & Fixes

## Issues Reported

1. **Episode doesn't stop playing when switching queues**
2. **Currently playing episode stays active instead of being replaced with new queue's last played episode**
3. **New queue's last played episode auto-starts playing (shouldn't)**
4. **Episode from different queue gets added to current queue (unwanted)**

## Root Cause Analysis

### Current Flow (BROKEN)
```
User switches queue
    ↓
QueueViewModel.switchActiveQueue(queueId)
    ├─ Saves playback state for OLD queue
    ├─ Updates UserPreferences.setCurrentQueueId(queueId)
    ├─ Loads NEW queue's metadata
    ├─ Restores playback state: PlaybackPreferences.writeMediaPlaying(media)
    └─ Posts QueueEvent.queueSwitched(queueId)
    ↓
PlaybackService.onQueueSwitched(event)
    ├─ Pauses current playback (mediaPlayer.pause())
    └─ Calls startPlayingFromPreferences()
        ├─ Loads playable from PlaybackPreferences
        └─ Calls startPlaying(playable, false)
            ├─ mediaPlayer.playMediaObject(..., true, true) ← AUTO-PLAYS
            ├─ addPlayableToQueue(playable) ← ADDS TO QUEUE (WRONG!)
            └─ Updates notification
```

### Specific Problems

**Problem 1 & 3: Auto-Play on Queue Switch**
- **File**: `PlaybackService.java:1707`
- **Method**: `onQueueSwitched()` calls `startPlayingFromPreferences()`
- **Issue**: `startPlayingFromPreferences()` → `startPlaying()` → `mediaPlayer.playMediaObject(..., true, true)`
- **The `true, true` flags mean: prepare AND play immediately**
- **Should**: Load the episode but NOT start playing it

**Problem 2: Episode Not Replaced**
- **File**: `PlaybackService.java:1806`
- **Method**: `addPlayableToQueue()`
- **Issue**: When switching queues, the old episode is still in the queue and the new one is added
- **Should**: Clear or replace the current playable in the queue

**Problem 4: Episode Added to Queue**
- **File**: `PlaybackService.java:1806` inside `startPlaying()`
- **Issue**: `addPlayableToQueue(playable)` is called unconditionally
- **Should**: Only add to queue if it's a NEW playable being started by user action, not when loading from queue switch

## Detailed Fix Strategy

### Fix 1: Don't Auto-Play on Queue Switch
**File**: `PlaybackService.java:1695-1709`

**Current Code**:
```java
public void onQueueSwitched(QueueEvent event) {
    if (event.action == QueueEvent.Action.QUEUE_SWITCHED) {
        // Pause current playback
        if (mediaPlayer.getPlayerStatus() == PlayerStatus.PLAYING) {
            mediaPlayer.pause(true, false);
        }
        // BUG: This auto-plays the new episode
        startPlayingFromPreferences();
    }
}
```

**Fixed Code**:
```java
public void onQueueSwitched(QueueEvent event) {
    if (event.action == QueueEvent.Action.QUEUE_SWITCHED) {
        Log.d(TAG, "Queue switched to: " + event.queueId);

        // Pause current playback (don't abandon audio focus yet)
        if (mediaPlayer.getPlayerStatus() == PlayerStatus.PLAYING) {
            mediaPlayer.pause(true, false);
        }

        // Load the new queue's saved playback state WITHOUT auto-playing
        // QueueViewModel has already updated PlaybackPreferences
        loadPlayableFromQueueWithoutPlaying();
    }
}

/**
 * Load the playable from preferences for display/queue info,
 * but don't start playing it.
 * Used when switching queues - just updates UI state.
 */
private void loadPlayableFromQueueWithoutPlaying() {
    Disposable d = Observable.fromCallable(() ->
            DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId()))
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            playable -> {
                if (playable != null) {
                    // Prepare but don't play
                    mediaPlayer.setPlayable(playable);
                    updateNotificationAndMediaSession(playable);
                    Log.d(TAG, "Loaded queue episode: " + playable.getEpisodeTitle());
                }
            },
            error -> {
                Log.d(TAG, "Could not load playable from queue");
                error.printStackTrace();
            });
    singleShotDisposables.add(d);
}
```

### Fix 2: Don't Add Queue Episode to Queue Again
**File**: `PlaybackService.java:785-807`

**Issue**: `startPlaying()` unconditionally calls `addPlayableToQueue()`
**Current Flow**: Queue Switch → Load Episode → Add to Queue (WRONG - it's already in the queue)

**Proposed Solution**:
Create separate paths:
- `startPlaying(Playable)` - User-initiated play (add to queue)
- `loadQueueEpisode(Playable)` - Queue switch/navigation (don't add to queue)

```java
/**
 * Load and prepare an episode from the current queue without adding it again.
 * Used when switching queues - the episode is already in the queue.
 */
private void loadQueueEpisode(Playable playable) {
    boolean localFeed = URLUtil.isContentUrl(playable.getStreamUrl());
    boolean stream = !playable.localFileAvailable() || localFeed;

    if (stream && !localFeed && !NetworkUtils.isStreamingAllowed()) {
        displayStreamingNotAllowedNotification(
            new PlaybackServiceStarter(this, playable).getIntent());
        PlaybackPreferences.writeNoMediaPlaying();
        stateManager.stopService();
        return;
    }

    if (!playable.getIdentifier().equals(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())) {
        PlaybackPreferences.clearCurrentlyPlayingTemporaryPlaybackSettings();
    }

    // Prepare but don't play (autoPlay=false)
    // Don't add to queue (it's already there)
    mediaPlayer.playMediaObject(playable, stream, false, true); // false = don't auto-play
    stateManager.validStartCommandWasReceived();
    recreateMediaSessionIfNeeded();
    updateNotificationAndMediaSession(playable);
    // NO addPlayableToQueue() call here!
}
```

### Fix 3: Update Queue State Properly
**File**: `PlaybackService.java`

When switching queues, we should:
1. Pause current playback ✓
2. Clear/reset the playback session
3. Load the episode info from the new queue
4. Update UI (notification, media session)
5. NOT auto-start playing
6. NOT add the episode to the queue

```java
public void onQueueSwitched(QueueEvent event) {
    if (event.action == QueueEvent.Action.QUEUE_SWITCHED) {
        Log.d(TAG, "Queue switched to: " + event.queueId);

        // Pause playback without abandoning audio focus
        if (mediaPlayer.getPlayerStatus() == PlayerStatus.PLAYING) {
            mediaPlayer.pause(true, false);
        }

        // Load (but don't play) the last episode from new queue
        loadQueueEpisodeForDisplay();
    }
}

private void loadQueueEpisodeForDisplay() {
    long feedMediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
    if (feedMediaId < 0) {
        // No saved episode for this queue
        Log.d(TAG, "No saved episode for this queue");
        return;
    }

    Disposable d = Observable.fromCallable(() -> DBReader.getFeedMedia(feedMediaId))
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            playable -> loadQueueEpisode(playable),
            error -> {
                Log.d(TAG, "Could not load episode for queue");
                error.printStackTrace();
            });
    singleShotDisposables.add(d);
}
```

## Implementation Summary

| Issue | Root Cause | File:Line | Fix |
|-------|-----------|----------|-----|
| 1. Episode doesn't stop | Already pausing (working) | PlaybackService:1701 | ✓ Works |
| 2. Episode not replaced | Auto-play happens | PlaybackService:1707 | Remove auto-play |
| 3. Auto-plays | `startPlayingFromPreferences()` | PlaybackService:776 | Use `loadQueueEpisode()` instead |
| 4. Added to queue | `addPlayableToQueue()` in `startPlaying()` | PlaybackService:806 | Create separate `loadQueueEpisode()` |

## Testing

After implementing fixes, verify:
1. Switch between queues → old episode stops, no auto-play
2. Switch back to first queue → resumes from where it was last playing
3. Episode doesn't get added to queue again
4. Notification shows correct episode title
5. Media session state is correct

## Related Code References

- `QueueViewModel.switchActiveQueue()` - Lines 136-177
- `PlaybackService.onQueueSwitched()` - Lines 1695-1709
- `PlaybackService.startPlayingFromPreferences()` - Lines 771-783
- `PlaybackService.startPlaying()` - Lines 785-807
- `PlaybackService.addPlayableToQueue()` - Lines 1806+
