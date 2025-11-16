package de.danoeh.antennapod.storage.database;

import android.util.Log;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.RefillRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Queue Refill Engine - Orchestrates the queue refill process.
 *
 * <p>Implements the business logic for refilling queues according to defined rules (T061-T067):
 * - T061: Class definition and initialization
 * - T062: Rule processing loop
 * - T063: Clear queue handling (FR-002, FR-038)
 * - T064: Episode selection with duplicate prevention (FR-013)
 * - T065: Partial fulfillment (FR-027)
 * - T066: Deleted feed/tag handling (FR-028)
 * - T067: Empty queue scenario (FR-029)
 */
public class QueueRefillEngine {
    private static final String TAG = "QueueRefillEngine";

    /**
     * Container for refill operation results.
     */
    public static class RefillOperation {
        public List<FeedItem> episodesToAdd;
        public boolean shouldClearQueue;
        public int rulesProcessed;
        public List<String> errors;

        public RefillOperation() {
            this.episodesToAdd = new ArrayList<>();
            this.shouldClearQueue = false;
            this.rulesProcessed = 0;
            this.errors = new ArrayList<>();
        }
    }

    /**
     * T061-T062: Process ruleset and generate episodes to add to queue.
     *
     * <p>Executes all rules in order and collects matching episodes.
     * Handles clear queue rules, duplicate prevention, and partial fulfillment.
     *
     * @param rules List of refill rules to process
     * @param currentQueueItems Current episodes in the queue (for duplicate prevention)
     * @return RefillOperation containing episodes to add and operation details
     */
    public static RefillOperation processRuleset(List<RefillRule> rules,
                                                   List<FeedItem> currentQueueItems) {
        Log.d(TAG, "Processing refill ruleset with " + rules.size() + " rules");

        RefillOperation operation = new RefillOperation();
        Set<Long> addedEpisodeIds = new HashSet<>();

        // Build set of current queue episode IDs for duplicate prevention (FR-013)
        Set<Long> currentQueueEpisodeIds = new HashSet<>();
        for (FeedItem item : currentQueueItems) {
            currentQueueEpisodeIds.add(item.getId());
        }

        // T062: Process each rule in order
        for (RefillRule rule : rules) {
            Log.d(TAG, "Processing rule: " + rule.getRuleType() + " - " + rule.getSourceType());

            // T063: Handle CLEAR_QUEUE rule
            if (rule.getRuleType() == RefillRule.RuleType.CLEAR_QUEUE) {
                operation.shouldClearQueue = true;
                currentQueueEpisodeIds.clear();  // Clear queue, so no duplicates to prevent
                Log.d(TAG, "Clear queue rule encountered");
                operation.rulesProcessed++;
                continue;
            }

            // T064: Get episodes matching this rule
            List<FeedItem> matchingEpisodes = DBReader.getEpisodesForRule(rule, null);

            if (matchingEpisodes == null || matchingEpisodes.isEmpty()) {
                // T066: Handle scenario where feed/tag no longer exists
                Log.d(TAG, "No episodes found for rule (feed/tag may have been deleted)");
                operation.rulesProcessed++;
                continue;
            }

            // T064: Add episodes without duplicates up to rule's count
            int addedCount = 0;
            for (FeedItem episode : matchingEpisodes) {
                // Skip if already in queue or already added in this refill (FR-013)
                if (currentQueueEpisodeIds.contains(episode.getId()) || addedEpisodeIds.contains(episode.getId())) {
                    Log.d(TAG, "Skipping duplicate episode: " + episode.getTitle());
                    continue;
                }

                operation.episodesToAdd.add(episode);
                addedEpisodeIds.add(episode.getId());
                currentQueueEpisodeIds.add(episode.getId());
                addedCount++;

                // T065: Stop when rule's count is reached (partial fulfillment)
                if (rule.getCount() != null && addedCount >= rule.getCount()) {
                    Log.d(TAG, "Rule count reached (" + addedCount + "/" + rule.getCount() + ")");
                    break;
                }
            }

            // T065: Log partial fulfillment if less than requested
            if (rule.getCount() != null && addedCount < rule.getCount()) {
                Log.d(TAG, "Partial fulfillment: requested " + rule.getCount() + ", got " + addedCount);
            }

            operation.rulesProcessed++;
        }

        // T067: Handle empty queue scenario
        if (operation.episodesToAdd.isEmpty()) {
            Log.d(TAG, "No episodes found matching any rules - queue will be empty after refill");
        }

        Log.d(TAG, "Refill complete: " + operation.episodesToAdd.size()
                + " episodes to add, clearQueue=" + operation.shouldClearQueue);

        return operation;
    }
}
