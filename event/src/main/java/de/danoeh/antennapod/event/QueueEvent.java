package de.danoeh.antennapod.event;

import androidx.annotation.Nullable;

import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;

public class QueueEvent {

    public enum Action {
        // Item operations
        ADDED, ADDED_ITEMS, SET_QUEUE, REMOVED, IRREVERSIBLE_REMOVED, CLEARED, DELETED_MEDIA, SORTED, MOVED,
        // Queue management operations
        QUEUE_CREATED, QUEUE_RENAMED, QUEUE_COLOR_CHANGED, QUEUE_DELETED, QUEUES_REORDERED, QUEUE_SWITCHED, CURRENTLY_PLAYING_UPDATED
    }

    public final Action action;
    public final FeedItem item;
    public final int position;
    public final List<FeedItem> items;
    public final long queueId;

    /**
     * Creates a QueueEvent with queue ID tracking.
     *
     * @param action The type of queue event that occurred
     * @param item Optional FeedItem involved in the event (for ADDED, REMOVED, MOVED, etc.)
     * @param items Optional list of FeedItems involved (for SET_QUEUE, SORTED, etc.)
     * @param position Position/index for operations like ADDED or MOVED
     * @param queueId The ID of the queue affected (or -1 for queue-level operations)
     */
    private QueueEvent(Action action,
                       @Nullable FeedItem item,
                       @Nullable List<FeedItem> items,
                       int position,
                       long queueId) {
        this.action = action;
        this.item = item;
        this.items = items;
        this.position = position;
        this.queueId = queueId;
    }

    // ============ Item Operations (backward compatible, queueId defaults to -1) ============

    public static QueueEvent added(FeedItem item, int position) {
        return new QueueEvent(Action.ADDED, item, null, position, -1);
    }

    public static QueueEvent setQueue(List<FeedItem> queue) {
        return new QueueEvent(Action.SET_QUEUE, null, queue, -1, -1);
    }

    public static QueueEvent removed(FeedItem item) {
        return new QueueEvent(Action.REMOVED, item, null, -1, -1);
    }

    public static QueueEvent irreversibleRemoved(FeedItem item) {
        return new QueueEvent(Action.IRREVERSIBLE_REMOVED, item, null, -1, -1);
    }

    public static QueueEvent cleared() {
        return new QueueEvent(Action.CLEARED, null, null, -1, -1);
    }

    public static QueueEvent sorted(List<FeedItem> sortedQueue) {
        return new QueueEvent(Action.SORTED, null, sortedQueue, -1, -1);
    }

    public static QueueEvent moved(FeedItem item, int newPosition) {
        return new QueueEvent(Action.MOVED, item, null, newPosition, -1);
    }

    // ============ Queue Management Operations (T026 - new queue-specific actions) ============

    /**
     * Fired when a new queue is created.
     *
     * @param queueId The ID of the newly created queue
     */
    public static QueueEvent queueCreated(long queueId) {
        return new QueueEvent(Action.QUEUE_CREATED, null, null, -1, queueId);
    }

    /**
     * Fired when a queue is renamed.
     *
     * @param queueId The ID of the renamed queue
     */
    public static QueueEvent queueRenamed(long queueId) {
        return new QueueEvent(Action.QUEUE_RENAMED, null, null, -1, queueId);
    }

    /**
     * Fired when a queue's color is changed.
     *
     * @param queueId The ID of the queue with changed color
     */
    public static QueueEvent queueColorChanged(long queueId) {
        return new QueueEvent(Action.QUEUE_COLOR_CHANGED, null, null, -1, queueId);
    }

    /**
     * Fired when a queue is deleted.
     *
     * @param queueId The ID of the deleted queue
     */
    public static QueueEvent queueDeleted(long queueId) {
        return new QueueEvent(Action.QUEUE_DELETED, null, null, -1, queueId);
    }

    /**
     * Fired when queues are reordered.
     *
     * @param queueIds List of queue IDs in new order
     */
    public static QueueEvent queuesReordered(List<Long> queueIds) {
        return new QueueEvent(Action.QUEUES_REORDERED, null, null, -1, -1);
    }

    /**
     * Fired when the user switches to a different active queue.
     *
     * @param queueId The ID of the newly active queue
     */
    public static QueueEvent queueSwitched(long queueId) {
        return new QueueEvent(Action.QUEUE_SWITCHED, null, null, -1, queueId);
    }

    /**
     * Fired when the currently playing episode for a queue is updated.
     *
     * @param queueId The ID of the queue with updated playback state
     */
    public static QueueEvent currentlyPlayingUpdated(long queueId) {
        return new QueueEvent(Action.CURRENTLY_PLAYING_UPDATED, null, null, -1, queueId);
    }
}
