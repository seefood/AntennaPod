package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.QueueRuleset;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DBReaderQueueRulesetTest {
    private static final long QUEUE_ID = 1L;

    @Before
    public void setUp() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PodDBAdapter.init(context);
        DBWriter.tearDownTests(); // flush any pending executor tasks from prior test
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    @After
    public void tearDown() throws Exception {
        DBWriter.tearDownTests();
    }

    @Test
    public void testGetQueueRulesetReturnsCorrectObject() throws Exception {
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();

        QueueRuleset ruleset = DBReader.getQueueRuleset(QUEUE_ID);
        assertNotNull(ruleset);
        assertEquals(rulesetId, ruleset.getId());
        assertEquals(QUEUE_ID, ruleset.getQueueId());
        assertTrue("created_at must be positive", ruleset.getCreatedAt() > 0);
        assertTrue("updated_at must be positive", ruleset.getUpdatedAt() > 0);
    }

    @Test
    public void testGetQueueRulesetReturnsNullForMissing() {
        assertNull("No ruleset should return null", DBReader.getQueueRuleset(QUEUE_ID));
    }

    @Test
    public void testHasQueueRulesetReturnsTrueAfterCreate() throws Exception {
        assertFalse("Should be false before creation", DBReader.hasQueueRuleset(QUEUE_ID));
        DBWriter.createQueueRuleset(QUEUE_ID).get();
        assertTrue("Should be true after creation", DBReader.hasQueueRuleset(QUEUE_ID));
    }

    @Test
    public void testGetRefillRulesOrderedByPosition() throws Exception {
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                1, RefillRule.SourceType.INBOX, null).get();
        DBWriter.createRefillRule(rulesetId, 1, RefillRule.SelectionMethod.NEWEST,
                2, RefillRule.SourceType.INBOX, null).get();
        DBWriter.createRefillRule(rulesetId, 2, RefillRule.SelectionMethod.RANDOM,
                3, RefillRule.SourceType.INBOX, null).get();

        List<RefillRule> rules = DBReader.getRefillRules(rulesetId);
        assertEquals(3, rules.size());
        assertEquals(0, rules.get(0).getPosition());
        assertEquals(1, rules.get(1).getPosition());
        assertEquals(2, rules.get(2).getPosition());
        assertEquals(RefillRule.SelectionMethod.OLDEST, rules.get(0).getSelectionMethod());
        assertEquals(1, rules.get(0).getCount());
    }

    @Test
    public void testGetRefillRuleById() throws Exception {
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        long ruleId = DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.NEWEST,
                5, RefillRule.SourceType.INBOX, null).get();

        RefillRule rule = DBReader.getRefillRule(ruleId);
        assertNotNull(rule);
        assertEquals(ruleId, rule.getId());
        assertEquals(rulesetId, rule.getRulesetId());
        assertEquals(0, rule.getPosition());
        assertEquals(RefillRule.SelectionMethod.NEWEST, rule.getSelectionMethod());
        assertEquals(5, rule.getCount());
        assertEquals(RefillRule.SourceType.INBOX, rule.getSourceType());
        assertNull("INBOX source_id must be null", rule.getSourceId());
    }

    @Test
    public void testGetRefillRulesByRulesetIdReturnsEmptyForMissing() {
        List<RefillRule> rules = DBReader.getRefillRules(999L);
        assertNotNull(rules);
        assertTrue("Should return empty list for unknown rulesetId", rules.isEmpty());
    }
}
