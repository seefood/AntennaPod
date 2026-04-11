package de.danoeh.antennapod.storage.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Tests database migration to version 3120000 (Smart Queues).
 * Verifies that QueueRuleset and RefillRule tables are created correctly
 * and that existing tables are not modified.
 */
@RunWith(RobolectricTestRunner.class)
public class DBMigrationTest {

    private SQLiteDatabase db;

    @Before
    public void setUp() {
        db = SQLiteDatabase.create(null);
        createSchemaAt3110000(db);
    }

    @After
    public void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    @Test
    public void testMigrationCreatesQueueRulesetTable() {
        DBUpgrader.upgrade(db, 3110000, 3120000);

        assertTrue("QueueRuleset table must exist after migration",
                tableExists(db, "QueueRuleset"));

        List<String> columns = getColumnNames(db, "QueueRuleset");
        assertTrue("id column required", columns.contains("id"));
        assertTrue("queue_id column required", columns.contains("queue_id"));
        assertTrue("created_at column required", columns.contains("created_at"));
        assertTrue("updated_at column required", columns.contains("updated_at"));
    }

    @Test
    public void testMigrationCreatesRefillRuleTable() {
        DBUpgrader.upgrade(db, 3110000, 3120000);

        assertTrue("RefillRule table must exist after migration",
                tableExists(db, "RefillRule"));

        List<String> columns = getColumnNames(db, "RefillRule");
        assertTrue("id column required", columns.contains("id"));
        assertTrue("ruleset_id column required", columns.contains("ruleset_id"));
        assertTrue("position column required", columns.contains("position"));
        assertTrue("selection_method column required", columns.contains("selection_method"));
        assertTrue("count column required", columns.contains("count"));
        assertTrue("source_type column required", columns.contains("source_type"));
        assertTrue("source_id column required", columns.contains("source_id"));
        assertTrue("created_at column required", columns.contains("created_at"));
        assertTrue("updated_at column required", columns.contains("updated_at"));
    }

    @Test
    public void testMigrationCreatesIndexes() {
        DBUpgrader.upgrade(db, 3110000, 3120000);

        assertTrue("idx_queue_ruleset_queue_id must exist",
                indexExists(db, "idx_queue_ruleset_queue_id"));
        assertTrue("idx_refill_rule_ruleset_id must exist",
                indexExists(db, "idx_refill_rule_ruleset_id"));
        assertTrue("idx_refill_rule_ruleset_position must exist",
                indexExists(db, "idx_refill_rule_ruleset_position"));
    }

    @Test
    public void testMigrationPreservesExistingTables() {
        DBUpgrader.upgrade(db, 3110000, 3120000);

        assertTrue("Feeds table must still exist", tableExists(db, "Feeds"));
        assertTrue("FeedItems table must still exist", tableExists(db, "FeedItems"));
        assertTrue("Queue table must still exist", tableExists(db, "Queue"));
        assertTrue("FeedMedia table must still exist", tableExists(db, "FeedMedia"));
        assertTrue("Favorites table must still exist", tableExists(db, "Favorites"));
    }

    @Test
    public void testMigrationIsIdempotentOnRepeat() {
        // Running twice should not crash (IF NOT EXISTS guards)
        DBUpgrader.upgrade(db, 3110000, 3120000);
        DBUpgrader.upgrade(db, 3110000, 3120000);

        assertTrue("QueueRuleset still exists after double migration",
                tableExists(db, "QueueRuleset"));
        assertTrue("RefillRule still exists after double migration",
                tableExists(db, "RefillRule"));
    }

    // ---- helpers ----

