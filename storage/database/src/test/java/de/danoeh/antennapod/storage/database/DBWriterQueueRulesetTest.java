package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.QueueRuleset;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for ruleset operations in DBWriter.
 * Verifies createQueueRuleset, updateQueueRuleset, deleteQueueRuleset,
 * createRefillRule, updateRefillRule, deleteRefillRule, reorderRefillRules.
 */
@RunWith(RobolectricTestRunner.class)
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class DBWriterQueueRulesetTest {
    private Context context;
    private Feed feed;
    private long queueId;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        // Create test feed with items
        feed = createFeed(1, "Test Feed");
        for (int i = 0; i < 10; i++) {
            feed.getItems().add(createFeedItem(feed, "item-" + i, "Item " + i));
        }
        FeedDatabaseWriter.updateFeed(context, feed, false);

        // Create test queue
        queueId = DBWriter.createQueue("Test Queue", 0xFF0000).get();
    }

    @After
    public void tearDown() {
        DBWriter.tearDownTests();
    }

    // Helper methods
    private Feed createFeed(long feedId, String title) {
        Feed feed = new Feed("http://example.com/feed" + feedId, title, null);
        feed.setLink("http://example.com");
        feed.setItems(new ArrayList<>());
        return feed;
    }

    private FeedItem createFeedItem(Feed feed, String identifier, String title) {
        FeedItem item = new FeedItem();
        item.setFeed(feed);
        item.setItemIdentifier(identifier);
        item.setTitle(title);
        item.setLink("http://example.com/" + identifier);
        item.setPubDate(new Date());

        FeedMedia media = new FeedMedia(item, "http://example.com/media/" + identifier, 0, "audio/mpeg");
        item.setMedia(media);

        return item;
    }

    @Test
    public void testCreateQueueRuleset_CreatesRuleset() throws Exception {
        // Create ruleset for queue
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();

        // Verify ruleset exists
        assertTrue(rulesetId > 0);
        QueueRuleset ruleset = DBReader.getQueueRuleset(queueId);
        assertNotNull(ruleset);
        assertEquals(queueId, ruleset.getQueueId());
        assertTrue(ruleset.getCreatedAt() > 0);
        assertTrue(ruleset.getUpdatedAt() > 0);
    }

    @Test
    public void testCreateQueueRuleset_OneRulesetPerQueue() throws Exception {
        // Create first ruleset
        long rulesetId1 = DBWriter.createQueueRuleset(queueId).get();
        assertTrue(rulesetId1 > 0);

        // Try to create second ruleset for same queue - should fail or return existing
        try {
            long rulesetId2 = DBWriter.createQueueRuleset(queueId).get();
            // If it doesn't throw, it should return the same ID
            assertEquals(rulesetId1, rulesetId2);
        } catch (Exception e) {
            // Or it should throw an exception
            assertTrue(e.getMessage().contains("UNIQUE") || e.getMessage().contains("already exists"));
        }
    }

    @Test
    public void testUpdateQueueRuleset_UpdatesTimestamp() throws Exception {
        // Create ruleset
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();
        QueueRuleset ruleset = DBReader.getQueueRuleset(queueId);
        long originalUpdatedAt = ruleset.getUpdatedAt();

        // Wait a bit to ensure timestamp difference
        Thread.sleep(10);

        // Update ruleset
        DBWriter.updateQueueRuleset(rulesetId).get();

        // Verify updated timestamp
        QueueRuleset updatedRuleset = DBReader.getQueueRuleset(queueId);
        assertTrue(updatedRuleset.getUpdatedAt() > originalUpdatedAt);
    }

    @Test
    public void testDeleteQueueRuleset_DeletesRulesetAndRules() throws Exception {
        // Create ruleset and add rules
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();
        final long ruleId1 = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.NEWEST, 5, RefillRule.SourceType.FEED,
                String.valueOf(feed.getId())).get();
        final long ruleId2 = DBWriter.createRefillRule(rulesetId, 1, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.OLDEST, 3, RefillRule.SourceType.INBOX, null).get();

        // Delete ruleset
        DBWriter.deleteQueueRuleset(rulesetId).get();

        // Verify ruleset and rules are deleted
        assertNull(DBReader.getQueueRuleset(queueId));
        assertNull(DBReader.getRefillRule(ruleId1));
        assertNull(DBReader.getRefillRule(ruleId2));
    }

    @Test
    public void testCreateRefillRule_CreatesRule() throws Exception {
        // Create ruleset
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();

        // Create rule
        long ruleId = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.NEWEST, 5, RefillRule.SourceType.FEED,
                String.valueOf(feed.getId())).get();

        // Verify rule exists
        assertTrue(ruleId > 0);
        RefillRule rule = DBReader.getRefillRule(ruleId);
        assertNotNull(rule);
        assertEquals(rulesetId, rule.getRulesetId());
        assertEquals(0, rule.getPosition());
        assertEquals(RefillRule.RuleType.ADD_EPISODES, rule.getRuleType());
        assertEquals(RefillRule.SelectionMethod.NEWEST, rule.getSelectionMethod());
        assertEquals(Integer.valueOf(5), rule.getCount());
        assertEquals(RefillRule.SourceType.FEED, rule.getSourceType());
        assertEquals(String.valueOf(feed.getId()), rule.getSourceId());
    }

    @Test
    public void testCreateRefillRule_ClearQueueRule_OnlyOneAllowed() throws Exception {
        // Create ruleset
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();

        // Create first CLEAR_QUEUE rule
        long ruleId1 = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.CLEAR_QUEUE,
                null, null, null, null).get();
        assertTrue(ruleId1 > 0);

        // Try to create second CLEAR_QUEUE rule - should fail
        try {
            long ruleId2 = DBWriter.createRefillRule(rulesetId, 1, RefillRule.RuleType.CLEAR_QUEUE,
                    null, null, null, null).get();
            // If it doesn't throw, verify only one exists
            List<RefillRule> rules = DBReader.getRefillRules(rulesetId);
            long clearQueueCount = rules.stream()
                    .filter(r -> r.getRuleType() == RefillRule.RuleType.CLEAR_QUEUE)
                    .count();
            assertEquals(1, clearQueueCount);
        } catch (Exception e) {
            // Or it should throw an exception
            assertTrue(e.getMessage().contains("CLEAR_QUEUE") || e.getMessage().contains("already exists"));
        }
    }

    @Test
    public void testCreateRefillRule_ClearQueueRule_MovesToFirstPosition() throws Exception {
        // Create ruleset
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();

        // Create ADD_EPISODES rule at position 0
        long ruleId1 = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.NEWEST, 5, RefillRule.SourceType.FEED,
                String.valueOf(feed.getId())).get();

        // Create CLEAR_QUEUE rule at position 1 - should move to position 0
        long ruleId2 = DBWriter.createRefillRule(rulesetId, 1, RefillRule.RuleType.CLEAR_QUEUE,
                null, null, null, null).get();

        // Verify CLEAR_QUEUE is at position 0, ADD_EPISODES moved to position 1
        RefillRule clearRule = DBReader.getRefillRule(ruleId2);
        assertEquals(0, clearRule.getPosition());
        RefillRule addRule = DBReader.getRefillRule(ruleId1);
        assertEquals(1, addRule.getPosition());
    }

    @Test
    public void testUpdateRefillRule_UpdatesRule() throws Exception {
        // Create ruleset and rule
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();
        long ruleId = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.NEWEST, 5, RefillRule.SourceType.FEED,
                String.valueOf(feed.getId())).get();

        // Update rule
        DBWriter.updateRefillRule(ruleId, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.OLDEST, 10, RefillRule.SourceType.TAG, "test-tag").get();

        // Verify updates
        RefillRule updatedRule = DBReader.getRefillRule(ruleId);
        assertEquals(RefillRule.SelectionMethod.OLDEST, updatedRule.getSelectionMethod());
        assertEquals(Integer.valueOf(10), updatedRule.getCount());
        assertEquals(RefillRule.SourceType.TAG, updatedRule.getSourceType());
        assertEquals("test-tag", updatedRule.getSourceId());
    }

    @Test
    public void testDeleteRefillRule_DeletesRule() throws Exception {
        // Create ruleset and rules
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();
        long ruleId1 = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.NEWEST, 5, RefillRule.SourceType.FEED,
                String.valueOf(feed.getId())).get();
        long ruleId2 = DBWriter.createRefillRule(rulesetId, 1, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.OLDEST, 3, RefillRule.SourceType.INBOX, null).get();

        // Delete first rule
        DBWriter.deleteRefillRule(ruleId1).get();

        // Verify first rule is deleted, second rule still exists
        assertNull(DBReader.getRefillRule(ruleId1));
        assertNotNull(DBReader.getRefillRule(ruleId2));
    }

    @Test
    public void testReorderRefillRules_ReordersRules() throws Exception {
        // Create ruleset and rules
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();
        long ruleId1 = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.NEWEST, 5, RefillRule.SourceType.FEED,
                String.valueOf(feed.getId())).get();
        long ruleId2 = DBWriter.createRefillRule(rulesetId, 1, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.OLDEST, 3, RefillRule.SourceType.INBOX, null).get();
        long ruleId3 = DBWriter.createRefillRule(rulesetId, 2, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.RANDOM, 2, RefillRule.SourceType.TAG, "test-tag").get();

        // Reorder: ruleId3 -> position 0, ruleId1 -> position 1, ruleId2 -> position 2
        Map<Long, Integer> newPositions = new HashMap<>();
        newPositions.put(ruleId3, 0);
        newPositions.put(ruleId1, 1);
        newPositions.put(ruleId2, 2);
        DBWriter.reorderRefillRules(rulesetId, newPositions).get();

        // Verify new order
        assertEquals(0, DBReader.getRefillRule(ruleId3).getPosition());
        assertEquals(1, DBReader.getRefillRule(ruleId1).getPosition());
        assertEquals(2, DBReader.getRefillRule(ruleId2).getPosition());
    }

    @Test
    public void testReorderRefillRules_ClearQueueRule_ProtectedAtPosition0() throws Exception {
        // Create ruleset with CLEAR_QUEUE at position 0
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();
        long clearRuleId = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.CLEAR_QUEUE,
                null, null, null, null).get();
        long ruleId1 = DBWriter.createRefillRule(rulesetId, 1, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.NEWEST, 5, RefillRule.SourceType.FEED,
                String.valueOf(feed.getId())).get();

        // Try to reorder CLEAR_QUEUE rule away from position 0 - should fail or be ignored
        Map<Long, Integer> newPositions = new HashMap<>();
        newPositions.put(clearRuleId, 1);
        newPositions.put(ruleId1, 0);
        try {
            DBWriter.reorderRefillRules(rulesetId, newPositions).get();
            // If it doesn't throw, verify CLEAR_QUEUE is still at position 0
            assertEquals(0, DBReader.getRefillRule(clearRuleId).getPosition());
        } catch (Exception e) {
            // Or it should throw an exception
            assertTrue(e.getMessage().contains("CLEAR_QUEUE") || e.getMessage().contains("position 0"));
        }
    }

    @Test
    public void testGetQueueRuleset_ReturnsRuleset() throws Exception {
        // Create ruleset
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();

        // Get ruleset
        QueueRuleset ruleset = DBReader.getQueueRuleset(queueId);

        // Verify
        assertNotNull(ruleset);
        assertEquals(rulesetId, ruleset.getId());
        assertEquals(queueId, ruleset.getQueueId());
    }

    @Test
    public void testGetRefillRules_ReturnsRulesOrderedByPosition() throws Exception {
        // Create ruleset and rules in non-sequential order
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();
        final long ruleId1 = DBWriter.createRefillRule(rulesetId, 2, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.NEWEST, 5, RefillRule.SourceType.FEED,
                String.valueOf(feed.getId())).get();
        final long ruleId2 = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.OLDEST, 3, RefillRule.SourceType.INBOX, null).get();
        final long ruleId3 = DBWriter.createRefillRule(rulesetId, 1, RefillRule.RuleType.ADD_EPISODES,
                RefillRule.SelectionMethod.RANDOM, 2, RefillRule.SourceType.TAG, "test-tag").get();

        // Get rules
        List<RefillRule> rules = DBReader.getRefillRules(rulesetId);

        // Verify order
        assertEquals(3, rules.size());
        assertEquals(ruleId2, rules.get(0).getId());
        assertEquals(ruleId3, rules.get(1).getId());
        assertEquals(ruleId1, rules.get(2).getId());
    }

    @Test
    public void testHasClearQueueRule_ReturnsTrueIfExists() throws Exception {
        // Create ruleset
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();

        // Initially no CLEAR_QUEUE rule
        assertFalse(DBReader.hasClearQueueRule(rulesetId));

        // Create CLEAR_QUEUE rule
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.CLEAR_QUEUE,
                null, null, null, null).get();

        // Now should have CLEAR_QUEUE rule
        assertTrue(DBReader.hasClearQueueRule(rulesetId));
    }

    @Test
    public void testGetClearQueueRule_ReturnsClearQueueRule() throws Exception {
        // Create ruleset
        long rulesetId = DBWriter.createQueueRuleset(queueId).get();

        // Initially no CLEAR_QUEUE rule
        assertNull(DBReader.getClearQueueRule(rulesetId));

        // Create CLEAR_QUEUE rule
        long clearRuleId = DBWriter.createRefillRule(rulesetId, 0, RefillRule.RuleType.CLEAR_QUEUE,
                null, null, null, null).get();

        // Get CLEAR_QUEUE rule
        RefillRule clearRule = DBReader.getClearQueueRule(rulesetId);
        assertNotNull(clearRule);
        assertEquals(clearRuleId, clearRule.getId());
        assertEquals(RefillRule.RuleType.CLEAR_QUEUE, clearRule.getRuleType());
    }
}
