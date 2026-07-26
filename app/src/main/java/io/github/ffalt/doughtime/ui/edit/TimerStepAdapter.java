package io.github.ffalt.doughtime.ui.edit;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.entity.TimerStep;
import java.util.List;

public class TimerStepAdapter extends RecyclerView.Adapter<TimerStepAdapter.StepViewHolder> {
    private final List<TimerStep> steps;

    public TimerStepAdapter(List<TimerStep> steps) {
        this.steps = steps;
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
    public int getItemCount() {
        return steps.size();
    }

    public class StepViewHolder extends RecyclerView.ViewHolder {
        private final TextView textStepNumber;
        private final EditText editTitle;
        private final EditText editDuration;
        private final EditText editDescription;
        private final ImageButton buttonRemove;

        public StepViewHolder(@NonNull View itemView) {
            super(itemView);
            textStepNumber = itemView.findViewById(R.id.text_step_number);
            editTitle = itemView.findViewById(R.id.edit_step_title);
            editDuration = itemView.findViewById(R.id.edit_step_duration);
            editDescription = itemView.findViewById(R.id.edit_step_description);
            buttonRemove = itemView.findViewById(R.id.button_remove_step);
        }

        public void bind(TimerStep step, int position) {
            textStepNumber.setText(itemView.getContext().getString(R.string.step_number, position + 1));
            editTitle.setText(step.title);
            editDuration.setText(String.valueOf(step.durationSeconds / 60));
            editDescription.setText(step.description);

            editTitle.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    step.title = s.toString();
                }
            });

            editDuration.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    try {
                        step.durationSeconds = Long.parseLong(s.toString()) * 60;
                    } catch (NumberFormatException ignored) { }
                }
            });

            editDescription.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    step.description = s.toString();
                }
            });

            buttonRemove.setOnClickListener(v -> {
                int adapterPosition = getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }

                steps.remove(adapterPosition);
                notifyItemRemoved(adapterPosition);
                notifyItemRangeChanged(adapterPosition, steps.size() - adapterPosition);
            });
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
