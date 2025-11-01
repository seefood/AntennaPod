package de.danoeh.antennapod.ui.common;

import androidx.annotation.ColorInt;

/**
 * Queue color palette utility.
 *
 * Provides a predefined set of 12 colors that match the AntennaPod theme for queue identification.
 * Colors are designed to be visually distinct and accessible.
 *
 * Colors are defined as hex values and can be used directly without resource loading,
 * ensuring compatibility with API level 21+.
 */
public class QueueColorPalette {
    // 12 theme-matched colors for queue identification
    // These match the palette in res/values/colors.xml (queue_color_1 through queue_color_12)
    private static final int[] COLORS = {
        0xFFFF6B6B,  // queue_color_1: Red
        0xFFFF8C42,  // queue_color_2: Orange
        0xFFFFD93D,  // queue_color_3: Yellow
        0xFF6BCF7F,  // queue_color_4: Green
        0xFF4ECDC4,  // queue_color_5: Teal
        0xFF45B7D1,  // queue_color_6: Cyan
        0xFF5C63A3,  // queue_color_7: Purple
        0xFF9C27B0,  // queue_color_8: Magenta
        0xFFE91E63,  // queue_color_9: Pink
        0xFFFF6F00,  // queue_color_10: Deep Orange
        0xFF1976D2,  // queue_color_11: Blue
        0xFF00897B   // queue_color_12: Teal
    };

    /**
     * Get the color palette as an array.
     *
     * @return Array of 12 @ColorInt values
     */
    public static int[] getColors() {
        return COLORS.clone();
    }

    /**
     * Get a specific color from the palette by index.
     *
     * @param index Color index (0-11)
     * @return RGB color value
     * @throws IndexOutOfBoundsException if index is out of range
     */
    @ColorInt
    public static int getColor(int index) {
        if (index < 0 || index >= COLORS.length) {
            throw new IndexOutOfBoundsException("Color index must be between 0 and " + (COLORS.length - 1));
        }
        return COLORS[index];
    }

    /**
     * Get the number of colors in the palette.
     *
     * @return Always returns 12
     */
    public static int getColorCount() {
        return 12;
    }

    /**
     * Check if a color is in the palette.
     *
     * @param color RGB color value to check
     * @return true if color is in the palette, false otherwise
     */
    public static boolean contains(@ColorInt int color) {
        for (int c : COLORS) {
            if (c == color) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the closest palette color to a given color.
     *
     * @param color RGB color value
     * @return Index of the closest color in the palette (0-11)
     */
    public static int findClosestColorIndex(@ColorInt int color) {
        int closestIndex = 0;
        long minDistance = Long.MAX_VALUE;

        for (int i = 0; i < COLORS.length; i++) {
            long distance = colorDistance(color, COLORS[i]);
            if (distance < minDistance) {
                minDistance = distance;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    /**
     * Calculate the Euclidean distance between two colors in RGB space.
     *
     * @param color1 First RGB color
     * @param color2 Second RGB color
     * @return Distance value
     */
    private static long colorDistance(@ColorInt int color1, @ColorInt int color2) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        long rDist = r1 - r2;
        long gDist = g1 - g2;
        long bDist = b1 - b2;

        return rDist * rDist + gDist * gDist + bDist * bDist;
    }
}
