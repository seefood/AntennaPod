package de.danoeh.antennapod.storage.database.mapper;

import android.database.Cursor;
import android.database.CursorWrapper;
import androidx.annotation.NonNull;

import de.danoeh.antennapod.model.feed.QueueRuleset;
import de.danoeh.antennapod.storage.database.PodDBAdapter;

/**
 * Converts a {@link Cursor} to a {@link QueueRuleset} object.
 */
public class QueueRulesetCursor extends CursorWrapper {

    private final int indexId;
    private final int indexQueueId;
    private final int indexCreatedAt;
    private final int indexUpdatedAt;

    public QueueRulesetCursor(@NonNull Cursor cursor) {
        super(cursor);
        this.indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.QUEUE_RULESET_ID);
        this.indexQueueId = cursor.getColumnIndexOrThrow(PodDBAdapter.QUEUE_RULESET_QUEUE_ID);
        this.indexCreatedAt = cursor.getColumnIndexOrThrow(PodDBAdapter.QUEUE_RULESET_CREATED_AT);
        this.indexUpdatedAt = cursor.getColumnIndexOrThrow(PodDBAdapter.QUEUE_RULESET_UPDATED_AT);
    }

    @NonNull
    public QueueRuleset getQueueRuleset() {
        return new QueueRuleset(
                getLong(indexId),
                getLong(indexQueueId),
                getLong(indexCreatedAt),
                getLong(indexUpdatedAt)
        );
    }
}
