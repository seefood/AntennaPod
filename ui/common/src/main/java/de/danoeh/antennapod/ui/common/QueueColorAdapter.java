package de.danoeh.antennapod.ui.common;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;

/**
 * Adapter for queue color picker grid.
 * Displays color squares with selection indicator (checkmark).
 */
public class QueueColorAdapter extends BaseAdapter {
    private final Context context;
    private final int[] colors;
    private int selectedPosition = 0;
    private int colorSize;

    public QueueColorAdapter(Context context, int[] colors, @ColorInt int currentColor) {
        this.context = context;
        this.colors = colors;
        this.colorSize = (int) (36 * context.getResources().getDisplayMetrics().density);

        // Find matching color
        if (currentColor != 0) {
            for (int i = 0; i < colors.length; i++) {
                if (colors[i] == currentColor) {
                    selectedPosition = i;
                    break;
                }
            }
        }
    }

    @Override
    public int getCount() {
        return colors.length;
    }

    @Override
    public Object getItem(int position) {
        return colors[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        FrameLayout container;
        if (convertView == null) {
            container = new FrameLayout(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    colorSize, colorSize);
            params.setMargins(4, 4, 4, 4);
            container.setLayoutParams(params);
        } else {
            if (convertView instanceof FrameLayout) {
                container = (FrameLayout) convertView;
            } else {
                container = new FrameLayout(context);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        colorSize, colorSize);
                params.setMargins(4, 4, 4, 4);
                container.setLayoutParams(params);
            }
        }
        container.removeAllViews();

        // Create color square
        View colorSquare = new View(context);
        FrameLayout.LayoutParams squareParams = new FrameLayout.LayoutParams(
                colorSize - 8, colorSize - 8);
        squareParams.setMargins(4, 4, 4, 4);
        colorSquare.setLayoutParams(squareParams);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(colors[position]);
        colorSquare.setBackground(drawable);

        container.addView(colorSquare);

        // Add checkmark if selected
        if (position == selectedPosition) {
            ImageView checkmark = new ImageView(context);
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                    colorSize - 8, colorSize - 8);
            checkParams.setMargins(4, 4, 4, 4);
            checkmark.setLayoutParams(checkParams);
            checkmark.setImageResource(R.drawable.ic_check_mark);
            checkmark.setScaleType(ImageView.ScaleType.CENTER);

            // Determine text color based on background brightness
            int textColor = ColorUtils.calculateLuminance(colors[position]) > 0.5
                    ? 0xFF000000 // Dark text for light background
                    : 0xFFFFFFFF; // Light text for dark background

            checkmark.setColorFilter(textColor);
            container.addView(checkmark);
        }

        return container;
    }

    public int getSelectedColor() {
        return colors[selectedPosition];
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
    }
}
