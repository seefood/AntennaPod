# QueueRefillEngine Contracts: Smart Queues

**Feature**: Smart Queues
**Date**: 2025-11-08
**Phase**: 1 - Design & Contracts

## RefillEngine Class

### processRuleset

```java
/**
 * Processes a ruleset and refills a queue according to its rules.
 *
 * @param queueId The ID of the queue to refill
 * @param clearQueue If true, clear existing episodes before refilling
 * @return RefillResult Result containing added episode count and any errors
 */
public RefillResult processRuleset(long queueId, boolean clearQueue)
```

**Preconditions**:
- `queueId` must reference an existing queue
- Queue must have a ruleset with at least one rule (or clearQueue must be true)

**Postconditions**:
- If `clearQueue` is true, all existing episodes removed from queue (not marked as played/deleted, FR-038)
- Rules processed in order (FR-014)
- Episodes selected and added to queue
- Duplicate episodes prevented (FR-013)
- Partial fulfillment applied (FR-027)
- Invalid rules skipped (FR-028)
- Returns refill result with statistics

**Algorithm**:
1. Get ruleset for queue
2. If `clearQueue` is true or first rule is CLEAR_QUEUE, remove all episodes from queue
3. For each rule in order:
   - If CLEAR_QUEUE: Already handled in step 2
   - If ADD_EPISODES:
     - Get episodes matching rule criteria via `DBReader.getEpisodesForRule()`
     - Apply selection method (oldest/newest/random)
     - Filter out episodes already in queue (prevent duplicates)
     - Take up to `count` episodes (partial fulfillment if fewer available)
     - Add episodes to queue via `DBWriter.addQueueItem()`
     - If source doesn't exist (deleted feed/tag), skip rule silently (FR-028)
4. Return result with statistics

### RefillResult

```java
public class RefillResult {
    public final int episodesAdded;
    public final int episodesRemoved;
    public final int rulesProcessed;
    public final int rulesSkipped;
    public final List<String> errors; // Empty if no errors

    public RefillResult(int episodesAdded, int episodesRemoved,
                       int rulesProcessed, int rulesSkipped,
                       List<String> errors) {
        this.episodesAdded = episodesAdded;
        this.episodesRemoved = episodesRemoved;
        this.rulesProcessed = rulesProcessed;
        this.rulesSkipped = rulesSkipped;
        this.errors = errors != null ? errors : new ArrayList<>();
    }
}
```

## Integration Points

### PlaybackService Integration

```java
/**
 * Hook into PlaybackService to trigger automatic refill when queue runs out.
 *
 * Listens for PlaybackHistoryEvent when episode finishes.
 * Checks if queue is empty.
 * If empty and ruleset exists, calls QueueRefillEngine.processRuleset().
 * Posts QueueEvent.refilled when complete.
 */
@Subscribe(threadMode = ThreadMode.MAIN)
public void onPlaybackHistoryEvent(PlaybackHistoryEvent event) {
    if (queueIsEmpty() && hasQueueRuleset(getCurrentQueueId())) {
        QueueRefillEngine engine = new QueueRefillEngine();
        RefillResult result = engine.processRuleset(getCurrentQueueId(), false);
        EventBus.getDefault().post(QueueEvent.refilled(getCurrentQueueId(), result));
    }
}
```

### QueueFragment Integration

```java
/**
 * Manual refill button handler.
 *
 * Calls QueueRefillEngine.processRuleset() with clearQueue based on first rule.
 * Starts playback from first episode after refill completes.
 */
public void onRefillButtonClick() {
    long queueId = getCurrentQueueId();
    QueueRuleset ruleset = DBReader.getQueueRuleset(queueId);
    boolean clearQueue = ruleset != null && DBReader.hasClearQueueRule(ruleset.getId());

    QueueRefillEngine engine = new QueueRefillEngine();
    RefillResult result = engine.processRuleset(queueId, clearQueue);

    if (result.episodesAdded > 0) {
        startPlaybackFromFirstEpisode();
    }
}
```
