# DBReader.getEpisodesForRule() Analysis

## Critical Finding: Root Cause of Refill Engine Bug

After analyzing the implementation, I've identified the core issue preventing the refill engine from advancing past duplicate episodes.

## 1. Method Signature & Parameters

**Location:** `/storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java`, lines 1127-1223

```java
@NonNull
public static List<FeedItem> getEpisodesForRule(@NonNull RefillRule rule,
                                                  @Nullable List<Long> excludeQueueIds)
```

### Parameters:
- **`rule`** - The refill rule defining which episodes to fetch
- **`excludeQueueIds`** - `@Nullable` parameter. When `null`, NO FILTERING occurs. When a `List<Long>`, episodes with IDs in this list are excluded from results

## 2. Critical Bug: The Second Parameter Is Ignored

**Line 82 in QueueRefillEngine.java:**
```java
List<FeedItem> matchingEpisodes = DBReader.getEpisodesForRule(rule, null);
                                                                      ^^^^
```

**THE BUG:** The `QueueRefillEngine` calls `getEpisodesForRule()` with `null` for the second parameter.

### Why This Is Critical:

In `getEpisodesForRule()`, lines 1195-1210, the duplicate filtering only happens when `excludeQueueIds` is NOT null:

```java
// Filter out episodes already in queue (duplicate prevention)
if (excludeQueueIds != null && !excludeQueueIds.isEmpty()) {
    List<FeedItem> filtered = new ArrayList<>();
    for (FeedItem item : episodes) {
        boolean inQueue = false;
        for (Long queueItemId : excludeQueueIds) {
            if (item.getId() == queueItemId) {
                inQueue = true;
                break;
            }
        }
        if (!inQueue) {
            filtered.add(item);
        }
    }
    episodes = filtered;
}
```

**Since `null` is passed, this entire filtering block is skipped.**

The filtering that DOES occur is in `QueueRefillEngine.processRuleset()` (lines 93-98):

```java
for (FeedItem episode : matchingEpisodes) {
    // Skip if already in queue or already added in this refill (FR-013)
    if (currentQueueEpisodeIds.contains(episode.getId()) ||
        addedEpisodeIds.contains(episode.getId())) {
        Log.d(TAG, "Skipping duplicate episode: " + episode.getTitle());
        continue;  // <-- SKIPPED, but NOT ADVANCED
    }
```

## 3. How the Method Applies Limits

**Line 1156-1160 (FEED source type):**
```java
int requestedCount = rule.getCount() != null ? rule.getCount() : 0;
// Get more episodes to account for items already in queue
int fetchCount = excludeQueueIds != null && !excludeQueueIds.isEmpty()
        ? requestedCount + excludeQueueIds.size() : requestedCount;
episodes = getFeedItemList(feed, filter, sortOrder, 0, fetchCount);
```

**PROBLEM:** When `excludeQueueIds` is `null`, `fetchCount = requestedCount` (no buffer).

**Line 1170 (TAG source type):**
```java
episodes = getEpisodes(0, rule.getCount() * 2, tagFilter, tagSortOrder);
```

**Line 1187-1188 (INBOX source type):**
```java
int inboxFetchCount = excludeQueueIds != null && !excludeQueueIds.isEmpty()
        ? inboxRequestedCount + excludeQueueIds.size() : inboxRequestedCount;
episodes = getEpisodes(0, inboxFetchCount, inboxFilter, inboxSortOrder);
```

**Line 1213-1215 (Final limit application):**
```java
// Limit to count (partial fulfillment handled by caller)
if (rule.getCount() != null && episodes.size() > rule.getCount()) {
    episodes = episodes.subList(0, rule.getCount());
}
```

## 4. Return Behavior: YES, Returns Up to Limit Only

The method returns **up to `rule.getCount()` episodes** after filtering.

However, the problem is:

1. It requests episodes using `requestedCount` (NOT accounting for duplicates)
2. It never filters out already-queued episodes (because `null` is passed)
3. It returns `rule.getCount()` episodes, which may include duplicates
4. The refill engine skips duplicates but doesn't advance through the returned list

**Example scenario with 5-episode limit:**
- 10 episodes available
- 7 already in queue
- `getEpisodesForRule()` with `null` returns episodes 1-5 (in order)
- Episodes 1-5 are ALL in currentQueueEpisodeIds
- All 5 are skipped (continue statement)
- Result: 0 episodes added, but 5 were fetched and discarded

## 5. Query Ordering

**Lines 1151-1152 (FEED):**
```java
SortOrder sortOrder = getSortOrderForSelectionMethod(rule.getSelectionMethod());
episodes = getFeedItemList(feed, filter, sortOrder, 0, fetchCount);
```

**Lines 1231-1244 show sort order mapping:**
- `SelectionMethod.OLDEST` → `SortOrder.DATE_OLD_NEW` (oldest first)
- `SelectionMethod.NEWEST` → `SortOrder.DATE_NEW_OLD` (newest first)  
- `SelectionMethod.RANDOM` → `SortOrder.RANDOM` (random order)
- Default → `SortOrder.DATE_NEW_OLD` (newest first)

**SAME for TAG and INBOX source types**

## Root Cause Summary

The refill engine fails to advance past duplicates because:

1. **`getEpisodesForRule()` is called with `null`** (line 82 in QueueRefillEngine)
2. This means **duplicate filtering in DBReader is never applied**
3. The method returns **only `rule.getCount()` episodes** (typically 5)
4. If all returned episodes are duplicates, **all are skipped** with no fallback
5. The engine **never requests additional episodes** to compensate for skipped duplicates

### The Fix Requires Two Changes:

**1. In QueueRefillEngine.processRuleset() line 82:**
```java
// BEFORE:
List<FeedItem> matchingEpisodes = DBReader.getEpisodesForRule(rule, null);

// AFTER (must collect queue IDs first):
List<Long> currentQueueIds = new ArrayList<>(currentQueueEpisodeIds);
List<FeedItem> matchingEpisodes = DBReader.getEpisodesForRule(rule, currentQueueIds);
```

**2. In QueueRefillEngine.processRuleset() lines 93-98:**
Since `getEpisodesForRule()` would already filter duplicates, the loop should:
- Trust that returned episodes are not duplicates
- Only filter against `addedEpisodeIds` (episodes added in THIS refill)
- Possibly optimize or simplify the duplicate check

**OR modify DBReader to request a buffer when duplicates are likely:**

In `getEpisodesForRule()` line 1158-1159, increase the fetch count proportionally:
```java
int fetchCount = excludeQueueIds != null && !excludeQueueIds.isEmpty()
        ? requestedCount + (int)(excludeQueueIds.size() * 1.5)  // 1.5x buffer
        : requestedCount + 10;  // minimum buffer
```

This would ensure enough fresh episodes are fetched to compensate for the ones skipped due to being already in queue.
