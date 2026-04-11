package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.RefillRule;
import de.danoeh.antennapod.model.feed.QueueRuleset;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DBWriterQueueRulesetTest {
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

    // --- Ruleset CRUD ---

    @Test
    public void testCreateRulesetIsIdempotent() throws Exception {
        long id1 = DBWriter.createQueueRuleset(QUEUE_ID).get();
        long id2 = DBWriter.createQueueRuleset(QUEUE_ID).get();
        assertEquals("second call must return existing id", id1, id2);
    }

    @Test
    public void testUpdateRulesetBumpsUpdatedAt() throws Exception {
        DBWriter.createQueueRuleset(QUEUE_ID).get();
        QueueRuleset before = DBReader.getQueueRuleset(QUEUE_ID);
        assertNotNull(before);

        Thread.sleep(2); // ensure time advances
        DBWriter.updateQueueRuleset(before.getId()).get();

        QueueRuleset after = DBReader.getQueueRuleset(QUEUE_ID);
        assertNotNull(after);
        assertTrue("updated_at must be >= created_at before + 1 ms",
                after.getUpdatedAt() > before.getUpdatedAt());
    }

    @Test
    public void testDeleteRulesetCascadesRules() throws Exception {
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                3, RefillRule.SourceType.INBOX, null).get();

        DBWriter.deleteQueueRuleset(rulesetId).get();

        assertNull("Ruleset must be gone", DBReader.getQueueRuleset(QUEUE_ID));
        List<RefillRule> rules = DBReader.getRefillRules(rulesetId);
        assertTrue("All rules must be deleted via cascade", rules.isEmpty());
    }

    // --- Rule CRUD ---

    @Test
    public void testCreateRefillRuleShiftsPositions() throws Exception {
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.NEWEST,
                5, RefillRule.SourceType.INBOX, null).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                3, RefillRule.SourceType.INBOX, null).get();

        List<RefillRule> rules = DBReader.getRefillRules(rulesetId);
        assertEquals(2, rules.size());
        assertEquals("inserted-at-0 rule is at position 0", 0, rules.get(0).getPosition());
        assertEquals("original rule shifted to position 1", 1, rules.get(1).getPosition());
    }

    @Test
    public void testDeleteRefillRuleCompacts() throws Exception {
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                2, RefillRule.SourceType.INBOX, null).get();
        DBWriter.createRefillRule(rulesetId, 1, RefillRule.SelectionMethod.NEWEST,
                2, RefillRule.SourceType.INBOX, null).get();
        DBWriter.createRefillRule(rulesetId, 2, RefillRule.SelectionMethod.RANDOM,
                2, RefillRule.SourceType.INBOX, null).get();

        List<RefillRule> before = DBReader.getRefillRules(rulesetId);
        long middleId = before.get(1).getId();
        DBWriter.deleteRefillRule(middleId).get();

        List<RefillRule> after = DBReader.getRefillRules(rulesetId);
        assertEquals(2, after.size());
        assertEquals("positions must be compacted to 0,1", 0, after.get(0).getPosition());
        assertEquals("positions must be compacted to 0,1", 1, after.get(1).getPosition());
    }

    @Test
    public void testReorderRefillRulesAssignsPositions() throws Exception {
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        long id0 = DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                1, RefillRule.SourceType.INBOX, null).get();
        long id1 = DBWriter.createRefillRule(rulesetId, 1, RefillRule.SelectionMethod.NEWEST,
                1, RefillRule.SourceType.INBOX, null).get();
        long id2 = DBWriter.createRefillRule(rulesetId, 2, RefillRule.SelectionMethod.RANDOM,
                1, RefillRule.SourceType.INBOX, null).get();

        // Reorder: 2, 0, 1
        DBWriter.reorderRefillRules(rulesetId, Arrays.asList(id2, id0, id1)).get();

        List<RefillRule> rules = DBReader.getRefillRules(rulesetId);
        assertEquals(3, rules.size());
        assertEquals("id2 must be at position 0", id2, rules.get(0).getId());
        assertEquals("id0 must be at position 1", id0, rules.get(1).getId());
        assertEquals("id1 must be at position 2", id1, rules.get(2).getId());
    }

    @Test
    public void testUpdateRefillRuleSavesNewValues() throws Exception {
        long rulesetId = DBWriter.createQueueRuleset(QUEUE_ID).get();
        long ruleId = DBWriter.createRefillRule(rulesetId, 0, RefillRule.SelectionMethod.OLDEST,
                2, RefillRule.SourceType.INBOX, null).get();

        DBWriter.updateRefillRule(ruleId, RefillRule.SelectionMethod.NEWEST, 7,
                RefillRule.SourceType.INBOX, null).get();

        RefillRule updated = DBReader.getRefillRule(ruleId);
        assertNotNull(updated);
        assertEquals(RefillRule.SelectionMethod.NEWEST, updated.getSelectionMethod());
        assertEquals(7, updated.getCount());
    }
}
