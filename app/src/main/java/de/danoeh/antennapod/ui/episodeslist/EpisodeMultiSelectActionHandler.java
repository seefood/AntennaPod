package de.danoeh.antennapod.ui.episodeslist;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.PluralsRes;

import java.util.ArrayList;
import java.util.List;

import androidx.fragment.app.Fragment;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.LongList;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.QueueSelectionDialog;
import de.danoeh.antennapod.ui.view.LocalDeleteModal;
import org.greenrobot.eventbus.EventBus;

public class EpisodeMultiSelectActionHandler {
    private static final String TAG = "EpisodeSelectHandler";
    private final Activity activity;
    private final int actionId;
    private final Fragment fragment; // For showing dialogs
    private int totalNumItems = 0;

    public EpisodeMultiSelectActionHandler(Activity activity, int actionId) {
        this.activity = activity;
        this.actionId = actionId;
        this.fragment = null;
    }

    public EpisodeMultiSelectActionHandler(Activity activity, int actionId, Fragment fragment) {
        this.activity = activity;
        this.actionId = actionId;
        this.fragment = fragment;
    }

    public void handleAction(List<FeedItem> items) {
        if (actionId == R.id.add_to_queue_item) {
            queueChecked(items);
        } else if (actionId == R.id.remove_from_queue_item) {
            removeFromQueueChecked(items);
        }  else if (actionId == R.id.remove_inbox_item) {
            removeFromInboxChecked(items);
        } else if (actionId == R.id.mark_read_item) {
            markedCheckedPlayed(items);
        } else if (actionId == R.id.mark_unread_item) {
            markedCheckedUnplayed(items);
        } else if (actionId == R.id.download_item) {
            downloadChecked(items);
        } else if (actionId == R.id.remove_item) {
            LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary(activity, items, () -> deleteChecked(items));
        } else if (actionId == R.id.move_to_top_item) {
            moveToTopChecked(items);
        } else if (actionId == R.id.move_to_bottom_item) {
            moveToBottomChecked(items);
        } else if (actionId == R.id.move_to_queue_item) {
            moveToQueueChecked(items);
        } else if (actionId == R.id.copy_to_queue_item) {
            copyToQueueChecked(items);
        } else {
            Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=" + actionId);
        }
    }

    private void queueChecked(List<FeedItem> items) {
        // Count here to give accurate number in snackbar
        List<FeedItem> toQueue = new ArrayList<>();
        for (FeedItem episode : items) {
            if (episode.hasMedia() && !episode.isTagged(FeedItem.TAG_QUEUE)) {
                toQueue.add(episode);
            }
        }
        DBWriter.addQueueItem(activity, toQueue.toArray(new FeedItem[0]));
        showMessage(R.plurals.added_to_queue_message, toQueue.size());
    }

    private void removeFromQueueChecked(List<FeedItem> items) {
        long[] checkedIds = getSelectedIds(items);
        DBWriter.removeQueueItem(activity, true, checkedIds);
        showMessage(R.plurals.removed_from_queue_message, checkedIds.length);
    }

    private void removeFromInboxChecked(List<FeedItem> items) {
        LongList markUnplayed = new LongList();
        for (FeedItem episode : items) {
            if (episode.isNew()) {
                markUnplayed.add(episode.getId());
            }
        }
        DBWriter.markItemPlayed(FeedItem.UNPLAYED, markUnplayed.toArray());
        showMessage(R.plurals.removed_from_inbox_batch_label, markUnplayed.size());
    }

    private void markedCheckedPlayed(List<FeedItem> items) {
        long[] checkedIds = getSelectedIds(items);
        DBWriter.markItemPlayed(FeedItem.PLAYED, checkedIds);
        showMessage(R.plurals.marked_as_played_message, checkedIds.length);
    }

    private void markedCheckedUnplayed(List<FeedItem> items) {
        long[] checkedIds = getSelectedIds(items);
        DBWriter.markItemPlayed(FeedItem.UNPLAYED, checkedIds);
        showMessage(R.plurals.marked_as_unplayed_message, checkedIds.length);
    }

    private void downloadChecked(List<FeedItem> items) {
        // download the check episodes in the same order as they are currently displayed
        int downloaded = 0;
        for (FeedItem episode : items) {
            if (episode.hasMedia() && !episode.isDownloaded() && !episode.getFeed().isLocalFeed()) {
                DownloadServiceInterface.get().download(activity, episode);
                downloaded++;
            }
        }
        showMessage(R.plurals.downloading_episodes_message, downloaded);
    }

