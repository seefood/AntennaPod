package de.danoeh.antennapod.storage.database.mapper;

import android.database.Cursor;
import android.database.CursorWrapper;
import androidx.annotation.NonNull;

import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.PodDBAdapter;

/**
 * Converts a {@link Cursor} to a {@link RefillRule} object.
 */
public class RefillRuleCursor extends CursorWrapper {

    private final int indexId;
    private final int indexRulesetId;
    private final int indexPosition;
    private final int indexRuleType;
    private final int indexSelectionMethod;
    private final int indexCount;
    private final int indexSourceType;
    private final int indexSourceId;
    private final int indexCreatedAt;
    private final int indexUpdatedAt;

    public RefillRuleCursor(@NonNull Cursor cursor) {
        super(cursor);
        this.indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_ID);
        this.indexRulesetId = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_RULESET_ID);
        this.indexPosition = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_POSITION);
        this.indexRuleType = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_RULE_TYPE);
        this.indexSelectionMethod = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_SELECTION_METHOD);
        this.indexCount = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_COUNT);
        this.indexSourceType = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_SOURCE_TYPE);
        this.indexSourceId = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_SOURCE_ID);
        this.indexCreatedAt = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_CREATED_AT);
        this.indexUpdatedAt = cursor.getColumnIndexOrThrow(PodDBAdapter.REFILL_RULE_UPDATED_AT);
    }

    @NonNull
    public RefillRule getRefillRule() {
        String ruleTypeStr = getString(indexRuleType);
        final RefillRule.RuleType ruleType = RefillRule.RuleType.valueOf(ruleTypeStr);

        RefillRule.SelectionMethod selectionMethod = null;
        if (!isNull(indexSelectionMethod)) {
            selectionMethod = RefillRule.SelectionMethod.valueOf(getString(indexSelectionMethod));
        }

        Integer count = null;
        if (!isNull(indexCount)) {
            count = getInt(indexCount);
        }

        RefillRule.SourceType sourceType = null;
        if (!isNull(indexSourceType)) {
            sourceType = RefillRule.SourceType.valueOf(getString(indexSourceType));
        }

        String sourceId = null;
        if (!isNull(indexSourceId)) {
            sourceId = getString(indexSourceId);
        }

        return new RefillRule(
                getLong(indexId),
                getLong(indexRulesetId),
                getInt(indexPosition),
                ruleType,
                selectionMethod,
                count,
                sourceType,
                sourceId,
                getLong(indexCreatedAt),
                getLong(indexUpdatedAt)
        );
    }
}
