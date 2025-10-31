-- Migration: Create QueueMetadata table
-- Version: 3080100 (from 3080000)
-- Purpose: Add support for multiple named queues
-- Impact: New table, no impact on existing data

CREATE TABLE QueueMetadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    color INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    currently_playing_feedmedia_id INTEGER DEFAULT -1,
    currently_playing_feed_id INTEGER DEFAULT -1
);

-- Unique constraint on sort_order (user-defined display order)
CREATE UNIQUE INDEX idx_queue_metadata_sort_order ON QueueMetadata(sort_order);
