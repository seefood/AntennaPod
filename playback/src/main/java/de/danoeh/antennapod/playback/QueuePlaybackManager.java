package de.danoeh.antennapod.playback;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

/**
 * Manages playback state for queue-aware playback.
 *
 * This class provides data management for queue playback state persistence.
 * It coordinates with the database layer to save/restore playback position
 * when switching between queues.
 *
 * IMPORTANT: The actual playback integration (loading media, seeking, playback control)
 * is implemented in PlaybackService and PlaybackController. This class handles the
 * data layer only.
 *
 * Phase 3 Integration: During Phase 3, PlaybackService will coordinate with this
 * class to manage queue-aware playback transitions.
 */
public class QueuePlaybackManager {
    private static final String TAG = "QueuePlaybackManager";

    private final Context context;

    /**
     * Create QueuePlaybackManager with application context.
     *
     * @param context Android application context
     */
    public QueuePlaybackManager(@NonNull Context context) {
        this.context = context;
    }

    /**
     * Save the current playback state for a queue.
     *
     * Records the currently playing media and feed IDs to the QueueMetadata
     * so the playback position can be restored when the queue becomes active.
     *
     * @param queueId ID of the queue to save state for
     * @param currentMediaId ID of the currently playing media (-1 if none playing)
     * @param currentFeedId ID of the feed of the currently playing media (-1 if none)
     */
    public void saveQueuePlaybackState(long queueId, long currentMediaId, long currentFeedId) {
        if (currentMediaId > 0 && currentFeedId > 0) {
            DBWriter.updateQueueCurrentlyPlaying(queueId, currentMediaId, currentFeedId);
            Log.d(TAG, "Saved playback state for queue " + queueId + ": media=" + currentMediaId);
        }
    }

    /**
     * Get the saved playback state for a queue.
     *
     * Retrieves the QueueMetadata which contains information about the last
     * playing episode and can be used to restore playback position.
     *
     * @param queueId ID of the queue
     * @return QueueMetadata with saved playback state, or null if not found
     */
    public QueueMetadata getQueuePlaybackState(long queueId) {
        return DBReader.getQueue(queueId);
    }

    /**
     * Check if a media/episode is 100% complete.
     *
     * @param media FeedMedia to check
     * @return true if played >= 95% of duration or explicitly marked as played
     */
    public boolean isEpisodeComplete(@NonNull FeedMedia media) {
        if (media.isPlayed()) {
            return true;
        }
        // Check if position >= 95% of duration (accounting for playback rounding)
        if (media.getDuration() > 0) {
            int positionPercent = (int) ((media.getPosition() * 100L) / media.getDuration());
            return positionPercent >= 95;
        }
        return false;
    }
}
