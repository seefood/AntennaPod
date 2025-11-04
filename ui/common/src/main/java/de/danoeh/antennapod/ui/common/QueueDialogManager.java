package de.danoeh.antennapod.ui.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.GridView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Factory class for creating and showing queue-related dialogs.
 *
 * <p>Provides static methods to show:
 * - Queue creation dialog (name + color)
 * - Queue rename dialog
 * - Queue color picker dialog
 * - Queue deletion confirmation dialog
 */
public class QueueDialogManager {
    private static final String TAG = "QueueDialogManager";

    /**
     * Callback interface for queue creation/editing operations.
     */
    public interface QueueNameColorCallback {
        /**
         * Called when user confirms queue creation or rename.
         *
         * @param name Queue name (non-empty)
         * @param color Selected color as ARGB int
         */
        void onConfirm(String name, @ColorInt int color);

        /**
         * Called when dialog is cancelled.
         */
        void onCancel();

        /**
         * Called when user requests queue deletion (edit mode only).
         * Default implementation does nothing.
         */
        default void onDelete() {
        }
    }

    /**
     * Callback interface for queue deletion.
     */
    public interface QueueDeleteCallback {
        /**
         * Called when user confirms deletion.
         */
        void onConfirmDelete();

        /**
         * Called when user cancels deletion.
         */
        void onCancel();
    }

    /**
     * Callback interface for color picker only.
     */
    public interface QueueColorPickerCallback {
        /**
         * Called when user selects a color.
         *
         * @param color Selected color as ARGB int
         */
        void onColorSelected(@ColorInt int color);

        /**
         * Called when picker is cancelled.
         */
        void onCancel();
    }

    /**
     * Show queue creation dialog.
     * Allows user to enter queue name and select a color.
     * Buttons: Cancel, Create
     *
     * @param fragment Fragment for context
     * @param callback Called when confirmed or cancelled
     */
    public static void showCreateQueueDialog(
            Fragment fragment,
            QueueNameColorCallback callback) {
        showNameColorDialog(
                fragment.requireActivity(),
                fragment.getString(R.string.queue_creation_title),
                null, // No current name
                callback,
                0, // No current color
                -1); // No queueId (create mode)
    }

    /**
     * Show queue rename/edit dialog.
     * Allows user to change queue name and color, or delete the queue.
     * Buttons: Cancel, Apply (or OK), Delete
     *
     * @param fragment Fragment for context
     * @param queueId ID of queue being edited
     * @param currentName Current queue name to pre-fill
     * @param currentColor Current queue color to show
     * @param callback Called when confirmed, cancelled, or deleted
     */
    public static void showRenameQueueDialog(
            Fragment fragment,
            long queueId,
            String currentName,
            @ColorInt int currentColor,
            QueueNameColorCallback callback) {
        Context context = fragment.requireActivity();
        showNameColorDialog(
                context,
                "Edit Queue",  // TODO: T003 - Add to strings.xml when implementing rename dialog
                currentName,
                callback,
                currentColor,
                queueId); // Pass queueId for edit mode
    }

