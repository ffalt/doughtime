package io.github.ffalt.doughtime.ui.edit;

import android.content.Context;
import android.view.View;
import android.widget.NumberPicker;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import io.github.ffalt.doughtime.R;
import java.util.Locale;
import java.util.Objects;

public class DurationPickerSheet {
    private static final int MAX_HOURS = 99;

    public interface OnDurationSelectedListener {
        void onDurationSelected(long durationSeconds);
    }

    private DurationPickerSheet() {
    }

    private static <T extends View> T requireView(BottomSheetDialog dialog, int viewId) {
        return Objects.requireNonNull(dialog.findViewById(viewId));
    }

    public static void show(Context context, long durationSeconds, OnDurationSelectedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setContentView(R.layout.sheet_duration_picker);

        NumberPicker pickerHours = requireView(dialog, R.id.picker_hours);
        NumberPicker pickerMinutes = requireView(dialog, R.id.picker_minutes);

        long totalMinutes = Math.max(durationSeconds, 0) / 60;
        pickerHours.setMinValue(0);
        pickerHours.setMaxValue(MAX_HOURS);
        pickerHours.setValue((int) Math.min(totalMinutes / 60, MAX_HOURS));

        String[] minuteLabels = new String[60];
        for (int minute = 0; minute < minuteLabels.length; minute++) {
            minuteLabels[minute] = String.format(Locale.getDefault(), "%02d", minute);
        }
        pickerMinutes.setMinValue(0);
        pickerMinutes.setMaxValue(minuteLabels.length - 1);
        pickerMinutes.setDisplayedValues(minuteLabels);
        pickerMinutes.setValue((int) (totalMinutes % 60));

        requireView(dialog, R.id.button_cancel).setOnClickListener(v -> dialog.dismiss());
        requireView(dialog, R.id.button_ok).setOnClickListener(v -> {
            pickerHours.clearFocus();
            pickerMinutes.clearFocus();
            listener.onDurationSelected(pickerHours.getValue() * 3600L + pickerMinutes.getValue() * 60L);
            dialog.dismiss();
        });

        dialog.show();
    }

    public static String format(Context context, long durationSeconds) {
        long totalMinutes = Math.max(durationSeconds, 0) / 60;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0 && minutes > 0) {
            return context.getString(R.string.duration_hours_minutes, hours, minutes);
        }
        if (hours > 0) {
            return context.getString(R.string.duration_hours, hours);
        }
        return context.getString(R.string.duration_minutes, minutes);
    }
}
