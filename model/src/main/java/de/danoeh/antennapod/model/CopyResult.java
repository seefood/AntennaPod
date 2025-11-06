package de.danoeh.antennapod.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a queue item copy operation.
 *
 * <p>This class aggregates the outcome of copying one or more episodes to another queue,
 * including success counts and details about any skipped items (e.g., duplicates).</p>
 */
public class CopyResult {
    /** Number of episodes successfully copied */
    private final int copiedCount;

    /** Number of episodes that could not be copied (skipped) */
    private final int skippedCount;

    /** List of feed item IDs that were not copied */
    private final List<Long> skippedItemIds;

    /** Map of skipped item IDs to their skip reasons */
    private final Map<Long, String> skipReasons;

    /**
     * Creates a CopyResult with the specified counts and details.
     *
     * @param copiedCount The number of episodes successfully copied
     * @param skippedCount The number of episodes that could not be copied
     * @param skippedItemIds List of feed item IDs that were skipped
     * @param skipReasons Map of skipped item IDs to their skip reasons
     */
    public CopyResult(int copiedCount, int skippedCount, List<Long> skippedItemIds, Map<Long, String> skipReasons) {
        this.copiedCount = copiedCount;
        this.skippedCount = skippedCount;
        this.skippedItemIds = skippedItemIds != null ? new ArrayList<>(skippedItemIds) : new ArrayList<>();
        this.skipReasons = skipReasons != null ? new HashMap<>(skipReasons) : new HashMap<>();
    }

    /**
     * Returns the number of episodes successfully copied.
     *
     * @return the count of copied episodes
     */
    public int getCopiedCount() {
        return copiedCount;
    }

    /**
     * Returns the number of episodes that could not be copied.
     *
     * @return the count of skipped episodes
     */
    public int getSkippedCount() {
        return skippedCount;
    }

    /**
     * Returns the list of feed item IDs that were not copied.
     *
     * @return list of skipped item IDs
     */
    public List<Long> getSkippedItemIds() {
        return new ArrayList<>(skippedItemIds);
    }

    /**
     * Returns the map of skipped item IDs to their skip reasons.
     *
     * @return map of skip reasons
     */
    public Map<Long, String> getSkipReasons() {
        return new HashMap<>(skipReasons);
    }
}
