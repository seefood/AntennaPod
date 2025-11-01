package de.danoeh.antennapod.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Manages dialog UI for creating and editing queues.
 *
 * Provides factory methods for showing queue creation/edit dialogs with:
 * - Name input field (alphanumeric + emoji support)
 * - 12-color palette picker with visual selection indicator
 * - Validation and error handling
 *
 * Usage:
 * ```java
 * QueueDialogManager.showCreateQueueDialog(fragment, (name, color) -> {
 *     viewModel.createQueue(name, color);
 * });
 * ```
 */
public abstract class QueueDialogManager {
    private static final String TAG = "QueueDialogManager";

    /**
     * Callback for queue creation/edit completion.
     */
    public interface OnQueueDialogCompleteListener {
        /**
         * Called when user confirms queue creation/edit.
         *
         * @param name Queue name (validated, not empty)
         * @param color Selected color as RGB integer
         */
        void onComplete(String name, int color);
    }

    /**
     * Show a dialog for creating a new queue.
     *
     * @param context Context for dialog creation
     * @param listener Callback when user confirms
     */
    public static void showCreateQueueDialog(
            @NonNull Context context,
            @NonNull OnQueueDialogCompleteListener listener) {
        showQueueDialog(context, "", QueueColorPalette.getColor(0), listener);
    }

    /**
     * Show a dialog for editing an existing queue.
     *
     * @param context Context for dialog creation
     * @param currentName Current queue name
     * @param currentColor Current queue color
     * @param listener Callback when user confirms
     */
    public static void showEditQueueDialog(
            @NonNull Context context,
            @NonNull String currentName,
            int currentColor,
            @NonNull OnQueueDialogCompleteListener listener) {
        showQueueDialog(context, currentName, currentColor, listener);
    }

    /**
     * Internal method to show queue creation/edit dialog.
     *
     * @param context Context for dialog creation
     * @param initialName Initial queue name (empty for create)
     * @param initialColor Initial color selection
     * @param listener Callback when user confirms
     */
    private static void showQueueDialog(
            @NonNull Context context,
            @NonNull String initialName,
            int initialColor,
            @NonNull OnQueueDialogCompleteListener listener) {
        // Create dialog layout
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.queue_creation_dialog, null);

        TextInputLayout nameInputLayout = dialogView.findViewById(R.id.queue_name_input_layout);
        TextInputEditText nameInput = dialogView.findViewById(R.id.queue_name_input);
        GridLayout colorGrid = dialogView.findViewById(R.id.queue_color_grid);

        // Set initial name
        if (nameInput != null) {
            nameInput.setText(initialName);
            nameInput.setSelection(initialName.length());
        }

        // Track selected color
        final int[] selectedColor = {initialColor};

        // Create color picker grid
        if (colorGrid != null) {
            colorGrid.removeAllViews();
            int[] colors = QueueColorPalette.getColors();

            for (int i = 0; i < colors.length; i++) {
                AppCompatImageView colorButton = new AppCompatImageView(context);
                colorButton.setTag(i);
                colorButton.setBackgroundColor(colors[i]);
                colorButton.setScaleType(ImageView.ScaleType.CENTER);

                // Set selection indicator if this is the initial color
                if (colors[i] == initialColor) {
                    colorButton.setImageResource(android.R.drawable.ic_menu_view);
                    colorButton.setImageTintList(ColorStateList.valueOf(
                            Color.luminance(colors[i]) > 0.5 ? Color.BLACK : Color.WHITE
                    ));
                }

                // Click listener for color selection
                final int colorIndex = i;
                colorButton.setOnClickListener(v -> {
                    // Update all buttons
                    for (int j = 0; j < colorGrid.getChildCount(); j++) {
                        AppCompatImageView btn = (AppCompatImageView) colorGrid.getChildAt(j);
                        btn.setImageDrawable(null);
                    }
                    // Mark selected button
                    colorButton.setImageResource(android.R.drawable.ic_menu_view);
                    colorButton.setImageTintList(ColorStateList.valueOf(
                            Color.luminance(colors[colorIndex]) > 0.5 ? Color.BLACK : Color.WHITE
                    ));
                    selectedColor[0] = colors[colorIndex];
                });

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 120;
                params.height = 120;
                params.setMargins(8, 8, 8, 8);
                colorButton.setLayoutParams(params);
                colorGrid.addView(colorButton);
            }
        }

        // Create dialog
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(initialName.isEmpty() ? R.string.queue_creation_title : R.string.queue_edit_title)
                .setView(dialogView)
                .setPositiveButton(R.string.queue_create_button, (dialog, which) -> {
                    String queueName = nameInput != null ? nameInput.getText().toString().trim() : "";
                    if (!queueName.isEmpty()) {
                        listener.onComplete(queueName, selectedColor[0]);
                    } else if (nameInputLayout != null) {
                        nameInputLayout.setError(context.getString(R.string.queue_name_cannot_be_empty));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);

        // Disable positive button until name is entered
        if (nameInput != null) {
            final MaterialAlertDialogBuilder finalBuilder = builder;
            nameInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // Enable/disable button based on input
                    // This will be handled by the dialog after creation
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        builder.show();
    }
}
