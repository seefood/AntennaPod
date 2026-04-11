package de.danoeh.antennapod.model.feed;

import androidx.annotation.Nullable;

/**
 * A single episode-selection rule within a {@link QueueRuleset}. Immutable.
 */
public final class RefillRule {

    public enum SelectionMethod {
        OLDEST, NEWEST, RANDOM
    }

    public enum SourceType {
        FEED, TAG, INBOX
    }

    private final long id;
    private final long rulesetId;
    private final int position;
    private final SelectionMethod selectionMethod;
    private final int count;
    private final SourceType sourceType;
    @Nullable
    private final String sourceId;
    private final long createdAt;
    private final long updatedAt;

    public RefillRule(long id, long rulesetId, int position, SelectionMethod selectionMethod,
                      int count, SourceType sourceType, @Nullable String sourceId,
                      long createdAt, long updatedAt) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0, was: " + count);
        }
        if ((sourceType == SourceType.FEED || sourceType == SourceType.TAG)
                && (sourceId == null || sourceId.isEmpty())) {
            throw new IllegalArgumentException("sourceId must be non-null and non-empty for "
                    + sourceType + " source type");
        }
        if (sourceType == SourceType.INBOX && sourceId != null) {
            throw new IllegalArgumentException("sourceId must be null for INBOX source type");
        }
        this.id = id;
        this.rulesetId = rulesetId;
        this.position = position;
        this.selectionMethod = selectionMethod;
        this.count = count;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public long getRulesetId() {
        return rulesetId;
    }

    public int getPosition() {
        return position;
    }

    public SelectionMethod getSelectionMethod() {
        return selectionMethod;
    }

    public int getCount() {
        return count;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    @Nullable
    public String getSourceId() {
        return sourceId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
