package de.danoeh.antennapod.model.feed;

import java.io.Serializable;

/**
 * A collection of rules associated with a specific queue that defines how episodes are selected during refill.
 *
 * @author AntennaPod Team
 */
public class QueueRuleset implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private long queueId;
    private long createdAt;
    private long updatedAt;

    public QueueRuleset() {
        // Default constructor
    }

    public QueueRuleset(long id, long queueId, long createdAt, long updatedAt) {
        this.id = id;
        this.queueId = queueId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getQueueId() {
        return queueId;
    }

    public void setQueueId(long queueId) {
        this.queueId = queueId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QueueRuleset that = (QueueRuleset) o;
        return id == that.id && queueId == that.queueId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "QueueRuleset{id=" + id + ", queueId=" + queueId
                + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "}";
    }
}