    private static boolean tableExists(SQLiteDatabase db, String tableName) {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName})) {
            return c.getCount() > 0;
        }
    }

    private static boolean indexExists(SQLiteDatabase db, String indexName) {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
                new String[]{indexName})) {
            return c.getCount() > 0;
        }
    }

    private static List<String> getColumnNames(SQLiteDatabase db, String tableName) {
        List<String> columns = new ArrayList<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                columns.add(c.getString(nameIdx));
            }
        }
        return columns;
    }

    /**
     * Recreates the database schema as it existed at version 3110000,
     * so migration tests start from a known baseline.
     */
    private static void createSchemaAt3110000(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Feeds ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT, custom_title TEXT, file_url TEXT, download_url TEXT,"
                + "downloaded INTEGER, link TEXT, description TEXT, payment_link TEXT,"
                + "last_update TEXT, language TEXT, author TEXT, image_url TEXT,"
                + "type TEXT, feed_identifier TEXT, auto_download INTEGER DEFAULT 1,"
                + "username TEXT, password TEXT,"
                + "include_filter TEXT DEFAULT '', exclude_filter TEXT DEFAULT '',"
                + "minimal_duration_filter INTEGER DEFAULT -1,"
                + "keep_updated INTEGER DEFAULT 1, is_paged INTEGER DEFAULT 0,"
                + "next_page_link TEXT, hide TEXT, sort_order TEXT,"
                + "last_update_failed INTEGER DEFAULT 0, auto_delete_action INTEGER DEFAULT 0,"
                + "feed_playback_speed REAL DEFAULT -1,"
                + "feed_skip_silence INTEGER DEFAULT 0,"
                + "feed_volume_adaption INTEGER DEFAULT 0,"
                + "tags TEXT,"
                + "feed_skip_intro INTEGER DEFAULT 0, feed_skip_ending INTEGER DEFAULT 0,"
                + "episode_notification INTEGER DEFAULT 0,"
                + "state INTEGER DEFAULT 1,"
                + "new_episodes_action INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE FeedItems ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT, pubDate INTEGER, read INTEGER,"
                + "link TEXT, description TEXT, payment_link TEXT,"
                + "media INTEGER, feed INTEGER,"
                + "has_simple_chapters INTEGER, item_identifier TEXT,"
                + "image_url TEXT, auto_download INTEGER,"
                + "podcastindex_chapter_url TEXT,"
                + "podcastindex_transcript_type TEXT,"
                + "podcastindex_transcript_url TEXT,"
                + "social_interact_url TEXT)");

        db.execSQL("CREATE TABLE FeedMedia ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "duration INTEGER, file_url TEXT, download_url TEXT,"
                + "downloaded INTEGER, position INTEGER, filesize INTEGER,"
                + "mime_type TEXT, playback_completion_date INTEGER,"
                + "feeditem INTEGER, played_duration INTEGER,"
                + "has_embedded_picture INTEGER, last_played_time INTEGER)");

        db.execSQL("CREATE TABLE DownloadLog ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "feedfile INTEGER, feedfile_type INTEGER, reason INTEGER,"
                + "successful INTEGER, completion_date INTEGER,"
                + "reason_detailed TEXT, title TEXT)");

        db.execSQL("CREATE TABLE Queue ("
                + "id INTEGER PRIMARY KEY,"
                + "feeditem INTEGER, feed INTEGER)");

        db.execSQL("CREATE TABLE SimpleChapters ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT, start INTEGER, feeditem INTEGER,"
                + "link TEXT, image_url TEXT)");

        db.execSQL("CREATE TABLE Favorites ("
                + "id INTEGER PRIMARY KEY,"
                + "feeditem INTEGER, feed INTEGER)");

        // existing indexes
        db.execSQL("CREATE INDEX FeedItems_feed ON FeedItems(feed)");
        db.execSQL("CREATE INDEX FeedItems_pubDate ON FeedItems(pubDate)");
        db.execSQL("CREATE INDEX FeedItems_read ON FeedItems(read)");
        db.execSQL("CREATE INDEX Queue_feeditem ON Queue(feeditem)");
        db.execSQL("CREATE INDEX FeedMedia_feeditem ON FeedMedia(feeditem)");
        db.execSQL("CREATE INDEX SimpleChapters_feeditem ON SimpleChapters(feeditem)");
    }
}
