-- Migration: Migrate existing queue data and SharedPreferences
-- Version: 3080100 (from 3080000)
-- Purpose: Create default "Main" queue and migrate currently_playing state
-- Impact: One new row in QueueMetadata, SharedPreferences values copied to database

-- Step 1: Create default "Main" queue with id=1
-- Note: color -14575885 = 0xFF1A1A1A (dark gray)
-- Note: created_at and currently_playing_* will be set from SharedPreferences in Java code
INSERT INTO QueueMetadata (id, name, color, created_at, sort_order, currently_playing_feedmedia_id, currently_playing_feed_id)
VALUES (
    1,
    'Main',
    -14575885,
    -- created_at: Current timestamp in milliseconds (set in DBUpgrader.java)
    strftime('%s', 'now') * 1000,
    0,
    -- currently_playing_feedmedia_id: Migrated from PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID (set in DBUpgrader.java)
    -1,
    -- currently_playing_feed_id: Migrated from PREF_CURRENTLY_PLAYING_FEED_ID (set in DBUpgrader.java)
    -1
);

-- Step 2: Set PREF_CURRENT_QUEUE_ID to 1 in SharedPreferences (done in DBUpgrader.java)
-- Step 3: Remove PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID from SharedPreferences (done in DBUpgrader.java)
-- Step 4: Remove PREF_CURRENTLY_PLAYING_FEED_ID from SharedPreferences (done in DBUpgrader.java)

-- Note: All existing Queue rows will have queue_id=1 (virtual DEFAULT from 002-alter-queue-add-queue-id.sql)
-- No UPDATE needed - SQLite handles this virtually
