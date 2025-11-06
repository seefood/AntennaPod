package de.danoeh.antennapod.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MoveResultTest {

    @Test
    public void testMoveResult_ProperlyAggregates() {
        // Arrange
        int movedCount = 3;
        int skippedCount = 2;
        List<Long> skippedItemIds = Arrays.asList(100L, 101L);
        Map<Long, String> skipReasons = new HashMap<>();
        skipReasons.put(100L, "Episode not in source queue");
        skipReasons.put(101L, "Episode not in source queue");

        // Act
        MoveResult result = new MoveResult(movedCount, skippedCount, skippedItemIds, skipReasons);

        // Assert
        assertEquals("Moved count should match", movedCount, result.getMovedCount());
        assertEquals("Skipped count should match", skippedCount, result.getSkippedCount());
        assertEquals("Skipped item IDs should match", skippedItemIds, result.getSkippedItemIds());
        assertEquals("Skip reasons should match", skipReasons, result.getSkipReasons());
    }

    @Test
    public void testMoveResult_WithNullCollections() {
        // Act
        MoveResult result = new MoveResult(5, 0, null, null);

        // Assert
        assertEquals("Moved count should be 5", 5, result.getMovedCount());
        assertEquals("Skipped count should be 0", 0, result.getSkippedCount());
        assertTrue("Skipped item IDs should be empty", result.getSkippedItemIds().isEmpty());
        assertTrue("Skip reasons should be empty", result.getSkipReasons().isEmpty());
    }

    @Test
    public void testMoveResult_ImmutabilityOfCollections() {
        // Arrange
        List<Long> originalSkipped = new ArrayList<>(Arrays.asList(100L, 101L));
        Map<Long, String> originalReasons = new HashMap<>();
        originalReasons.put(100L, "Test reason");

        MoveResult result = new MoveResult(1, 2, originalSkipped, originalReasons);

        // Act - Get collections from result and try to modify them
        List<Long> returnedSkipped = result.getSkippedItemIds();
        Map<Long, String> returnedReasons = result.getSkipReasons();
        returnedSkipped.add(102L);
        returnedReasons.put(101L, "New reason");

        // Assert - Original result should not be affected
        assertEquals("Skipped items should remain 2", 2, result.getSkippedItemIds().size());
        assertEquals("Reasons should remain 1", 1, result.getSkipReasons().size());
        assertTrue("Returned list should not contain 102L", !result.getSkippedItemIds().contains(102L));
    }
}
