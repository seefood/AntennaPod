package de.danoeh.antennapod.ui.common;

import android.content.Context;

import androidx.annotation.ColorInt;

import de.danoeh.antennapod.R;

/**
 * Queue color palette utility.
 *
 * Provides a predefined set of 12 colors that match the AntennaPod theme for queue identification.
 * Colors are designed to be visually distinct and accessible.
 */
public class QueueColorPalette {
    private static final int[] COLORS = new int[12];
    private static boolean initialized = false;

    /**
     * Initialize the color palette from Android resources.
     * Must be called once with application context before using any palette methods.
     *
     * @param context Android context for accessing resources
     */
    public static void initialize(Context context) {
        if (initialized) {
            return;
        }

        // Load 12 theme-matched colors from resources
        // Colors are defined in res/values/colors.xml with keys:
        // queue_color_1, queue_color_2, ..., queue_color_12
        COLORS[0] = context.getColor(R.color.queue_color_1);
        COLORS[1] = context.getColor(R.color.queue_color_2);
        COLORS[2] = context.getColor(R.color.queue_color_3);
        COLORS[3] = context.getColor(R.color.queue_color_4);
        COLORS[4] = context.getColor(R.color.queue_color_5);
        COLORS[5] = context.getColor(R.color.queue_color_6);
        COLORS[6] = context.getColor(R.color.queue_color_7);
        COLORS[7] = context.getColor(R.color.queue_color_8);
        COLORS[8] = context.getColor(R.color.queue_color_9);
        COLORS[9] = context.getColor(R.color.queue_color_10);
        COLORS[10] = context.getColor(R.color.queue_color_11);
        COLORS[11] = context.getColor(R.color.queue_color_12);

        initialized = true;
    }

    /**
     * Get the color palette as an array.
     *
     * @return Array of 12 @ColorInt values
     * @throws IllegalStateException if palette has not been initialized
     */
    public static int[] getColors() {
        if (!initialized) {
            throw new IllegalStateException("QueueColorPalette must be initialized with Context first");
        }
        return COLORS.clone();
    }

    /**
     * Get a specific color from the palette by index.
     *
     * @param index Color index (0-11)
     * @return RGB color value
     * @throws IllegalStateException if palette has not been initialized
     * @throws IndexOutOfBoundsException if index is out of range
     */
    @ColorInt
    public static int getColor(int index) {
        if (!initialized) {
            throw new IllegalStateException("QueueColorPalette must be initialized with Context first");
        }
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
        if (!initialized) {
            throw new IllegalStateException("QueueColorPalette must be initialized with Context first");
        }

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

        long rDist = (long) (r1 - r2);
        long gDist = (long) (g1 - g2);
        long bDist = (long) (b1 - b2);

        return rDist * rDist + gDist * gDist + bDist * bDist;
    }
}
