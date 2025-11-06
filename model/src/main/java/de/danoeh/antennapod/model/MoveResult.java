package de.danoeh.antennapod.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a queue item move operation.
 *
 * <p>This class aggregates the outcome of moving one or more episodes between queues,
 * including success counts and details about any skipped items.</p>
 */
public class MoveResult {
    /** Number of episodes successfully moved */
    private final int movedCount;

    /** Number of episodes that could not be moved (skipped) */
    private final int skippedCount;

    /** List of feed item IDs that were not moved */
    private final List<Long> skippedItemIds;

    /** Map of skipped item IDs to their skip reasons */
    private final Map<Long, String> skipReasons;

    /**
     * Creates a MoveResult with the specified counts and details.
     *
     * @param movedCount The number of episodes successfully moved
     * @param skippedCount The number of episodes that could not be moved
     * @param skippedItemIds List of feed item IDs that were skipped
     * @param skipReasons Map of skipped item IDs to their skip reasons
     */
    public MoveResult(int movedCount, int skippedCount, List<Long> skippedItemIds, Map<Long, String> skipReasons) {
        this.movedCount = movedCount;
        this.skippedCount = skippedCount;
        this.skippedItemIds = skippedItemIds != null ? new ArrayList<>(skippedItemIds) : new ArrayList<>();
        this.skipReasons = skipReasons != null ? new HashMap<>(skipReasons) : new HashMap<>();
    }

    /**
     * Returns the number of episodes successfully moved.
     *
     * @return the count of moved episodes
     */
    public int getMovedCount() {
        return movedCount;
    }

    /**
     * Returns the number of episodes that could not be moved.
     *
     * @return the count of skipped episodes
     */
    public int getSkippedCount() {
        return skippedCount;
    }

    /**
     * Returns the list of feed item IDs that were not moved.
     *
     * @return immutable list of skipped item IDs
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
