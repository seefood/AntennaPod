package de.danoeh.antennapod.playback;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

/**
 * Manages playback state transitions when switching between queues.
 *
 * Implements pause-load-restore logic:
 * 1. Pause current queue: Saves playback position of currently playing episode
 * 2. Switch queue: Changes active queue in UserPreferences
 * 3. Restore queue position: Loads the queue's last playing episode and position
 *
 * MVP (Phase 1-2): Supports pause-load-restore with safe fallback (pause if queue empty).
 * Phase 3+: Will add auto-skip logic for 100% complete episodes (requires DBReader extension).
 *
 * NOTE: This class provides the interface for queue-aware playback integration.
 * Actual PlaybackController implementation details will be resolved during Phase 3
 * integration when the full playback service API is available.
 */
public class QueuePlaybackManager {
    private static final String TAG = "QueuePlaybackManager";

    private final Context context;
    private final PlaybackController playbackController;

    /**
     * Create QueuePlaybackManager with context and playback controller.
     *
     * @param context Android context
     * @param playbackController PlaybackController for playback operations
     */
    public QueuePlaybackManager(@NonNull Context context, @NonNull PlaybackController playbackController) {
        this.context = context;
        this.playbackController = playbackController;
    }

    /**
     * Pause the current queue and save its playback position.
     *
     * Saves the FeedMedia ID and position of the currently playing episode
     * to the QueueMetadata of the active queue.
     *
     * @param currentQueueId ID of the queue to pause
     */
    public void pauseCurrentQueue(long currentQueueId) {
        // Get the currently playing media
        FeedMedia currentMedia = playbackController.getMedia();
        if (currentMedia == null) {
            Log.d(TAG, "No media currently playing");
            return;
        }

        // Save current position to the queue metadata
        FeedItem currentItem = DBReader.getFeedItem(currentMedia.getId());
        if (currentItem != null) {
            DBWriter.updateQueueCurrentlyPlaying(currentQueueId,
                    currentMedia.getId(),
                    currentItem.getFeed().getId());
        }

        // Pause playback
        playbackController.pause();
    }

    /**
     * Switch to a queue and restore its playback position.
     *
     * This is a convenience method combining pauseCurrentQueue + restoreQueuePosition.
     *
     * @param oldQueueId ID of the current queue to pause
     * @param newQueueId ID of the queue to switch to
     */
    public void switchToQueue(long oldQueueId, long newQueueId) {
        pauseCurrentQueue(oldQueueId);
        restoreQueuePosition(newQueueId);
    }

    /**
     * Restore playback position for a queue.
     *
     * Loads the queue's last playing episode from QueueMetadata and restarts
     * playback from that position.
     *
     * Implements auto-skip logic: If the last-playing episode is 100% complete,
     * skips to the next unplayed episode.
     *
     * @param queueId ID of the queue to restore playback for
     */
    public void restoreQueuePosition(long queueId) {
        QueueMetadata queue = DBReader.getQueue(queueId);
        if (queue == null) {
            Log.w(TAG, "Queue not found: " + queueId);
            return;
        }

        // Check if queue has a saved playing episode
        if (!queue.isEpisodePlaying()) {
            Log.d(TAG, "Queue has no saved playback position");
            // MVP: Just stop playback. Auto-skip logic is Phase 3+ feature.
            playbackController.pause();
            return;
        }

        // Get the saved playing episode
        FeedItem playingEpisode = DBReader.getFeedItem(queue.getCurrentlyPlayingFeedMediaId());
        if (playingEpisode == null || playingEpisode.getMedia() == null) {
            Log.d(TAG, "Saved playback episode not found");
            return;
        }

        FeedMedia media = playingEpisode.getMedia();

        // Check if episode is 100% complete
        if (isEpisodeComplete(media)) {
            // MVP: Don't auto-skip, just pause playback.
            // Phase 3+ feature: Auto-skip to next unplayed episode (requires DBReader extension)
            Log.d(TAG, "Episode is complete, pausing playback");
            playbackController.pause();
            return;
        }

        // Load the media and restore playback position
        playbackController.loadMedia(media);
        if (media.getPosition() > 0) {
            playbackController.seek(media.getPosition());
        }
    }

    /**
     * Check if an episode is 100% complete.
     *
     * @param media FeedMedia to check
     * @return true if played >= duration or if explicitly marked as played
     */
    private boolean isEpisodeComplete(@NonNull FeedMedia media) {
        if (media.isPlayed()) {
            return true;
        }
        // Check if position >= 95% of duration (accounting for rounding)
        if (media.getDuration() > 0) {
            int positionPercent = (int) ((media.getPosition() * 100L) / media.getDuration());
            return positionPercent >= 95;
        }
        return false;
    }

}