    private void deleteChecked(List<FeedItem> items) {
        int countHasMedia = 0;
        for (FeedItem feedItem : items) {
            if (!feedItem.hasMedia()) {
                continue;
            }
            if (feedItem.getMedia().isDownloaded() || feedItem.getFeed().isLocalFeed()) {
                countHasMedia++;
                DBWriter.deleteFeedMediaOfItem(activity, feedItem.getMedia());
            } else if (DownloadServiceInterface.get().isDownloadingEpisode(feedItem.getMedia().getDownloadUrl())) {
                countHasMedia++;
                DownloadServiceInterface.get().cancel(activity, feedItem.getMedia());
            }
        }
        showMessage(R.plurals.deleted_episode_message, countHasMedia);
    }

    private void moveToTopChecked(List<FeedItem> items) {
        DBWriter.moveQueueItemsToTop(items);
        showMessage(R.plurals.move_to_top_message, items.size());
    }

    private void moveToBottomChecked(List<FeedItem> items) {
        DBWriter.moveQueueItemsToBottom(items);
        showMessage(R.plurals.move_to_bottom_message, items.size());
    }

    private void moveToQueueChecked(List<FeedItem> items) {
        if (fragment == null) {
            Log.e(TAG, "Fragment required for move to queue operation");
            return;
        }

        // Get source queue ID (use current active queue)
        long sourceQueueId = UserPreferences.getCurrentQueueId();

        // Get item IDs
        List<Long> itemIds = new ArrayList<>();
        for (FeedItem item : items) {
            itemIds.add(item.getId());
        }

        QueueSelectionDialog dialog = QueueSelectionDialog.newInstance(sourceQueueId, "move");
        dialog.setOnQueueSelectedListener(selectedQueue -> {
            try {
                de.danoeh.antennapod.model.MoveResult result = DBWriter.moveQueueItems(
                        itemIds, sourceQueueId, selectedQueue.getId()).get();
                if (result.getMovedCount() > 0) {
                    String message = activity.getString(R.string.episodes_moved_to_queue,
                            result.getMovedCount(), selectedQueue.getName());
                    EventBus.getDefault().post(new MessageEvent(message));
                }
                if (result.getSkippedCount() > 0) {
                    String skippedMessage = activity.getString(R.string.episodes_skipped,
                            result.getSkippedCount());
                    EventBus.getDefault().post(new MessageEvent(skippedMessage));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error moving episodes to queue", e);
                String errorMessage = activity.getString(R.string.error_moving_episodes);
                EventBus.getDefault().post(new MessageEvent(errorMessage));
            }
        });
        dialog.show(fragment.getChildFragmentManager(), "QueueSelectionDialog");
    }

    private void copyToQueueChecked(List<FeedItem> items) {
        if (fragment == null) {
            Log.e(TAG, "Fragment required for copy to queue operation");
            return;
        }

        // Get item IDs
        List<Long> itemIds = new ArrayList<>();
        for (FeedItem item : items) {
            itemIds.add(item.getId());
        }

        QueueSelectionDialog dialog = QueueSelectionDialog.newInstance(null, "copy");
        dialog.setOnQueueSelectedListener(selectedQueue -> {
            try {
                de.danoeh.antennapod.model.CopyResult result = DBWriter.copyQueueItems(
                        itemIds, selectedQueue.getId()).get();
                if (result.getCopiedCount() > 0) {
                    String message = activity.getString(R.string.episodes_copied_to_queue,
                            result.getCopiedCount(), selectedQueue.getName());
                    EventBus.getDefault().post(new MessageEvent(message));
                }
                if (result.getSkippedCount() > 0) {
                    String skippedMessage = activity.getString(R.string.episodes_skipped,
                            result.getSkippedCount());
                    EventBus.getDefault().post(new MessageEvent(skippedMessage));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error copying episodes to queue", e);
                String errorMessage = activity.getString(R.string.error_copying_episodes);
                EventBus.getDefault().post(new MessageEvent(errorMessage));
            }
        });
        dialog.show(fragment.getChildFragmentManager(), "QueueSelectionDialog");
    }

    private void showMessage(@PluralsRes int msgId, int numItems) {
        if (numItems == 1) {
            return;
        }
        totalNumItems += numItems;
        activity.runOnUiThread(() -> {
            String text = activity.getResources().getQuantityString(msgId, totalNumItems, totalNumItems);
            EventBus.getDefault().post(new MessageEvent(text));
        });
    }

    private long[] getSelectedIds(List<FeedItem> items) {
        long[] checkedIds = new long[items.size()];
        for (int i = 0; i < items.size(); ++i) {
            checkedIds[i] = items.get(i).getId();
        }
        return checkedIds;
    }
}
