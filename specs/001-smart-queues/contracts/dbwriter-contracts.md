# DBWriter Contracts: Smart Queues

**Feature**: Smart Queues
**Date**: 2025-11-08
**Phase**: 1 - Design & Contracts

## Ruleset Operations

### createQueueRuleset

```java
/**
 * Creates a new ruleset for a queue.
 *
 * @param queueId The ID of the queue to create a ruleset for
 * @return Future<Long> The ID of the created ruleset
 */
public static Future<Long> createQueueRuleset(long queueId)
```

**Preconditions**:
- `queueId` must reference an existing queue
- Queue must not already have a ruleset

**Postconditions**:
- New ruleset created with `queueId`
- `createdAt` and `updatedAt` set to current timestamp
- Returns ruleset ID

**Events Posted**: None (ruleset creation is internal)

### updateQueueRuleset

```java
/**
 * Updates the ruleset's updatedAt timestamp.
 *
 * @param rulesetId The ID of the ruleset to update
 * @return Future<Void>
 */
public static Future<Void> updateQueueRuleset(long rulesetId)
```

**Preconditions**:
- `rulesetId` must reference an existing ruleset

**Postconditions**:
- `updatedAt` set to current timestamp

**Events Posted**: None (timestamp update is internal)

### deleteQueueRuleset

```java
/**
 * Deletes a ruleset and all its rules (cascade delete).
 *
 * @param rulesetId The ID of the ruleset to delete
 * @return Future<Void>
 */
public static Future<Void> deleteQueueRuleset(long rulesetId)
```

**Preconditions**:
- `rulesetId` must reference an existing ruleset

**Postconditions**:
- Ruleset deleted
- All associated rules deleted (cascade)

**Events Posted**: None (ruleset deletion is internal)

## Rule Operations

### createRefillRule

```java
/**
 * Creates a new refill rule in a ruleset.
 *
 * @param rulesetId The ID of the ruleset to add the rule to
 * @param position The position of the rule (0-based, lower numbers execute first)
 * @param ruleType The type of rule ("CLEAR_QUEUE" or "ADD_EPISODES")
 * @param selectionMethod The selection method ("OLDEST", "NEWEST", or "RANDOM") - null for CLEAR_QUEUE
 * @param count The number of episodes to add - null for CLEAR_QUEUE
 * @param sourceType The source type ("FEED", "TAG", or "INBOX") - null for CLEAR_QUEUE
 * @param sourceId The source identifier (feed ID, tag name, or null for INBOX) - null for CLEAR_QUEUE
 * @return Future<Long> The ID of the created rule
 */
public static Future<Long> createRefillRule(
    long rulesetId,
    int position,
    String ruleType,
    String selectionMethod,
    Integer count,
    String sourceType,
    String sourceId
)
```

**Preconditions**:
- `rulesetId` must reference an existing ruleset
- `position` must be unique within the ruleset
- For CLEAR_QUEUE: `selectionMethod`, `count`, `sourceType`, `sourceId` must be null
- For CLEAR_QUEUE: Only one CLEAR_QUEUE rule allowed per ruleset (FR-030)
- For CLEAR_QUEUE: Must be at position 0 (FR-031)
- For ADD_EPISODES: `selectionMethod`, `count`, `sourceType` must be non-null
- For ADD_EPISODES: `sourceId` must be non-null for FEED and TAG, null for INBOX

**Postconditions**:
- New rule created with specified parameters
- `createdAt` and `updatedAt` set to current timestamp
- If CLEAR_QUEUE added at non-zero position, automatically moved to position 0 (FR-031)
- Returns rule ID

**Events Posted**: None (rule creation is internal, UI updates via ruleset change events)

### updateRefillRule

```java
/**
 * Updates an existing refill rule.
 *
 * @param ruleId The ID of the rule to update
 * @param selectionMethod The new selection method - null to keep existing
 * @param count The new count - null to keep existing
 * @param sourceType The new source type - null to keep existing
 * @param sourceId The new source identifier - null to keep existing
 * @return Future<Void>
 */
public static Future<Void> updateRefillRule(
    long ruleId,
    String selectionMethod,
    Integer count,
    String sourceType,
    String sourceId
)
```

**Preconditions**:
- `ruleId` must reference an existing rule
- Rule must be of type ADD_EPISODES (CLEAR_QUEUE cannot be updated)

**Postconditions**:
- Rule parameters updated
- `updatedAt` set to current timestamp
- Ruleset `updatedAt` updated

**Events Posted**: None (rule update is internal)

### deleteRefillRule

```java
/**
 * Deletes a refill rule.
 *
 * @param ruleId The ID of the rule to delete
 * @return Future<Void>
 */
public static Future<Void> deleteRefillRule(long ruleId)
```

**Preconditions**:
- `ruleId` must reference an existing rule

**Postconditions**:
- Rule deleted
- Ruleset `updatedAt` updated

**Events Posted**: None (rule deletion is internal)

### reorderRefillRules

```java
/**
 * Reorders rules in a ruleset by updating their positions.
 *
 * @param rulesetId The ID of the ruleset
 * @param rulePositions Map of rule ID to new position
 * @return Future<Void>
 */
public static Future<Void> reorderRefillRules(
    long rulesetId,
    Map<Long, Integer> rulePositions
)
```

**Preconditions**:
- `rulesetId` must reference an existing ruleset
- All rule IDs in `rulePositions` must belong to the ruleset
- All positions must be unique
- CLEAR_QUEUE rule (if exists) must remain at position 0 (FR-033)

**Postconditions**:
- Rule positions updated
- Ruleset `updatedAt` updated

**Events Posted**: None (reorder is internal)

## Refill Operations

### refillQueue

```java
/**
 * Refills a queue according to its ruleset.
 *
 * @param queueId The ID of the queue to refill
 * @param clearQueue If true, clear existing episodes before refilling
 * @return Future<RefillResult> Result containing added episode count and any errors
 */
public static Future<RefillResult> refillQueue(long queueId, boolean clearQueue)
```

**Preconditions**:
- `queueId` must reference an existing queue
- Queue must have a ruleset with at least one rule (or clearQueue must be true)

**Postconditions**:
- If `clearQueue` is true, all existing episodes removed from queue (not marked as played/deleted, FR-038)
- Rules processed in order
- Episodes selected and added to queue via `DBWriter.addQueueItem()`
- Duplicate episodes prevented (FR-013)
- Partial fulfillment applied (FR-027)
- Invalid rules skipped (FR-028)
- Returns refill result with statistics

**Events Posted**:
- `QueueEvent.removed` for each episode removed (if clearQueue)
- `QueueEvent.added` for each episode added
- `QueueEvent.refilled` when refill completes

### RefillResult

```java
public class RefillResult {
    public final int episodesAdded;
    public final int episodesRemoved;
    public final int rulesProcessed;
    public final int rulesSkipped;
    public final List<String> errors; // Empty if no errors
}
```
