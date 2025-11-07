package de.danoeh.antennapod.storage.database;

import android.app.backup.BackupManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import android.view.KeyEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.google.common.util.concurrent.Futures;
import de.danoeh.antennapod.event.DownloadLogEvent;

import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import de.danoeh.antennapod.event.FavoritesEvent;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.event.FeedEvent;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.CopyResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.QueueMetadata;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.MoveResult;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;

/**
 * Provides methods for writing data to AntennaPod's database.
 * In general, DBWriter-methods will be executed on an internal ExecutorService.
 * Some methods return a Future-object which the caller can use for waiting for the method's completion. The returned Future's
 * will NOT contain any results.
 */
public class DBWriter {

    private static final String TAG = "DBWriter";

    private static final ExecutorService dbExec;

    static {
        dbExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("DatabaseExecutor");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    private DBWriter() {
    }

    /**
     * Wait until all threads are finished to avoid the "Illegal connection pointer" error of
     * Robolectric. Call this method only for unit tests.
     */
    public static void tearDownTests() {
        // dbExec is single-threaded FIFO, so if a newly submitted task runs, all previous tasks must have finished
        final Semaphore available = new Semaphore(1, true);
        try {
            available.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        dbExec.submit(() -> available.release());
        try {
            available.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes a downloaded FeedMedia file from the storage device.
     *
     * @param context A context that is used for opening a database connection.
     */
    public static Future<?> deleteFeedMediaOfItem(@NonNull final Context context,
                                                  final FeedMedia media) {
        return runOnDbThread(() -> {
            if (media == null) {
                return;
            }
            boolean result = deleteFeedMediaSynchronous(context, media);
            if (result && UserPreferences.shouldDeleteRemoveFromQueue()) {
                DBWriter.removeQueueItemSynchronous(context, false, media.getItemId());
            }
        });
    }

    private static boolean deleteFeedMediaSynchronous(@NonNull Context context, @NonNull FeedMedia media) {
        Log.i(TAG, String.format(Locale.US, "Requested to delete FeedMedia [id=%d, title=%s, downloaded=%s",
                media.getId(), media.getEpisodeTitle(), media.isDownloaded()));
        boolean localDelete = false;
        if (media.getLocalFileUrl() != null && media.getLocalFileUrl().startsWith("content://")) {
            // Local feed
            DocumentFile documentFile = DocumentFile.fromSingleUri(context, Uri.parse(media.getLocalFileUrl()));
            if (documentFile == null || !documentFile.exists() || !documentFile.delete()) {
                Log.d(TAG, "Deletion of local file failed.");
            }
            media.setLocalFileUrl(null);
            localDelete = true;
        } else if (media.getLocalFileUrl() != null) {
            // delete transcript file before the media file because the fileurl is needed
            if (media.getTranscriptFileUrl() != null) {
                File transcriptFile = new File(media.getTranscriptFileUrl());
                if (transcriptFile.exists() && !transcriptFile.delete()) {
                    Log.d(TAG, "Deletion of transcript file failed.");
                }
            }

            // delete downloaded media file
            File mediaFile = new File(media.getLocalFileUrl());
            if (mediaFile.exists() && !mediaFile.delete()) {
                Log.d(TAG, "Deletion of downloaded file failed.");
            }
            media.setDownloaded(false, 0);
            media.setLocalFileUrl(null);
            media.setHasEmbeddedPicture(false);
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setMedia(media);
            adapter.close();
        }

        if (media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
            PlaybackPreferences.writeNoMediaPlaying();
            context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_STOP));
        }

        if (localDelete) {
            // Do full update of this feed to get rid of the item
            FeedUpdateManager.getInstance().runOnce(context, media.getItem().getFeed());
        } else {
            if (media.getItem().getFeed().getState() == Feed.STATE_SUBSCRIBED) {
                SynchronizationQueue.getInstance().enqueueEpisodeAction(
                        new EpisodeAction.Builder(media.getItem(), EpisodeAction.DELETE)
                            .currentTimestamp()
                            .build());
            }

            EventBus.getDefault().post(FeedItemEvent.updated(media.getItem()));
        }
        return true;
    }

    /**
     * Deletes a Feed and all downloaded files of its components like images and downloaded episodes.
     *
     * @param context A context that is used for opening a database connection.
     * @param feedId  ID of the Feed that should be deleted.
     */
    public static Future<?> deleteFeed(final Context context, final long feedId) {
        return runOnDbThread(() -> {
            final Feed feed = DBReader.getFeed(feedId, false, 0, Integer.MAX_VALUE);
            if (feed == null) {
                return;
            }

            deleteFeedItemsSynchronous(context, feed.getItems());

            // delete feed
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.removeFeed(feed);
            adapter.close();

            if (!feed.isLocalFeed() && feed.getState() == Feed.STATE_SUBSCRIBED) {
                SynchronizationQueue.getInstance().enqueueFeedRemoved(feed.getDownloadUrl());
            }
            EventBus.getDefault().post(new FeedListUpdateEvent(feed));
        });
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     * Deleting media also removes the download log entries.
     */
    @NonNull
    public static Future<?> deleteFeedItems(@NonNull Context context, @NonNull List<FeedItem> items) {
        return runOnDbThread(() -> deleteFeedItemsSynchronous(context, items));
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     * Deleting media also removes the download log entries.
     */
    private static void deleteFeedItemsSynchronous(@NonNull Context context, @NonNull List<FeedItem> items) {
        List<FeedItem> queue = DBReader.getQueue();
        List<FeedItem> removedFromQueue = new ArrayList<>();
        for (FeedItem item : items) {
            if (queue.remove(item)) {
                removedFromQueue.add(item);
            }
            if (item.getMedia() != null) {
                if (item.getMedia().getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
                    // Applies to both downloaded and streamed media
                    PlaybackPreferences.writeNoMediaPlaying();
                    context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_STOP));
                }
                if (!item.getFeed().isLocalFeed()) {
                    if (DownloadServiceInterface.get().isDownloadingEpisode(item.getMedia().getDownloadUrl())) {
                        DownloadServiceInterface.get().cancel(context, item.getMedia());
                    }
                    if (item.getMedia().isDownloaded()) {
                        deleteFeedMediaSynchronous(context, item.getMedia());
                    }
                }
            }
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        if (!removedFromQueue.isEmpty()) {
            adapter.setQueue(queue);
        }
        adapter.removeFeedItems(items);
        adapter.close();

        for (FeedItem item : removedFromQueue) {
            EventBus.getDefault().post(QueueEvent.irreversibleRemoved(item));
        }

        // we assume we also removed download log entries for the feed or its media files.
        // especially important if download or refresh failed, as the user should not be able
        // to retry these
        EventBus.getDefault().post(DownloadLogEvent.listUpdated());

        BackupManager backupManager = new BackupManager(context);
        backupManager.dataChanged();
    }

    /**
     * Deletes the entire playback history.
     */
    public static Future<?> clearPlaybackHistory() {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.clearPlaybackHistory();
            adapter.close();
            EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated());
        });
    }

