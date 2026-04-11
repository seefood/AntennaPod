package de.danoeh.antennapod.event;

import androidx.annotation.Nullable;

import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;

public class QueueEvent {

    public enum Action {
        ADDED, ADDED_ITEMS, SET_QUEUE, REMOVED, IRREVERSIBLE_REMOVED, CLEARED, DELETED_MEDIA,
        SORTED, MOVED, REFILLED, OPERATION_FAILED
    }

    public final Action action;
    public final FeedItem item;
    public final int position;
    public final List<FeedItem> items;
    /**
     * Number of episodes added; only set for {@link Action#REFILLED}.
     */
    public final int episodesAdded;
    /**
     * Human-readable failure message; only set for {@link Action#OPERATION_FAILED}.
     */
    @Nullable
    public final String errorMessage;


    private QueueEvent(Action action,
                       @Nullable FeedItem item,
                       @Nullable List<FeedItem> items,
                       int position,
                       int episodesAdded,
                       @Nullable String errorMessage) {
        this.action = action;
        this.item = item;
        this.items = items;
        this.position = position;
        this.episodesAdded = episodesAdded;
        this.errorMessage = errorMessage;
    }

    private QueueEvent(Action action,
                       @Nullable FeedItem item,
                       @Nullable List<FeedItem> items,
                       int position) {
        this(action, item, items, position, 0, null);
    }

    public static QueueEvent added(FeedItem item, int position) {
        return new QueueEvent(Action.ADDED, item, null, position);
    }

    public static QueueEvent setQueue(List<FeedItem> queue) {
        return new QueueEvent(Action.SET_QUEUE, null, queue, -1);
    }

    public static QueueEvent removed(FeedItem item) {
        return new QueueEvent(Action.REMOVED, item, null, -1);
    }

    public static QueueEvent irreversibleRemoved(FeedItem item) {
        return new QueueEvent(Action.IRREVERSIBLE_REMOVED, item, null, -1);
    }

    public static QueueEvent cleared() {
        return new QueueEvent(Action.CLEARED, null, null, -1);
    }

    public static QueueEvent sorted(List<FeedItem> sortedQueue) {
        return new QueueEvent(Action.SORTED, null, sortedQueue, -1);
    }

    public static QueueEvent moved(FeedItem item, int newPosition) {
        return new QueueEvent(Action.MOVED, item, null, newPosition);
    }

    public static QueueEvent refilled(long queueId, int episodesAdded) {
        return new QueueEvent(Action.REFILLED, null, null, (int) queueId, episodesAdded, null);
    }

    public static QueueEvent operationFailed(long queueId, String message) {
        return new QueueEvent(Action.OPERATION_FAILED, null, null, (int) queueId, 0, message);
    }

    /**
     * Returns the queue ID; meaningful for {@link Action#REFILLED} and {@link Action#OPERATION_FAILED}.
     */
    public long getQueueId() {
        return position;
    }
}
