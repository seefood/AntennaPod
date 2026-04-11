package de.danoeh.antennapod.model;

/**
 * Result of a queue refill operation. Immutable.
 *
 * <p>{@code queueWasEmpty} captures whether the queue was empty before the write phase began,
 * enabling the caller to start playback after a successful Append (FR-017).
 */
public final class RefillResult {
    private final int episodesAdded;
    private final int rulesProcessed;
    private final int rulesSkipped;
    private final boolean success;
    private final boolean queueWasEmpty;

    public RefillResult(int episodesAdded, int rulesProcessed, int rulesSkipped,
                        boolean success, boolean queueWasEmpty) {
        this.episodesAdded = episodesAdded;
        this.rulesProcessed = rulesProcessed;
        this.rulesSkipped = rulesSkipped;
        this.success = success;
        this.queueWasEmpty = queueWasEmpty;
    }

    public int getEpisodesAdded() {
        return episodesAdded;
    }

    public int getRulesProcessed() {
        return rulesProcessed;
    }

    public int getRulesSkipped() {
        return rulesSkipped;
    }

    public boolean isSuccess() {
        return success;
    }

    /**
     * True if the queue was empty before the refill write phase began.
     * When true and the operation is an Append that added ≥1 episode,
     * the caller should start playback from the first newly added episode (FR-017).
     */
    public boolean wasQueueEmpty() {
        return queueWasEmpty;
    }
}
