package de.danoeh.antennapod.storage.database.mapper;

import android.database.Cursor;
import android.database.CursorWrapper;
import androidx.annotation.NonNull;

import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.storage.database.PodDBAdapter;

/**
 * Converts a {@link Cursor} to a {@link QueueMetadata} object.
 *
 * Maps database columns from the QueueMetadata table to the domain model.
 * Follows the CursorWrapper pattern used throughout AntennaPod's database layer.
 */
public class QueueMetadataCursor extends CursorWrapper {

    private final int indexId;
    private final int indexName;
    private final int indexColor;
    private final int indexCreatedAt;
    private final int indexSortOrder;
    private final int indexCurrentlyPlayingFeedMediaId;
    private final int indexCurrentlyPlayingFeedId;

    /**
     * Create a QueueMetadataCursor wrapping the given cursor.
     *
     * @param cursor Database cursor over QueueMetadata table
     * @throws IllegalArgumentException if required columns are missing
     */
    public QueueMetadataCursor(@NonNull Cursor cursor) {
        super(cursor);
        this.indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.QUEUE_METADATA_ID);
        this.indexName = cursor.getColumnIndexOrThrow(PodDBAdapter.QUEUE_METADATA_NAME);
        this.indexColor = cursor.getColumnIndexOrThrow(PodDBAdapter.QUEUE_METADATA_COLOR);
        this.indexCreatedAt = cursor.getColumnIndexOrThrow(PodDBAdapter.QUEUE_METADATA_CREATED_AT);
        this.indexSortOrder = cursor.getColumnIndexOrThrow(PodDBAdapter.QUEUE_METADATA_SORT_ORDER);
        this.indexCurrentlyPlayingFeedMediaId = cursor.getColumnIndexOrThrow(
                PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEEDMEDIA_ID);
        this.indexCurrentlyPlayingFeedId = cursor.getColumnIndexOrThrow(
                PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEED_ID);
    }

    /**
     * Create a {@link QueueMetadata} instance from the current database row.
     *
     * @return QueueMetadata object constructed from cursor data
     */
    @NonNull
    public QueueMetadata getQueueMetadata() {
        return new QueueMetadata(
                getLong(indexId),
                getString(indexName),
                getInt(indexColor),
                getLong(indexCreatedAt),
                getInt(indexSortOrder),
                getLong(indexCurrentlyPlayingFeedMediaId),
                getLong(indexCurrentlyPlayingFeedId)
        );
    }
}
