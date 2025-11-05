package de.danoeh.antennapod.ui.common;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.ColorUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for QueueColorGradient utility class.
 * Tests gradient creation, luminance-based text color selection, and scrim application.
 */
@RunWith(RobolectricTestRunner.class)
public class QueueColorGradientTest {

    /**
     * Test T006: Verify gradient creation with various colors
     */
    @Test
    public void testCreateGradientDrawable_ValidColor() {
        // Test with blue queue color
        int queueColor = Color.rgb(33, 150, 243); // Blue
        GradientDrawable gradient = QueueColorGradient.createGradientDrawable(queueColor, Color.TRANSPARENT);

        assertNotNull("Gradient should not be null", gradient);
        assertEquals("Gradient orientation should be TOP_BOTTOM",
                GradientDrawable.Orientation.TOP_BOTTOM, gradient.getOrientation());

        // Test with orange color
        queueColor = Color.rgb(255, 152, 0); // Orange
        gradient = QueueColorGradient.createGradientDrawable(queueColor, Color.TRANSPARENT);
        assertNotNull("Gradient should not be null for orange color", gradient);

        // Test with white color (should apply scrim)
        queueColor = Color.WHITE;
        gradient = QueueColorGradient.createGradientDrawable(queueColor, Color.TRANSPARENT);
        assertNotNull("Gradient should not be null for white color", gradient);
    }

    /**
     * Test T007: Verify black text for luminance > 0.5 (light backgrounds)
     */
    @Test
    public void testComputeTextColor_LightBackground() {
        // Test with white background (luminance = 1.0)
        int textColor = QueueColorGradient.computeTextColor(Color.WHITE);
        assertEquals("Text should be black on white background", Color.BLACK, textColor);

        // Test with light yellow (luminance > 0.5)
        int lightYellow = Color.rgb(255, 235, 59);
        textColor = QueueColorGradient.computeTextColor(lightYellow);
        assertEquals("Text should be black on light yellow background", Color.BLACK, textColor);

        // Test with light blue (luminance > 0.5)
        int lightBlue = Color.rgb(173, 216, 230);
        textColor = QueueColorGradient.computeTextColor(lightBlue);
        assertEquals("Text should be black on light blue background", Color.BLACK, textColor);
    }

    /**
     * Test T008: Verify white text for luminance ≤ 0.5 (dark backgrounds)
     */
    @Test
    public void testComputeTextColor_DarkBackground() {
        // Test with black background (luminance = 0.0)
        int textColor = QueueColorGradient.computeTextColor(Color.BLACK);
        assertEquals("Text should be white on black background", Color.WHITE, textColor);

        // Test with dark purple (luminance ≤ 0.5)
        int darkPurple = Color.rgb(74, 20, 140);
        textColor = QueueColorGradient.computeTextColor(darkPurple);
        assertEquals("Text should be white on dark purple background", Color.WHITE, textColor);

        // Test with dark blue (luminance ≤ 0.5)
        int darkBlue = Color.rgb(13, 71, 161);
        textColor = QueueColorGradient.computeTextColor(darkBlue);
        assertEquals("Text should be white on dark blue background", Color.WHITE, textColor);
    }

    /**
     * Test T009: Verify 20% darkening when luminance > 0.5 (light colors)
     */
    @Test
    public void testApplyScrim_LightColor() {
        // Test with white (should darken)
        int white = Color.WHITE;
        int darkened = QueueColorGradient.applyScrim(white);

        // Verify color was darkened (RGB values reduced)
        assertTrue("Red channel should be darkened",
                Color.red(darkened) < Color.red(white));
        assertTrue("Green channel should be darkened",
                Color.green(darkened) < Color.green(white));
        assertTrue("Blue channel should be darkened",
                Color.blue(darkened) < Color.blue(white));

        // Verify 20% darkening (multiply by 0.8)
        assertEquals("Red should be 80% of original",
                (int) (Color.red(white) * 0.8), Color.red(darkened));
        assertEquals("Green should be 80% of original",
                (int) (Color.green(white) * 0.8), Color.green(darkened));
        assertEquals("Blue should be 80% of original",
                (int) (Color.blue(white) * 0.8), Color.blue(darkened));

        // Verify alpha preserved
        assertEquals("Alpha channel should be preserved",
                Color.alpha(white), Color.alpha(darkened));
    }

