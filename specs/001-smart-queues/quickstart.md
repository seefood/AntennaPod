# Quickstart: Smart Queues

**Feature**: Smart Queues
**Date**: 2025-11-08
**Phase**: 1 - Design & Contracts

## Overview

Smart Queues enables automatic refilling of podcast queues based on user-defined rules. This guide provides a quick overview of how to use the feature once implemented.

## User Workflow

### 1. Configure Rules for a Queue

1. Open a queue in the app
2. Tap the "Edit Rules" button (or similar UI element)
3. Add rules to define how episodes should be selected:
   - **Clear queue**: Removes all existing episodes before refilling (optional, first rule only)
   - **Add episodes**: Selects episodes from a feed, tag, or inbox using oldest/newest/random method
4. Reorder rules as needed (rules execute in order)
5. Save the ruleset

### 2. Manual Refill

1. Open a queue with rules configured
2. Tap the "Refill" button
3. Queue is automatically populated according to the rules
4. Playback starts from the first episode

### 3. Automatic Refill

1. Configure rules for a queue (see step 1)
2. Play episodes from the queue normally
3. When the queue runs out of episodes, it automatically refills according to the rules
4. Playback continues seamlessly

## Rule Types

### Clear Queue Rule

- **Purpose**: Removes all existing episodes from the queue before applying other rules
- **Position**: Must be first rule (automatically moved if added elsewhere)
- **Limits**: Only one per ruleset
- **Behavior**: Episodes are removed but not marked as played or deleted

### Add Episodes Rule

- **Purpose**: Selects and adds episodes to the queue
- **Parameters**:
  - **Count**: Number of episodes to add (e.g., 5, 10)
  - **Source**: Feed, Tag, or Inbox
  - **Selection Method**: Oldest, Newest, or Random
- **Behavior**:
  - Only selects episodes that are not 100% played (partially played episodes are eligible)
  - Prevents duplicates (episode won't be added twice even if it matches multiple rules)
  - Partial fulfillment: If fewer episodes available than requested, adds all available

## Example Rulesets

### Example 1: News First, Then Stories

1. Clear queue
2. Add 5 newest episodes tagged "news"
3. Add 3 oldest episodes from feed "Sequential Story Podcast"

**Result**: Queue contains 5 news episodes first, then 3 story episodes, in that order.

### Example 2: Random Mix

1. Clear queue
2. Add 10 random episodes from inbox
3. Add 5 random episodes tagged "favorites"

**Result**: Queue contains 10 random inbox episodes, then 5 random favorite episodes (no duplicates).

### Example 3: Feed Priority

1. Clear queue
2. Add 3 newest episodes from feed "Daily News"
3. Add 2 newest episodes from feed "Weekly Summary"
4. Add 5 random episodes tagged "entertainment"

**Result**: Queue contains daily news first, then weekly summary, then entertainment episodes.

## Edge Cases Handled

- **Insufficient episodes**: If a rule requests 10 episodes but only 5 are available, all 5 are added and refill continues
- **Deleted feed/tag**: If a rule references a feed or tag that no longer exists, the rule is skipped silently
- **No matching episodes**: If no episodes match any rule, refill completes with an empty queue
- **Duplicate prevention**: If an episode matches multiple rules, it's only added once (first rule that matches it)

## Technical Implementation Notes

- Rules are stored in the database (new tables: QueueRuleset, RefillRule)
- Refill operations use existing queue management code (DBWriter.addQueueItem, etc.)
- Episode selection reuses existing filtering mechanisms (FeedItemFilter, tag filtering)
- Automatic refill is triggered via PlaybackService events
- All operations follow AntennaPod's event-driven architecture (EventBus)
