package de.danoeh.antennapod.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CopyResultTest {

    @Test
    public void testCopyResult_ProperlyAggregates() {
        // Arrange
        int copiedCount = 4;
        int skippedCount = 1;
        List<Long> skippedItemIds = Arrays.asList(105L);
        Map<Long, String> skipReasons = new HashMap<>();
        skipReasons.put(105L, "Episode already in target queue");

        // Act
        CopyResult result = new CopyResult(copiedCount, skippedCount, skippedItemIds, skipReasons);

        // Assert
        assertEquals("Copied count should match", copiedCount, result.getCopiedCount());
        assertEquals("Skipped count should match", skippedCount, result.getSkippedCount());
        assertEquals("Skipped item IDs should match", skippedItemIds, result.getSkippedItemIds());
        assertEquals("Skip reasons should match", skipReasons, result.getSkipReasons());
    }

    @Test
    public void testCopyResult_WithNullCollections() {
        // Act
        CopyResult result = new CopyResult(3, 0, null, null);

        // Assert
        assertEquals("Copied count should be 3", 3, result.getCopiedCount());
        assertEquals("Skipped count should be 0", 0, result.getSkippedCount());
        assertTrue("Skipped item IDs should be empty", result.getSkippedItemIds().isEmpty());
        assertTrue("Skip reasons should be empty", result.getSkipReasons().isEmpty());
    }

    @Test
    public void testCopyResult_ImmutabilityOfCollections() {
        // Arrange
        List<Long> originalSkipped = new ArrayList<>(Arrays.asList(105L, 106L));
        Map<Long, String> originalReasons = new HashMap<>();
        originalReasons.put(105L, "Duplicate");

        CopyResult result = new CopyResult(2, 2, originalSkipped, originalReasons);

        // Act - Get collections from result and try to modify them
        List<Long> returnedSkipped = result.getSkippedItemIds();
        Map<Long, String> returnedReasons = result.getSkipReasons();
        returnedSkipped.add(107L);
        returnedReasons.put(106L, "Another duplicate");

        // Assert - Original result should not be affected
        assertEquals("Skipped items should remain 2", 2, result.getSkippedItemIds().size());
        assertEquals("Reasons should remain 1", 1, result.getSkipReasons().size());
        assertTrue("Returned list should not contain 107L", !result.getSkippedItemIds().contains(107L));
    }
}
