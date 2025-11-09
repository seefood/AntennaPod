package de.danoeh.antennapod.model.feed;

import java.io.Serializable;

/**
 * A single rule that defines episode selection criteria.
 *
 * @author AntennaPod Team
 */
public class RefillRule implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum RuleType {
        CLEAR_QUEUE,
        ADD_EPISODES
    }

    public enum SelectionMethod {
        OLDEST,
        NEWEST,
        RANDOM
    }

    public enum SourceType {
        FEED,
        TAG,
        INBOX
    }

    private long id;
    private long rulesetId;
    private int position;
    private RuleType ruleType;
    private SelectionMethod selectionMethod;
    private Integer count;
    private SourceType sourceType;
    private String sourceId;
    private long createdAt;
    private long updatedAt;

    public RefillRule() {
        // Default constructor
    }

    public RefillRule(long id, long rulesetId, int position, RuleType ruleType,
                      SelectionMethod selectionMethod, Integer count,
                      SourceType sourceType, String sourceId,
                      long createdAt, long updatedAt) {
        this.id = id;
        this.rulesetId = rulesetId;
        this.position = position;
        this.ruleType = ruleType;
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

    public void setId(long id) {
        this.id = id;
    }

    public long getRulesetId() {
        return rulesetId;
    }

    public void setRulesetId(long rulesetId) {
        this.rulesetId = rulesetId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    public SelectionMethod getSelectionMethod() {
        return selectionMethod;
    }

    public void setSelectionMethod(SelectionMethod selectionMethod) {
        this.selectionMethod = selectionMethod;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
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
        RefillRule that = (RefillRule) o;
        return id == that.id && rulesetId == that.rulesetId && position == that.position;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "RefillRule{id=" + id + ", rulesetId=" + rulesetId
                + ", position=" + position + ", ruleType=" + ruleType
                + ", selectionMethod=" + selectionMethod + ", count=" + count
                + ", sourceType=" + sourceType + ", sourceId='" + sourceId + "'"
                + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "}";
    }
}
