package io.github.ffalt.doughtime.ui.edit;

import android.annotation.SuppressLint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.textfield.TextInputLayout;
import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.entity.TimerStep;
import java.util.Collections;
import java.util.List;

public class TimerStepAdapter extends RecyclerView.Adapter<TimerStepAdapter.StepViewHolder> {
    private static final Object PAYLOAD_STEP_NUMBER = new Object();

    public interface OnStartDragListener {
        void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder);
    }

    private final List<TimerStep> steps;
    private final OnStartDragListener dragListener;

    public TimerStepAdapter(List<TimerStep> steps, OnStartDragListener dragListener) {
        this.steps = steps;
        this.dragListener = dragListener;
    }

    @NonNull
    @Override
    public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timer_step_edit, parent, false);
        return new StepViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
        holder.bind(steps.get(position), position);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_STEP_NUMBER)) {
            holder.bindStepNumber(position);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public int getItemCount() {
        return steps.size();
    }

    public void moveStep(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(steps, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(steps, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
        int first = Math.min(fromPosition, toPosition);
        int count = Math.abs(fromPosition - toPosition) + 1;
        notifyItemRangeChanged(first, count, PAYLOAD_STEP_NUMBER);
    }

    public class StepViewHolder extends RecyclerView.ViewHolder {
        private final TextView textStepNumber;
        private final EditText editTitle;
        private final EditText editDuration;
        private final TextInputLayout layoutDuration;
        private final EditText editDescription;
        private TimerStep currentStep;
        private boolean binding;

        public StepViewHolder(@NonNull View itemView) {
            super(itemView);
            textStepNumber = itemView.findViewById(R.id.text_step_number);
            editTitle = itemView.findViewById(R.id.edit_step_title);
            editDuration = itemView.findViewById(R.id.edit_step_duration);
            layoutDuration = itemView.findViewById(R.id.layout_step_duration);
            editDescription = itemView.findViewById(R.id.edit_step_description);
            ImageButton buttonRemove = itemView.findViewById(R.id.button_remove_step);
            ImageView imageDragHandle = itemView.findViewById(R.id.image_drag_handle);

            editTitle.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (binding || currentStep == null) {
                        return;
                    }
                    currentStep.title = s.toString();
                }
            });

            View.OnClickListener durationClickListener = v -> {
                TimerStep step = currentStep;
                if (step == null) {
                    return;
                }
                DurationPickerSheet.show(itemView.getContext(), step.durationSeconds, durationSeconds -> {
                    step.durationSeconds = durationSeconds;
                    if (currentStep == step) {
                        editDuration.setText(DurationPickerSheet.format(itemView.getContext(), durationSeconds));
                    }
                });
            };
            editDuration.setOnClickListener(durationClickListener);
            layoutDuration.setEndIconOnClickListener(durationClickListener);

            editDescription.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (binding || currentStep == null) {
                        return;
                    }
                    currentStep.description = s.toString();
                }
            });

            buttonRemove.setOnClickListener(v -> {
                int adapterPosition = getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }

                steps.remove(adapterPosition);
                notifyItemRemoved(adapterPosition);
                notifyItemRangeChanged(adapterPosition, steps.size() - adapterPosition, PAYLOAD_STEP_NUMBER);
            });

            setupDragHandle(imageDragHandle);
        }

        @SuppressLint("ClickableViewAccessibility")
        private void setupDragHandle(View dragHandle) {
            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragListener.onStartDrag(this);
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                return false;
            });
        }

        public void bind(TimerStep step, int position) {
            binding = true;
            currentStep = step;
            bindStepNumber(position);
            editTitle.setText(step.title);
            editDuration.setText(DurationPickerSheet.format(itemView.getContext(), step.durationSeconds));
            editDescription.setText(step.description);
            binding = false;
        }

        public void bindStepNumber(int position) {
            textStepNumber.setText(itemView.getContext().getString(R.string.step_number, position + 1));
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
