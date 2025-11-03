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
 * Displays color circles with selection indicator (checkmark).
 */
public class QueueColorAdapter extends BaseAdapter {
    private final Context context;
    private final int[] colors;
    private int selectedPosition = 0;
    private int colorSize;

    public QueueColorAdapter(Context context, int[] colors, @ColorInt int currentColor) {
        this.context = context;
        this.colors = colors;
        this.colorSize = (int) (56 * context.getResources().getDisplayMetrics().density);

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
            params.setMargins(8, 8, 8, 8);
            container.setLayoutParams(params);
        } else {
            if (convertView instanceof FrameLayout) {
                container = (FrameLayout) convertView;
            } else {
                container = new FrameLayout(context);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        colorSize, colorSize);
                params.setMargins(8, 8, 8, 8);
                container.setLayoutParams(params);
            }
        }
        container.removeAllViews();

        // Create color circle
        View colorCircle = new View(context);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(
                colorSize - 16, colorSize - 16);
        circleParams.setMargins(8, 8, 8, 8);
        colorCircle.setLayoutParams(circleParams);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(colors[position]);
        colorCircle.setBackground(drawable);

        container.addView(colorCircle);

        // Add checkmark if selected
        if (position == selectedPosition) {
            ImageView checkmark = new ImageView(context);
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                    colorSize - 16, colorSize - 16);
            checkParams.setMargins(8, 8, 8, 8);
            checkmark.setLayoutParams(checkParams);
            checkmark.setImageResource(android.R.drawable.ic_menu_view);
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