    /**
     * Show queue deletion confirmation dialog.
     *
     * @param fragment Fragment for context
     * @param queueName Name of queue to delete
     * @param callback Called when confirmed or cancelled
     */
    public static void showDeleteQueueDialog(
            Fragment fragment,
            String queueName,
            QueueDeleteCallback callback) {
        Context context = fragment.requireActivity();
        String message = context.getString(R.string.queue_delete_message, queueName);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.queue_delete_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> callback.onCancel())
                .setPositiveButton(R.string.queue_delete_confirm, (dialog, which) -> callback.onConfirmDelete())
                .show();
    }

    /**
     * Show color picker dialog for queue color selection.
     *
     * @param fragment Fragment for context
     * @param currentColor Currently selected color (optional)
     * @param callback Called when color selected or cancelled
     */
    public static void showColorPickerDialog(
            Fragment fragment,
            @ColorInt int currentColor,
            QueueColorPickerCallback callback) {
        showColorPickerDialog(
                fragment.requireActivity(),
                currentColor,
                callback);
    }

    /**
     * Internal: Show name+color dialog with pre-selected color and optional delete button.
     * If queueId > 0, dialog is in edit mode with Apply and Delete buttons.
     * If queueId <= 0, dialog is in create mode with Create button only.
     */
    @SuppressLint("InflateParams")
    private static void showNameColorDialog(
            Context context,
            String title,
            String currentName,
            QueueNameColorCallback callback,
            @ColorInt int currentColor,
            long queueId) {
        LayoutInflater inflater = LayoutInflater.from(context);
        @SuppressLint("InflateParams")
        android.view.View view = inflater.inflate(R.layout.queue_creation_dialog, null);

        TextInputEditText nameInput = view.findViewById(R.id.queue_name_input);
        GridView colorGrid = view.findViewById(R.id.queue_color_picker);

        if (currentName != null) {
            nameInput.setText(currentName);
            nameInput.selectAll();
        }

        // Setup color picker
        int[] colors = getQueueColors(context);
        QueueColorAdapter colorAdapter = new QueueColorAdapter(context, colors, currentColor);
        colorGrid.setAdapter(colorAdapter);

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel,
                        (dialogInterface, which) -> callback.onCancel());

        // Determine button labels and behavior based on mode (create vs edit)
        final int positiveButtonLabel;
        if (queueId > 0) {
            // Edit mode: show Apply button and Delete button
            positiveButtonLabel = android.R.string.ok; // "OK" / "Apply"
            dialogBuilder.setNeutralButton("Delete", (dialogInterface, which) -> {
                callback.onDelete();
            });
        } else {
            // Create mode: show Create button only
            positiveButtonLabel = R.string.queue_create_button;
        }

        dialogBuilder.setPositiveButton(positiveButtonLabel, (dialogInterface, which) -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(context, R.string.queue_empty_name_error,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (name.length() > 100) {
                name = name.substring(0, 100);
            }
            int selectedColor = colorAdapter.getSelectedColor();
            callback.onConfirm(name, selectedColor);
        });

        AlertDialog dialog = dialogBuilder.show();

        // Adjust dialog for keyboard and position it higher on screen
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            // Position dialog to start higher on screen (5% from top instead of centered)
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            params.y = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.05f);
            dialog.getWindow().setAttributes(params);
        }

        // Only allow confirm when name is non-empty
        colorGrid.setOnItemClickListener((parent, view1, position, id) -> {
            colorAdapter.setSelectedPosition(position);
            colorAdapter.notifyDataSetChanged();
        });
    }

    /**
     * Internal: Show color picker dialog.
     */
    @SuppressLint("InflateParams")
    private static void showColorPickerDialog(
            Context context,
            @ColorInt int currentColor,
            QueueColorPickerCallback callback) {
        LayoutInflater inflater = LayoutInflater.from(context);
        android.view.View view = inflater.inflate(R.layout.queue_color_picker_dialog, null);

        GridView colorGrid = view.findViewById(R.id.queue_color_grid);
        int[] colors = getQueueColors(context);
        QueueColorAdapter colorAdapter = new QueueColorAdapter(context, colors, currentColor);
        colorGrid.setAdapter(colorAdapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.queue_color_picker_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, (dialogInterface, which) -> callback.onCancel())
                .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                    int selectedColor = colorAdapter.getSelectedColor();
                    callback.onColorSelected(selectedColor);
                })
                .show();

        // Adjust dialog for keyboard and position it higher on screen
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            // Position dialog to start higher on screen
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            params.y = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.05f);
            dialog.getWindow().setAttributes(params);
        }

        colorGrid.setOnItemClickListener((parent, view1, position, id) -> {
            colorAdapter.setSelectedPosition(position);
            colorAdapter.notifyDataSetChanged();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
        });
    }

    /**
     * Get array of queue colors from resources.
     */
    private static int[] getQueueColors(Context context) {
        // 12 Material Design colors
        return new int[]{
                0xFFEF5350, // Red
                0xFFEC407A, // Pink
                0xFFAB47BC, // Purple
                0xFF7E57C2, // Deep Purple
                0xFF5C6BC0, // Indigo
                0xFF42A5F5, // Blue
                0xFF29B6F6, // Light Blue
                0xFF26C6DA, // Cyan
                0xFF26A69A, // Teal
                0xFF66BB6A, // Green
                0xFF9CCC65, // Light Green
                0xFFFFB74D  // Orange
        };
    }
}
