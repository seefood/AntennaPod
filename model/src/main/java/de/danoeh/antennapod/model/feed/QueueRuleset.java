package de.danoeh.antennapod.model.feed;

/**
 * Represents a ruleset associated with a playback queue.
 * Each queue has at most one ruleset. Immutable.
 */
public final class QueueRuleset {
    private final long id;
    private final long queueId;
    private final long createdAt;
    private final long updatedAt;

    public QueueRuleset(long id, long queueId, long createdAt, long updatedAt) {
        this.id = id;
        this.queueId = queueId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public long getQueueId() {
        return queueId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