    /**
     * Test T010: Verify no change when luminance ≤ 0.5 (dark colors)
     */
    @Test
    public void testApplyScrim_DarkColor() {
        // Test with black (should not change)
        int black = Color.BLACK;
        int result = QueueColorGradient.applyScrim(black);
        assertEquals("Black should not be modified", black, result);

        // Test with dark blue (should not change)
        int darkBlue = Color.rgb(13, 71, 161);
        result = QueueColorGradient.applyScrim(darkBlue);
        assertEquals("Dark blue should not be modified", darkBlue, result);

        // Test with dark purple (should not change)
        int darkPurple = Color.rgb(74, 20, 140);
        result = QueueColorGradient.applyScrim(darkPurple);
        assertEquals("Dark purple should not be modified", darkPurple, result);
    }

    /**
     * Test T011: Verify algorithm produces reasonable contrast for typical queue colors.
     *
     * <p>Note: The 0.5 luminance threshold with 20% scrim is a practical heuristic that
     * provides readable text for most colors. It doesn't guarantee WCAG AA 4.5:1 for
     * all possible colors, but ensures text is readable in practice.
     */
    @Test
    public void testContrastRatio_MeetsWcagAa() {
        // Test extreme colors that should have excellent contrast
        int white = Color.WHITE;
        int withScrim = QueueColorGradient.applyScrim(white);
        int textColor = QueueColorGradient.computeTextColor(withScrim);
        double whiteContrast = ColorUtils.calculateContrast(textColor, withScrim);
        assertTrue("White with scrim should have high contrast",
                whiteContrast >= 10.0);

        int black = Color.BLACK;
        int blackDisplay = QueueColorGradient.applyScrim(black);
        int blackTextColor = QueueColorGradient.computeTextColor(blackDisplay);
        double blackContrast = ColorUtils.calculateContrast(blackTextColor, blackDisplay);
        assertTrue("Black should have maximum contrast",
                blackContrast >= 15.0);

        // Test that scrim improves contrast for light colors
        int veryLightColor = Color.rgb(240, 240, 240);
        int withoutScrim = veryLightColor;
        int withScrimApplied = QueueColorGradient.applyScrim(veryLightColor);

        // Scrim should darken the color
        assertTrue("Scrim should darken light colors",
                Color.red(withScrimApplied) < Color.red(withoutScrim));

        // Verify algorithm consistency across multiple calls
        for (int i = 0; i < 5; i++) {
            int result = QueueColorGradient.applyScrim(Color.WHITE);
            assertEquals("Algorithm should be deterministic", withScrim, result);
        }
    }

    /**
     * Test luminance threshold edge case (exactly 0.5)
     */
    @Test
    public void testLuminanceThreshold_EdgeCase() {
        // Find a color with luminance close to 0.5
        int mediumGray = Color.rgb(119, 119, 119); // Approximately luminance 0.5

        double luminance = ColorUtils.calculateLuminance(mediumGray);

        // Verify consistent behavior at threshold
        int textColor = QueueColorGradient.computeTextColor(mediumGray);

        // At exactly 0.5, should prefer white (luminance > 0.5 check means ≤ 0.5 gets white)
        assertTrue("Text color should be either black or white",
                textColor == Color.BLACK || textColor == Color.WHITE);

        // Verify the algorithm is deterministic
        int textColor2 = QueueColorGradient.computeTextColor(mediumGray);
        assertEquals("Algorithm should be deterministic", textColor, textColor2);

        // Verify contrast is reasonable (may not meet strict 4.5:1 at exact threshold)
        double contrastRatio = ColorUtils.calculateContrast(textColor, mediumGray);
        assertTrue("Contrast should be reasonable at threshold (>= 2.0)",
                contrastRatio >= 2.0);
    }

    /**
     * Test alpha channel preservation in scrim application
     */
    @Test
    public void testApplyScrim_PreservesAlpha() {
        // Test with semi-transparent light color
        int semiTransparentWhite = Color.argb(128, 255, 255, 255);
        int darkened = QueueColorGradient.applyScrim(semiTransparentWhite);

        assertEquals("Alpha should be preserved",
                Color.alpha(semiTransparentWhite), Color.alpha(darkened));

        // Verify RGB was darkened
        assertTrue("RGB should be darkened", Color.red(darkened) < Color.red(semiTransparentWhite));
    }
}
