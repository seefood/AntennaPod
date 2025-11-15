package de.danoeh.antennapod.event;

import androidx.annotation.Nullable;

import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;

public class QueueEvent {

    public enum Action {
        // Item operations
        ADDED,
        ADDED_ITEMS,
        SET_QUEUE,
        REMOVED,
        IRREVERSIBLE_REMOVED,
        CLEARED,
        DELETED_MEDIA,
        SORTED,
        MOVED,
        // Queue transfer operations (T028)
        ITEM_MOVED,
        ITEM_COPIED,
        ITEMS_BATCH_MOVED,
        ITEMS_BATCH_COPIED,
        // Queue management operations
        QUEUE_CREATED,
        QUEUE_RENAMED,
        QUEUE_COLOR_CHANGED,
        QUEUE_DELETED,
        QUEUE_SWITCHED,
        CURRENTLY_PLAYING_UPDATED,
        // Queue refill operations (T068, T073)
        REFILLED
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

    // ============ Queue Transfer Operations (T028-T029) ============

    /**
     * Fired when a single episode is moved from one queue to another.
     *
     * @param item The FeedItem that was moved
     * @param sourceQueueId The ID of the source queue
     * @param targetQueueId The ID of the target queue
     */
    public static QueueEvent itemMoved(FeedItem item, long sourceQueueId, long targetQueueId) {
        // Store source queue ID in position field for now; better pattern would be to extend QueueEvent
        return new QueueEvent(Action.ITEM_MOVED, item, null, (int) sourceQueueId, targetQueueId);
    }

    /**
     * Fired when a single episode is copied to another queue.
     *
     * @param item The FeedItem that was copied
     * @param targetQueueId The ID of the queue it was copied to
     */
    public static QueueEvent itemCopied(FeedItem item, long targetQueueId) {
        return new QueueEvent(Action.ITEM_COPIED, item, null, -1, targetQueueId);
    }

    /**
     * Fired when multiple episodes are moved from one queue to another.
     *
     * @param items The FeedItems that were moved
     * @param sourceQueueId The ID of the source queue
     * @param targetQueueId The ID of the target queue
     */
    public static QueueEvent itemsBatchMoved(List<FeedItem> items, long sourceQueueId, long targetQueueId) {
        return new QueueEvent(Action.ITEMS_BATCH_MOVED, null, items, (int) sourceQueueId, targetQueueId);
    }

    /**
     * Fired when multiple episodes are copied to another queue.
     *
     * @param items The FeedItems that were copied
     * @param targetQueueId The ID of the target queue
     */
    public static QueueEvent itemsBatchCopied(List<FeedItem> items, long targetQueueId) {
        return new QueueEvent(Action.ITEMS_BATCH_COPIED, null, items, -1, targetQueueId);
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

    /**
     * Fired when a queue is cleared (for refill engine).
     *
     * @param queueId The ID of the cleared queue
     */
    public static QueueEvent cleared(long queueId) {
        return new QueueEvent(Action.CLEARED, null, null, -1, queueId);
    }

    /**
     * Fired when a queue is refilled (for refill engine).
     *
     * @param queueId The ID of the refilled queue
     */
    public static QueueEvent refilled(long queueId) {
        return new QueueEvent(Action.REFILLED, null, null, -1, queueId);
    }
}