    /**
     * Deletes the entire download log.
     */
    public static Future<?> clearDownloadLog() {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.clearDownloadLog();
            adapter.close();
            EventBus.getDefault().post(DownloadLogEvent.listUpdated());
        });
    }

    public static Future<?> deleteFromPlaybackHistory(FeedItem feedItem) {
        return addItemToPlaybackHistory(feedItem.getMedia(), new Date(0));
    }

    /**
     * Adds a FeedMedia object to the playback history. A FeedMedia object is in the playback history if
     * its playback completion date is set to a non-null value. This method will set the playback completion date to the
     * current date regardless of the current value.
     *
     * @param media FeedMedia that should be added to the playback history.
     */
    public static Future<?> addItemToPlaybackHistory(FeedMedia media) {
        return addItemToPlaybackHistory(media, new Date());
    }

    /**
     * Adds a FeedMedia object to the playback history. A FeedMedia object is in the playback history if
     * its playback completion date is set to a non-null value. This method will set the playback completion date to the
     * current date regardless of the current value.
     *
     * @param media FeedMedia that should be added to the playback history.
     * @param date LastPlayedTimeHistory for <code>media</code>
     */
    public static Future<?> addItemToPlaybackHistory(final FeedMedia media, Date date) {
        return runOnDbThread(() -> {
            Log.d(TAG, "Adding item to playback history");
            media.setLastPlayedTimeHistory(date);

            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedMediaLastPlayedTimeHistory(media);
            adapter.close();
            EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated());

        });
    }

    /**
     * Adds a Download status object to the download log.
     *
     * @param status The DownloadStatus object.
     */
    public static Future<?> addDownloadStatus(final DownloadResult status) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setDownloadStatus(status);
            adapter.close();
            EventBus.getDefault().post(DownloadLogEvent.listUpdated());
        });

    }

    /**
     * Inserts a FeedItem in the queue at the specified index. The 'read'-attribute of the FeedItem will be set to
     * true. If the FeedItem is already in the queue, the queue will not be modified.
     *
     * @param context             A context that is used for opening a database connection.
     * @param itemId              ID of the FeedItem that should be added to the queue.
     * @param index               Destination index. Must be in range 0..queue.size()
     * @throws IndexOutOfBoundsException if index < 0 || index >= queue.size()
     */
    public static Future<?> addQueueItemAt(final Context context, final long itemId, final int index) {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            final List<FeedItem> queue = DBReader.getQueue();

            if (!itemListContains(queue, itemId)) {
                FeedItem item = DBReader.getFeedItem(itemId);
                if (item != null) {
                    queue.add(index, item);
                    adapter.setQueue(queue);
                    item.addTag(FeedItem.TAG_QUEUE);
                    EventBus.getDefault().post(QueueEvent.added(item, index));
                    EventBus.getDefault().post(FeedItemEvent.updated(item));
                    if (item.isNew()) {
                        DBWriter.markItemPlayed(FeedItem.UNPLAYED, item.getId());
                    }
                }
            }

            adapter.close();
            AutoDownloadManager.getInstance().autodownloadUndownloadedItems(context);
        });
    }

    /**
     * Appends FeedItem objects to the end of the queue. The 'read'-attribute of all items will be set to true.
     * If a FeedItem is already in the queue, the FeedItem will not change its position in the queue.
     *
     * @param context  A context that is used for opening a database connection.
     * @param items    FeedItem objects that should be added to the queue.
     */
    public static Future<?> addQueueItem(final Context context, final FeedItem... items) {
        return runOnDbThread(() -> {
            if (items.length < 1) {
                return;
            }

            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            final List<FeedItem> queue = DBReader.getQueue();

            LongList markAsUnplayedIds = new LongList();
            List<QueueEvent> events = new ArrayList<>();
            List<FeedItem> updatedItems = new ArrayList<>();
            ItemEnqueuePositionCalculator positionCalculator =
                    new ItemEnqueuePositionCalculator(UserPreferences.getEnqueueLocation());
            Playable currentlyPlaying = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
            int insertPosition = positionCalculator.calcPosition(queue, currentlyPlaying);
            for (FeedItem item : items) {
                if (itemListContains(queue, item.getId())) {
                    continue;
                } else if (!item.hasMedia()) {
                    continue;
                }
                queue.add(insertPosition, item);
                events.add(QueueEvent.added(item, insertPosition));

                item.addTag(FeedItem.TAG_QUEUE);
                updatedItems.add(item);
                if (item.isNew()) {
                    markAsUnplayedIds.add(item.getId());
                }
                insertPosition++;
            }
            if (!updatedItems.isEmpty()) {
                applySortOrder(queue, events);
                adapter.setQueue(queue);
                for (QueueEvent event : events) {
                    EventBus.getDefault().post(event);
                }
                EventBus.getDefault().post(FeedItemEvent.updated(updatedItems));
                if (markAsUnplayedIds.size() > 0) {
                    DBWriter.markItemPlayed(FeedItem.UNPLAYED, markAsUnplayedIds.toArray());
                }
            }
            adapter.close();
            AutoDownloadManager.getInstance().autodownloadUndownloadedItems(context);
        });
    }

    /**
     * Sorts the queue depending on the configured sort order.
     * If the queue is not in keep sorted mode, nothing happens.
     *
     * @param queue  The queue to be sorted.
     * @param events Replaces the events by a single SORT event if the list has to be sorted automatically.
     */
    private static void applySortOrder(List<FeedItem> queue, List<QueueEvent> events) {
        if (!UserPreferences.isQueueKeepSorted()) {
            // queue is not in keep sorted mode, there's nothing to do
            return;
        }

        // Sort queue by configured sort order
        SortOrder sortOrder = UserPreferences.getQueueKeepSortedOrder();
        if (sortOrder == SortOrder.RANDOM) {
            // do not shuffle the list on every change
            return;
        }
        Permutor<FeedItem> permutor = FeedItemPermutors.getPermutor(sortOrder);
        permutor.reorder(queue);

        // Replace ADDED events by a single SORTED event
        events.clear();
        events.add(QueueEvent.sorted(queue));
    }

    /**
     * Removes all FeedItem objects from the queue.
     */
    public static Future<?> clearQueue() {
        return runOnDbThread(() -> {
            final long currentQueueId = UserPreferences.getCurrentQueueId();
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.clearQueue();

            // Clear the currently_playing_feedmedia_id since queue is now empty
            ContentValues values = new ContentValues();
            values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEEDMEDIA_ID, -1);
            values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEED_ID, -1);
            adapter.updateQueueMetadata(currentQueueId, values);

            adapter.close();

            EventBus.getDefault().post(QueueEvent.cleared());
        });
    }

    /**
     * Removes a FeedItem object from the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param item                FeedItem that should be removed.
     */
    public static Future<?> removeQueueItem(final Context context,
                                            final boolean performAutoDownload, final FeedItem item) {
        return runOnDbThread(() -> removeQueueItemSynchronous(context, performAutoDownload, item.getId()));
    }

    public static Future<?> removeQueueItem(final Context context, final boolean performAutoDownload,
                                            final long... itemIds) {
        return runOnDbThread(() -> removeQueueItemSynchronous(context, performAutoDownload, itemIds));
    }

    private static void removeQueueItemSynchronous(final Context context,
                                                   final boolean performAutoDownload,
                                                   final long... itemIds) {
        if (itemIds.length < 1) {
            return;
        }
        final PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        final List<FeedItem> queue = DBReader.getQueue();

        boolean queueModified = false;
        List<QueueEvent> events = new ArrayList<>();
        List<FeedItem> updatedItems = new ArrayList<>();
        for (long itemId : itemIds) {
            int position = indexInItemList(queue, itemId);
            if (position >= 0) {
                final FeedItem item = DBReader.getFeedItem(itemId);
                if (item == null) {
                    Log.e(TAG, "removeQueueItem - item in queue but somehow cannot be loaded."
                            + " Item ignored. It should never happen. id:" + itemId);
                    continue;
                }
                queue.remove(position);
                item.removeTag(FeedItem.TAG_QUEUE);
                events.add(QueueEvent.removed(item));
                updatedItems.add(item);
                queueModified = true;
            } else {
                Log.v(TAG, "removeQueueItem - item  not in queue:" + itemId);
            }
        }
        if (queueModified) {
            adapter.setQueue(queue);

            // If queue is now empty, clear the currently_playing_feedmedia_id in QueueMetadata
            if (queue.isEmpty()) {
                long currentQueueId = UserPreferences.getCurrentQueueId();
                ContentValues values = new ContentValues();
                values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEEDMEDIA_ID, -1);
                values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEED_ID, -1);
                adapter.updateQueueMetadata(currentQueueId, values);
                Log.d(TAG, "Queue is now empty, cleared currently_playing_feedmedia_id");
            }

            for (QueueEvent event : events) {
                EventBus.getDefault().post(event);
            }
            EventBus.getDefault().post(FeedItemEvent.updated(updatedItems));
        } else {
            Log.w(TAG, "Queue was not modified by call to removeQueueItem");
        }
        adapter.close();
        if (performAutoDownload) {
            AutoDownloadManager.getInstance().autodownloadUndownloadedItems(context);
        }
    }

    public static Future<?> toggleFavoriteItem(final FeedItem item) {
        if (item.isTagged(FeedItem.TAG_FAVORITE)) {
            return removeFavoriteItem(item);
        } else {
            return addFavoriteItem(item);
        }
    }

    public static Future<?> addFavoriteItem(final FeedItem item) {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance().open();
            adapter.addFavoriteItem(item);
            adapter.close();
            item.addTag(FeedItem.TAG_FAVORITE);
            EventBus.getDefault().post(new FavoritesEvent());
            EventBus.getDefault().post(FeedItemEvent.updated(item));
        });
    }

    public static Future<?> removeFavoriteItem(final FeedItem item) {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance().open();
            adapter.removeFavoriteItem(item);
            adapter.close();
            item.removeTag(FeedItem.TAG_FAVORITE);
            EventBus.getDefault().post(new FavoritesEvent());
            EventBus.getDefault().post(FeedItemEvent.updated(item));
        });
    }

    /**
     * Changes the position of a FeedItem in the queue.
     *
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     *                        false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    public static Future<?> moveQueueItem(final int from, final int to, final boolean broadcastUpdate) {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            final List<FeedItem> queue = DBReader.getQueue();

            if (from >= 0 && from < queue.size() && to >= 0 && to < queue.size()) {
                final FeedItem item = queue.remove(from);
                queue.add(to, item);

                adapter.setQueue(queue);
                if (broadcastUpdate) {
                    EventBus.getDefault().post(QueueEvent.moved(item, to));
                }
            }
            adapter.close();
        });
    }

    public static Future<?> moveQueueItemsToTop(final List<FeedItem> items) {
        return runOnDbThread(() -> moveQueueItemsSynchronous(true, items));
    }

    public static Future<?> moveQueueItemsToBottom(final List<FeedItem> items) {
        return runOnDbThread(() -> moveQueueItemsSynchronous(false, items));
    }

    private static void moveQueueItemsSynchronous(final boolean moveToTop, final List<FeedItem> items) {
        if (items.isEmpty()) {
            return;
        }

        final PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        final List<FeedItem> queue = DBReader.getQueue();

        List<FeedItem> selectedItems = moveToTop ? new ArrayList<>(items) : items;
        if (moveToTop) {
            Collections.reverse(selectedItems);
        }

        boolean queueModified = false;
        List<QueueEvent> events = new ArrayList<>();

        queue.removeAll(selectedItems);
        events.add(QueueEvent.setQueue(queue));

        for (FeedItem item : selectedItems) {
            int newIndex = moveToTop ? 0 : queue.size();
            queue.add(newIndex, item);
            events.add(QueueEvent.moved(item, newIndex));
            queueModified = true;
        }

        if (queueModified) {
            adapter.setQueue(queue);
            for (QueueEvent event : events) {
                EventBus.getDefault().post(event);
            }
        } else {
            Log.w(TAG, "moveToTop: " + moveToTop +  " - Queue was not modified.");
        }
        adapter.close();
    }

    public static Future<?> resetPagedFeedPage(Feed feed) {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.resetPagedFeedPage(feed);
            adapter.close();
        });
    }

    /**
     * Sets the 'read'-attribute of all specified FeedItems
     *
     * @param played  New value of the 'read'-attribute, one of FeedItem.PLAYED, FeedItem.NEW,
     *                FeedItem.UNPLAYED
     * @param itemIds IDs of the FeedItems.
     */
    public static Future<?> markItemPlayed(final int played, final long... itemIds) {
        return markItemPlayed(played, true, itemIds);
    }

    /**
     * Sets the 'read'-attribute of all specified FeedItems
     *
     * @param played  New value of the 'read'-attribute, one of FeedItem.PLAYED, FeedItem.NEW,
     *                FeedItem.UNPLAYED
     * @param broadcastUpdate true if this operation should trigger a UnreadItemsUpdate broadcast.
     *        This option is usually set to true
     * @param itemIds IDs of the FeedItems.
     */
    public static Future<?> markItemPlayed(final int played, final boolean broadcastUpdate,
                                           final long... itemIds) {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemRead(played, itemIds);
            adapter.close();
            if (broadcastUpdate) {
                EventBus.getDefault().post(new UnreadItemsUpdateEvent());
            }
        });
    }

    /**
     * Sets the 'read'-attribute of a FeedItem to the specified value.
     *
     * @param item               The FeedItem object
     * @param played             New value of the 'read'-attribute one of FeedItem.PLAYED,
     *                           FeedItem.NEW, FeedItem.UNPLAYED
     * @param resetMediaPosition true if this method should also reset the position of the FeedItem's FeedMedia object.
     */
    @NonNull
    public static Future<?> markItemPlayed(FeedItem item, int played, boolean resetMediaPosition) {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemRead(item, played, resetMediaPosition);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    /**
     * Sets the 'read'-attribute of all NEW FeedItems of a specific Feed to UNPLAYED.
     *
     * @param feedId ID of the Feed.
     */
    public static Future<?> removeFeedNewFlag(final long feedId) {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED, feedId);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    /**
     * Sets the 'read'-attribute of all NEW FeedItems to UNPLAYED.
     */
    public static Future<?> removeAllNewFlags() {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    static Future<?> addNewFeed(final Context context, final Feed... feeds) {
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setCompleteFeed(feeds);
            adapter.close();

            for (Feed feed : feeds) {
                if (!feed.isLocalFeed() && feed.getState() == Feed.STATE_SUBSCRIBED) {
                    SynchronizationQueue.getInstance().enqueueFeedAdded(feed.getDownloadUrl());
                }
            }

            BackupManager backupManager = new BackupManager(context);
            backupManager.dataChanged();
        });
    }

    static Future<?> setCompleteFeed(final Feed... feeds) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setCompleteFeed(feeds);
            adapter.close();
        });
    }

    public static Future<?> setItemList(final List<FeedItem> items) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.storeFeedItemlist(items);
            adapter.close();
            EventBus.getDefault().post(FeedItemEvent.updated(items));
        });
    }

    /**
     * Saves a FeedMedia object in the database. This method will save all attributes of the FeedMedia object. The
     * contents of FeedComponent-attributes (e.g. the FeedMedia's 'item'-attribute) will not be saved.
     *
     * @param media The FeedMedia object.
     */
    public static Future<?> setFeedMedia(final FeedMedia media) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setMedia(media);
            adapter.close();
        });
    }

    /**
     * Saves the 'position', 'duration' and 'last played time' attributes of a FeedMedia object
     *
     * @param media The FeedMedia object.
     */
    public static Future<?> setFeedMediaPlaybackInformation(final FeedMedia media) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedMediaPlaybackInformation(media);
            adapter.close();
        });
    }

    /**
     * Saves a FeedItem object in the database. This method will save all attributes of the FeedItem object including
     * the content of FeedComponent-attributes.
     *
     * @param item The FeedItem object.
     */
    public static Future<?> setFeedItem(final FeedItem item) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setSingleFeedItem(item);
            adapter.close();
            EventBus.getDefault().post(FeedItemEvent.updated(item));
        });
    }

    /**
     * Updates download URL of a feed
     */
    public static Future<?> updateFeedDownloadURL(final String original, final String updated) {
        Log.d(TAG, "updateFeedDownloadURL(original: " + original + ", updated: " + updated + ")");
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedDownloadUrl(original, updated);
            adapter.close();
        });
    }

    /**
     * Saves a FeedPreferences object in the database. The Feed ID of the FeedPreferences-object MUST NOT be 0.
     *
     * @param preferences The FeedPreferences object.
     */
    public static Future<?> setFeedPreferences(final FeedPreferences preferences) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedPreferences(preferences);
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(preferences.getFeedID()));
        });
    }

    private static boolean itemListContains(List<FeedItem> items, long itemId) {
        return indexInItemList(items, itemId) >= 0;
    }

    private static int indexInItemList(List<FeedItem> items, long itemId) {
        for (int i = 0; i < items.size(); i++) {
            FeedItem item = items.get(i);
            if (item.getId() == itemId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Saves if a feed's last update failed
     *
     * @param lastUpdateFailed true if last update failed
     */
    public static Future<?> setFeedLastUpdateFailed(final long feedId,
                                                    final boolean lastUpdateFailed) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedLastUpdateFailed(feedId, lastUpdateFailed);
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(feedId));
        });
    }

    public static Future<?> setFeedCustomTitle(Feed feed) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedCustomTitle(feed.getId(), feed.getCustomTitle());
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(feed));
        });
    }

    public static Future<?> setFeedState(Context context, Feed feed, int newState) {
        int oldState = feed.getState();
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedState(feed.getId(), newState);
            feed.setState(newState);
            if (oldState == Feed.STATE_NOT_SUBSCRIBED && newState == Feed.STATE_SUBSCRIBED) {
                feed.getPreferences().setKeepUpdated(true);
                DBWriter.setFeedPreferences(feed.getPreferences());
                FeedUpdateManager.getInstance().runOnceOrAsk(context, feed);
                SynchronizationQueue.getInstance().enqueueFeedAdded(feed.getDownloadUrl());
                DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                        SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
                for (FeedItem item : feed.getItems()) {
                    if (item.isPlayed()) {
                        SynchronizationQueue.getInstance().enqueueEpisodePlayed(item.getMedia(), true);
                    }
                }
            }
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(feed));
        });
    }

    /**
     * Sort the FeedItems in the queue with the given the named sort order.
     *
     * @param broadcastUpdate <code>true</code> if this operation should trigger a
     *                        QueueUpdateBroadcast. This option should be set to <code>false</code>
     *                        if the caller wants to avoid unexpected updates of the GUI.
     */
    public static Future<?> reorderQueue(@Nullable SortOrder sortOrder, final boolean broadcastUpdate) {
        if (sortOrder == null) {
            Log.w(TAG, "reorderQueue() - sortOrder is null. Do nothing.");
            return runOnDbThread(() -> { });
        }
        final Permutor<FeedItem> permutor = FeedItemPermutors.getPermutor(sortOrder);
        return runOnDbThread(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            final List<FeedItem> queue = DBReader.getQueue();

            permutor.reorder(queue);
            adapter.setQueue(queue);
            if (broadcastUpdate) {
                EventBus.getDefault().post(QueueEvent.sorted(queue));
            }
            adapter.close();
        });
    }

    /**
     * Set filter of the feed
     *
     * @param feedId       The feed's ID
     * @param filterValues Values that represent properties to filter by
     */
    public static Future<?> setFeedItemsFilter(final long feedId,
                                               final Set<String> filterValues) {
        Log.d(TAG, "setFeedItemsFilter() called with: " + "feedId = [" + feedId + "], filterValues = [" + filterValues + "]");
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemFilter(feedId, filterValues);
            adapter.close();
            EventBus.getDefault().post(new FeedEvent(FeedEvent.Action.FILTER_CHANGED, feedId));
        });
    }

    /**
     * Set item sort order of the feed
     *
     */
    public static Future<?> setFeedItemSortOrder(long feedId, @Nullable SortOrder sortOrder) {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemSortOrder(feedId, sortOrder);
            adapter.close();
            EventBus.getDefault().post(new FeedEvent(FeedEvent.Action.SORT_ORDER_CHANGED, feedId));
        });
    }

    /**
     * Reset the statistics in DB
     */
    @NonNull
    public static Future<?> resetStatistics() {
        return runOnDbThread(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.resetAllMediaPlayedDuration();
            adapter.close();
        });
    }

    /**
     * Removes the feed with the given download url. This method should NOT be executed on the GUI thread.
     *
     * @param context     Used for accessing the db
     * @param downloadUrl URL of the feed.
     */
    public static void removeFeedWithDownloadUrl(Context context, String downloadUrl) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor cursor = adapter.getFeedCursorDownloadUrls();
        long feedId = 0;
        if (cursor.moveToFirst()) {
            do {
                if (cursor.getString(1).equals(downloadUrl)) {
                    feedId = cursor.getLong(0);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        adapter.close();

        if (feedId != 0) {
            try {
                deleteFeed(context, feedId).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        } else {
            Log.w(TAG, "removeFeedWithDownloadUrl: Could not find feed with url: " + downloadUrl);
        }
    }

    // ============ Multiple Queues Support (T015-T023) ============

    /**
     * Creates a new queue with the given name and color.
     * T015: createQueue(String name, int color)
     *
     * @param name User-defined queue name (cannot be null or empty)
     * @param color RGB color value for UI
     * @return {@code Future<Long>} with the new queue ID
     */
    public static Future<Long> createQueue(@NonNull final String name, final int color) {
        return dbExec.submit(() -> {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Queue name cannot be null or empty");
            }

            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            try {
                // Get next sort_order using atomic MAX query to avoid race conditions
                int sortOrder = adapter.getNextQueueSortOrder();

                // Insert new queue
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(PodDBAdapter.QUEUE_METADATA_NAME, name);
                values.put(PodDBAdapter.QUEUE_METADATA_COLOR, color);
                values.put(PodDBAdapter.QUEUE_METADATA_CREATED_AT, System.currentTimeMillis());
                values.put(PodDBAdapter.QUEUE_METADATA_SORT_ORDER, sortOrder);
                values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEEDMEDIA_ID, QueueMetadata.NO_MEDIA_PLAYING);
                values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEED_ID, QueueMetadata.NO_MEDIA_PLAYING);

                long queueId = adapter.insertQueue(values);

                // Post queue creation event (T026 - Phase 4)
                EventBus.getDefault().post(QueueEvent.queueCreated(queueId));
                return queueId;
            } finally {
                adapter.close();
            }
        });
    }

    /**
     * Renames a queue.
     * T016: renameQueue(long queueId, String newName)
     *
     * @param queueId The ID of the queue to rename
     * @param newName The new name (cannot be null or empty)
     * @return {@code Future<Void>}
     */
    public static Future<Void> renameQueue(final long queueId, @NonNull final String newName) {
        return dbExec.submit(() -> {
            if (newName == null || newName.trim().isEmpty()) {
                throw new IllegalArgumentException("Queue name cannot be null or empty");
            }

            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            try {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(PodDBAdapter.QUEUE_METADATA_NAME, newName);
                adapter.updateQueueMetadata(queueId, values);

                // Post queue renamed event (T026 - Phase 4)
                EventBus.getDefault().post(QueueEvent.queueRenamed(queueId));
            } finally {
                adapter.close();
            }
            return null;
        });
    }

    /**
     * Changes a queue's color.
     * T017: changeQueueColor(long queueId, int color)
     *
     * @param queueId The ID of the queue
     * @param color RGB color value
     * @return {@code Future<Void>}
     */
    public static Future<Void> changeQueueColor(final long queueId, final int color) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            try {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(PodDBAdapter.QUEUE_METADATA_COLOR, color);
                adapter.updateQueueMetadata(queueId, values);

                // Post queue color changed event (T026 - Phase 4)
                EventBus.getDefault().post(QueueEvent.queueColorChanged(queueId));
            } finally {
                adapter.close();
            }
            return null;
        });
    }

    /**
     * Deletes a queue.
     * T018: deleteQueue(long queueId)
     * Last queue cannot be deleted.
     *
     * @param queueId The ID of the queue to delete
     * @return {@code Future<Void>}
     */
    public static Future<Void> deleteQueue(final long queueId) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            try {
                // Check if this is the last queue
                List<QueueMetadata> allQueues = DBReader.getAllQueues();
                if (allQueues.size() <= 1) {
                    throw new IllegalStateException("Cannot delete the last queue");
                }

                // Delete all items in this queue
                adapter.deleteQueueItems(queueId);

                // Delete queue metadata
                adapter.deleteQueueMetadata(queueId);

                // Post queue deleted event (T026 - Phase 4)
                EventBus.getDefault().post(QueueEvent.queueDeleted(queueId));
            } finally {
                adapter.close();
            }
            return null;
        });
    }

    /**
     * Updates the last playing episode for a queue.
     * T018b: updateQueuePlaybackState(long queueId, long feedMediaId)
     *
     * <p>Saves which episode was last playing in a queue. When switching to a different queue,
     * PlaybackService can restore playback from the saved position.
     *
     * @param queueId The ID of the queue
     * @param feedMediaId The ID of the FeedMedia that was last playing, or -1 for none
     * @return {@code Future<Void>}
     */
    public static Future<Void> updateQueuePlaybackState(final long queueId, final long feedMediaId) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            try {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEEDMEDIA_ID, feedMediaId);
                if (feedMediaId >= 0) {
                    // Also set the feed ID from the feed media
                    FeedMedia media = DBReader.getFeedMedia(feedMediaId);
                    if (media != null && media.getItem() != null) {
                        values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEED_ID,
                                media.getItem().getFeed().getId());
                    }
                }
                adapter.updateQueueMetadata(queueId, values);

                // Post event for UI to update (T026 - Phase 4)
                EventBus.getDefault().post(QueueEvent.currentlyPlayingUpdated(queueId));
            } finally {
                adapter.close();
            }
            return null;
        });
    }

    /**
     * Sets the currently playing episode for a queue.
     * T023: setCurrentlyPlaying(long queueId, long feedMediaId, long feedId)
     *
     * @param queueId The queue ID
     * @param feedMediaId FeedMedia ID of currently playing episode, or -1 for none
     * @param feedId Feed ID of currently playing episode, or -1 for none
     * @return {@code Future<Void>}
     */
    public static Future<Void> setCurrentlyPlaying(final long queueId, final long feedMediaId, final long feedId) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            try {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEEDMEDIA_ID, feedMediaId);
                values.put(PodDBAdapter.QUEUE_METADATA_CURRENTLY_PLAYING_FEED_ID, feedId);
                adapter.updateQueueMetadata(queueId, values);

                // Post currently playing updated event (T026 - Phase 4)
                EventBus.getDefault().post(QueueEvent.currentlyPlayingUpdated(queueId));
            } finally {
                adapter.close();
            }
            return null;
        });
    }

    // ============ Queue Item Transfer Operations (T012-T016) ============

    /**
     * Moves an episode from one queue to another.
     * The episode is removed from the source queue and added to the target queue.
     * Reuses the public removeQueueItem and addQueueItem methods to ensure proper UI updates.
     *
     * @param feedItemId The ID of the feed item to move
     * @param sourceQueueId The ID of the source queue
     * @param targetQueueId The ID of the target queue
     * @return {@code Future<MoveResult>} with operation details. On success, movedCount=1, skippedCount=0.
     *         On failure (e.g., episode not in source queue), movedCount=0, skippedCount=1 with reason.
     * @throws Exception (via Future) if database operation fails
     */
    public static Future<MoveResult> moveQueueItem(final long feedItemId, final long sourceQueueId, final long targetQueueId) {
        return dbExec.submit(() -> {
            try {
                // Get the feed item
                FeedItem item = DBReader.getFeedItem(feedItemId);
                if (item == null) {
                    throw new Exception("Feed item not found: " + feedItemId);
                }

                // Save the current active queue
                long originalQueueId = UserPreferences.getCurrentQueueId();

                try {
                    // Switch to source queue and check if item is in queue
                    UserPreferences.setCurrentQueueId(sourceQueueId);
                    List<FeedItem> sourceQueue = DBReader.getQueue();
                    boolean inSourceQueue = sourceQueue.stream().anyMatch(i -> i.getId() == feedItemId);
                    if (!inSourceQueue) {
                        throw new Exception("Episode not in source queue");
                    }

                    // Remove the item from source queue (this posts QueueEvent.removed)
                    removeQueueItemSynchronous(null, false, feedItemId);

                    // Switch to target queue and add the item (this posts QueueEvent.added)
                    UserPreferences.setCurrentQueueId(targetQueueId);
                    // Reuse addQueueItem logic - call it synchronously since we're on dbExec thread
                    final PodDBAdapter adapter = PodDBAdapter.getInstance();
                    adapter.open();
                    final List<FeedItem> queue = DBReader.getQueue();

                    if (!itemListContains(queue, item.getId()) && item.hasMedia()) {
                        final LongList markAsUnplayedIds = new LongList();
                        final List<QueueEvent> events = new ArrayList<>();
                        final List<FeedItem> updatedItems = new ArrayList<>();
                        final ItemEnqueuePositionCalculator positionCalculator =
                                new ItemEnqueuePositionCalculator(UserPreferences.getEnqueueLocation());
                        final Playable currentlyPlaying = DBReader.getFeedMedia(
                                PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
                        final int insertPosition = positionCalculator.calcPosition(queue, currentlyPlaying);

                        queue.add(insertPosition, item);
                        events.add(QueueEvent.added(item, insertPosition));
                        item.addTag(FeedItem.TAG_QUEUE);
                        updatedItems.add(item);
                        if (item.isNew()) {
                            markAsUnplayedIds.add(item.getId());
                        }

                        applySortOrder(queue, events);
                        adapter.setQueue(queue);
                        for (QueueEvent event : events) {
                            EventBus.getDefault().post(event);
                        }
                        EventBus.getDefault().post(FeedItemEvent.updated(updatedItems));
                        if (markAsUnplayedIds.size() > 0) {
                            DBWriter.markItemPlayed(FeedItem.UNPLAYED, markAsUnplayedIds.toArray());
                        }
                    }
                    adapter.close();
                    AutoDownloadManager.getInstance().autodownloadUndownloadedItems(null);

                    // Post move event
                    EventBus.getDefault().post(QueueEvent.itemMoved(item, sourceQueueId, targetQueueId));

                    // Return success result
                    return new MoveResult(1, 0, new ArrayList<>(), new HashMap<>());
                } finally {
                    // Restore the original active queue
                    UserPreferences.setCurrentQueueId(originalQueueId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error moving queue item: " + feedItemId, e);
                List<Long> skipped = new ArrayList<>();
                skipped.add(feedItemId);
                Map<Long, String> reasons = new HashMap<>();
                reasons.put(feedItemId, e.getMessage());
                return new MoveResult(0, 1, skipped, reasons);
            }
        });
    }

    /**
     * Copies an episode to another queue while keeping it in the source queue.
     *
     * @param feedItemId The ID of the feed item to copy
     * @param targetQueueId The ID of the target queue
     * @return {@code Future<CopyResult>} with operation details. On success, copiedCount=1, skippedCount=0.
     *         On failure (e.g., episode already in target queue), copiedCount=0, skippedCount=1 with reason.
     * @throws Exception (via Future) if database operation fails
     */
    public static Future<CopyResult> copyQueueItem(final long feedItemId, final long targetQueueId) {
        return dbExec.submit(() -> {
            try {
                addQueueItemSynchronous(feedItemId, targetQueueId);

                // Post event
                FeedItem item = DBReader.getFeedItem(feedItemId);
                if (item != null) {
                    EventBus.getDefault().post(QueueEvent.itemCopied(item, targetQueueId));
                }

                return new CopyResult(1, 0, new ArrayList<>(), new HashMap<>());
            } catch (Exception e) {
                Log.e(TAG, "Error copying queue item: " + feedItemId, e);
                List<Long> skipped = new ArrayList<>();
                skipped.add(feedItemId);
                Map<Long, String> reasons = new HashMap<>();
                reasons.put(feedItemId, e.getMessage());
                return new CopyResult(0, 1, skipped, reasons);
            }
        });
    }

    /**
     * Moves multiple episodes from one queue to another.
     * Uses best-effort approach: attempts to move each episode, skipping any that fail.
     * Reuses the public removeQueueItem and addQueueItem methods to ensure proper UI updates.
     *
     * @param feedItemIds The IDs of the feed items to move
     * @param sourceQueueId The ID of the source queue
     * @param targetQueueId The ID of the target queue
     * @return {@code Future<MoveResult>} with operation details. Contains movedCount, skippedCount,
     *         skippedItemIds, and skipReasons for each failed item.
     * @throws Exception (via Future) if database operation fails
     */
    public static Future<MoveResult> moveQueueItems(final List<Long> feedItemIds, final long sourceQueueId, final long targetQueueId) {
        return dbExec.submit(() -> {
            int movedCount = 0;
            List<Long> skipped = new ArrayList<>();
            Map<Long, String> reasons = new HashMap<>();
            List<FeedItem> movedItems = new ArrayList<>();

            // Save the current active queue
            long originalQueueId = UserPreferences.getCurrentQueueId();

            try {
                // Get all feed items first
                List<FeedItem> items = new ArrayList<>();
                for (long feedItemId : feedItemIds) {
                    FeedItem item = DBReader.getFeedItem(feedItemId);
                    if (item != null) {
                        items.add(item);
                    } else {
                        // Item not found, skip it
                        skipped.add(feedItemId);
                        reasons.put(feedItemId, "Feed item not found");
                    }
                }

                // Switch to source queue and check which items are in the queue
                UserPreferences.setCurrentQueueId(sourceQueueId);
                List<FeedItem> sourceQueue = DBReader.getQueue();
                List<FeedItem> itemsToRemove = new ArrayList<>();
                for (FeedItem item : items) {
                    if (!skipped.contains(item.getId())) {
                        boolean inSourceQueue = sourceQueue.stream().anyMatch(i -> i.getId() == item.getId());
                        if (inSourceQueue) {
                            itemsToRemove.add(item);
                        } else {
                            // Item not in source queue, skip it
                            skipped.add(item.getId());
                            reasons.put(item.getId(), "Episode not in source queue");
                        }
                    }
                }

                // Remove items from source queue (this posts QueueEvent.removed for each)
                if (!itemsToRemove.isEmpty()) {
                    long[] itemIdsToRemove = itemsToRemove.stream().mapToLong(FeedItem::getId).toArray();
                    removeQueueItemSynchronous(null, false, itemIdsToRemove);
                }

                // Switch to target queue and add items (this posts QueueEvent.added for each)
                UserPreferences.setCurrentQueueId(targetQueueId);
                // Reuse addQueueItem logic - call it synchronously since we're on dbExec thread
                final PodDBAdapter adapter = PodDBAdapter.getInstance();
                adapter.open();
                final List<FeedItem> queue = DBReader.getQueue();

                LongList markAsUnplayedIds = new LongList();
                List<QueueEvent> events = new ArrayList<>();
                List<FeedItem> updatedItems = new ArrayList<>();
                ItemEnqueuePositionCalculator positionCalculator =
                        new ItemEnqueuePositionCalculator(UserPreferences.getEnqueueLocation());
                Playable currentlyPlaying = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
                int insertPosition = positionCalculator.calcPosition(queue, currentlyPlaying);

                for (FeedItem item : itemsToRemove) {
                    if (!itemListContains(queue, item.getId()) && item.hasMedia()) {
                        queue.add(insertPosition, item);
                        events.add(QueueEvent.added(item, insertPosition));
                        item.addTag(FeedItem.TAG_QUEUE);
                        updatedItems.add(item);
                        if (item.isNew()) {
                            markAsUnplayedIds.add(item.getId());
                        }
                        insertPosition++;
                        movedItems.add(item);
                        movedCount++;
                    } else {
                        // Item already in queue or has no media, skip it
                        skipped.add(item.getId());
                        reasons.put(item.getId(), itemListContains(queue, item.getId())
                                ? "Episode already in target queue"
                                : "Episode has no media");
                    }
                }

                if (!updatedItems.isEmpty()) {
                    applySortOrder(queue, events);
                    adapter.setQueue(queue);
                    for (QueueEvent event : events) {
                        EventBus.getDefault().post(event);
                    }
                    EventBus.getDefault().post(FeedItemEvent.updated(updatedItems));
                    if (markAsUnplayedIds.size() > 0) {
                        DBWriter.markItemPlayed(FeedItem.UNPLAYED, markAsUnplayedIds.toArray());
                    }
                }
                adapter.close();
                AutoDownloadManager.getInstance().autodownloadUndownloadedItems(null);

                // Post batch move event if any items were moved
                if (!movedItems.isEmpty()) {
                    EventBus.getDefault().post(QueueEvent.itemsBatchMoved(movedItems, sourceQueueId, targetQueueId));
                }
            } finally {
                // Restore the original active queue
                UserPreferences.setCurrentQueueId(originalQueueId);
            }

            return new MoveResult(movedCount, skipped.size(), skipped, reasons);
        });
    }

    /**
     * Copies multiple episodes to another queue.
     * Uses best-effort approach: skips items that are already in the target queue or encounter errors.
     *
     * @param feedItemIds The IDs of the feed items to copy
     * @param targetQueueId The ID of the target queue
     * @return {@code Future<CopyResult>} with operation details. Contains copiedCount, skippedCount,
     *         skippedItemIds, and skipReasons for each failed item.
     * @throws Exception (via Future) if database operation fails
     */
    public static Future<CopyResult> copyQueueItems(final List<Long> feedItemIds, final long targetQueueId) {
        return dbExec.submit(() -> {
            int copiedCount = 0;
            List<Long> skipped = new ArrayList<>();
            Map<Long, String> reasons = new HashMap<>();

            for (long feedItemId : feedItemIds) {
                try {
                    addQueueItemSynchronous(feedItemId, targetQueueId);
                    copiedCount++;
                } catch (Exception e) {
                    skipped.add(feedItemId);
                    reasons.put(feedItemId, e.getMessage());
                }
            }

            if (copiedCount > 0) {
                List<FeedItem> copiedItems = new ArrayList<>();
                for (long feedItemId : feedItemIds) {
                    if (!skipped.contains(feedItemId)) {
                        FeedItem item = DBReader.getFeedItem(feedItemId);
                        if (item != null) {
                            copiedItems.add(item);
                        }
                    }
                }
                EventBus.getDefault().post(QueueEvent.itemsBatchCopied(copiedItems, targetQueueId));
            }

            return new CopyResult(copiedCount, skipped.size(), skipped, reasons);
        });
    }

    /**
     * Synchronous helper method to remove a queue item from a specific queue.
     * Does NOT trigger events (caller is responsible).
     *
     * @param context Application context (nullable for internal use)
     * @param performAutoDownload Whether to perform auto-download after removal
     * @param feedItemId The feed item ID to remove
     * @param queueId The queue ID to remove from
     * @throws Exception if removal fails
     */
    private static void removeQueueItemSynchronous(@Nullable Context context, boolean performAutoDownload, long feedItemId, long queueId) throws Exception {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            // Get current queue items
            List<FeedItem> queue = DBReader.getQueue(queueId);

            // Check if item is in the queue
            boolean found = false;
            // Remove the item from the list (API 21 compatible)
            for (int i = queue.size() - 1; i >= 0; i--) {
                if (queue.get(i).getId() == feedItemId) {
                    queue.remove(i);
                    found = true;
                    break;
                }
            }

            // Throw exception if item not found in source queue
            if (!found) {
                throw new Exception("Episode not in source queue");
            }

            // Update the queue
            adapter.setQueue(queue, queueId);
        } finally {
            adapter.close();
        }
    }

    /**
     * Synchronous helper method to add a queue item to a specific queue.
     * Does NOT trigger events (caller is responsible).
     *
     * @param feedItemId The feed item ID to add
     * @param queueId The queue ID to add to
     * @throws Exception if addition fails
     */
    private static void addQueueItemSynchronous(long feedItemId, long queueId) throws Exception {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            FeedItem item = DBReader.getFeedItem(feedItemId);
            if (item == null) {
                throw new Exception("Feed item not found: " + feedItemId);
            }

            // Get current queue items
            List<FeedItem> queue = DBReader.getQueue(queueId);

            // Check if item already in target queue (prevent duplicates)
            if (itemListContains(queue, feedItemId)) {
                throw new Exception("Episode already in target queue");
            }

            // Add the item to the end of the queue
            queue.add(item);

            // Update the queue
            adapter.setQueue(queue, queueId);
        } finally {
            adapter.close();
        }
    }

    // ============ Backward Compatibility (T020-T022, T042) ============

    /**
     * Add queue item at specific position (overloaded for multi-queue).
     * T042: addQueueItemAt(long itemId, int index, long queueId)
     *
     * Preserves existing behavior: insert episode at specific position within queue.
     * NOTE: This is an overload of existing addQueueItemAt(Context, long, int) method.
     * For Phase 3, implementation deferred to Phase 4 when QueuePreferences available.
     *
     * @param context Application context
     * @param itemId Feed item ID to add
     * @param index Position to insert at (0-indexed)
     * @param queueId Target queue ID
     * @return {@code Future<Void>}
     */
    // TODO T042: Implement addQueueItemAt with queueId parameter
    // Implementation strategy:
    // 1. Get current queue items: SELECT * FROM Queue WHERE queue_id = ? ORDER BY id ASC
    // 2. If index > max_id, just append (no shift needed)
    // 3. Otherwise, shift positions for all items at position >= index (increment id by 1)
    // 4. Insert new item at target position
    // 5. Update QueueMetadata.currently_playing_feedmedia_id if first item
    // 6. Post QueueEvent

    /**
     * Submit to the DB thread only if caller is not already on the DB thread. Otherwise,
     * just execute synchronously
     */
    private static Future<?> runOnDbThread(Runnable runnable) {
        if ("DatabaseExecutor".equals(Thread.currentThread().getName())) {
            runnable.run();
            return Futures.immediateFuture(null);
        } else {
            return dbExec.submit(runnable);
        }
    }
}
