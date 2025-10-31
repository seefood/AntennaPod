-- Migration: Add queue_id column to Queue table
-- Version: 3080100 (from 3080000)
-- Purpose: Associate queue items with specific queues
-- Impact: Virtual column added (DEFAULT 1), instant operation

-- Step 1: Add queue_id column with virtual DEFAULT
-- Note: SQLite applies DEFAULT virtually, so this is instant for existing rows
ALTER TABLE Queue ADD COLUMN queue_id INTEGER DEFAULT 1;

-- Step 2: Create composite index for primary query pattern (WHERE queue_id = ? ORDER BY id)
CREATE INDEX idx_queue_queue_id_id ON Queue(queue_id, id);

-- Step 3: Create unique constraint on (queue_id, id) - position within specific queue
CREATE UNIQUE INDEX idx_queue_unique_position ON Queue(queue_id, id);

-- Step 4: Create unique constraint on feeditem - episode appears in at most one queue
CREATE UNIQUE INDEX idx_queue_unique_feeditem ON Queue(feeditem);
