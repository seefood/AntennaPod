# DBReader Contracts: Smart Queues

**Feature**: Smart Queues
**Date**: 2025-11-08
**Phase**: 1 - Design & Contracts

## Ruleset Read Operations

### getQueueRuleset

```java
/**
 * Gets the ruleset for a queue.
 *
 * @param queueId The ID of the queue
 * @return QueueRuleset The ruleset, or null if no ruleset exists
 */
public static QueueRuleset getQueueRuleset(long queueId)
```

**Returns**: Ruleset for the queue, or null if no ruleset exists

### hasQueueRuleset

```java
/**
 * Checks if a queue has a ruleset.
 *
 * @param queueId The ID of the queue
 * @return boolean True if ruleset exists, false otherwise
 */
public static boolean hasQueueRuleset(long queueId)
```

**Returns**: True if queue has a ruleset, false otherwise

## Rule Read Operations

### getRefillRules

```java
/**
 * Gets all rules for a ruleset, ordered by position.
 *
 * @param rulesetId The ID of the ruleset
 * @return List<RefillRule> List of rules ordered by position (ascending)
 */
public static List<RefillRule> getRefillRules(long rulesetId)
```

**Returns**: List of rules ordered by position (ascending), empty list if no rules

### getRefillRule

```java
/**
 * Gets a specific rule by ID.
 *
 * @param ruleId The ID of the rule
 * @return RefillRule The rule, or null if not found
 */
public static RefillRule getRefillRule(long ruleId)
```

**Returns**: Rule with the given ID, or null if not found

### hasClearQueueRule

```java
/**
 * Checks if a ruleset has a CLEAR_QUEUE rule.
 *
 * @param rulesetId The ID of the ruleset
 * @return boolean True if CLEAR_QUEUE rule exists, false otherwise
 */
public static boolean hasClearQueueRule(long rulesetId)
```

**Returns**: True if ruleset has a CLEAR_QUEUE rule, false otherwise

### getClearQueueRule

```java
/**
 * Gets the CLEAR_QUEUE rule for a ruleset, if it exists.
 *
 * @param rulesetId The ID of the ruleset
 * @return RefillRule The CLEAR_QUEUE rule, or null if not found
 */
public static RefillRule getClearQueueRule(long rulesetId)
```

**Returns**: CLEAR_QUEUE rule for the ruleset, or null if not found

## Episode Selection Operations

### getEpisodesForRule

```java
/**
 * Gets episodes matching a rule's criteria.
 *
 * @param rule The rule to apply
 * @return List<FeedItem> List of episodes matching the rule (may be empty)
 */
public static List<FeedItem> getEpisodesForRule(RefillRule rule)
```

**Preconditions**:
- `rule` must be of type ADD_EPISODES
- `rule` must have valid sourceType and sourceId

**Returns**: List of episodes matching the rule's criteria:
- For FEED: Episodes from the specified feed
- For TAG: Episodes with the specified tag
- For INBOX: Episodes in the inbox
- Excludes episodes that are 100% played (FR-012)
- Excludes episodes already in any queue (FR-013)

**Selection Method**:
- OLDEST: Episodes ordered by publication date (oldest first)
- NEWEST: Episodes ordered by publication date (newest first)
- RANDOM: Episodes in random order

**Note**: This method reuses existing `DBReader.getFeedItemList()`, `FeedItemFilter`, and `DBReader.getInboxItemList()` methods (FR-024).
