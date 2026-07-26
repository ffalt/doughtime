package io.github.ffalt.doughtime.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.entity.TimerWithSteps;
import com.google.android.material.button.MaterialButton;

public class TimerAdapter extends ListAdapter<TimerWithSteps, TimerAdapter.TimerViewHolder> {

    private final OnTimerClickListener listener;

    public interface OnTimerClickListener {
        void onStartClick(TimerWithSteps timer);

        void onEditClick(TimerWithSteps timer);

        void onDeleteClick(TimerWithSteps timer);

        void onDuplicateClick(TimerWithSteps timer);
    }

    public TimerAdapter(OnTimerClickListener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull TimerWithSteps oldItem, @NonNull TimerWithSteps newItem) {
                return oldItem.timer.id == newItem.timer.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull TimerWithSteps oldItem, @NonNull TimerWithSteps newItem) {
                return oldItem.timer.title.equals(newItem.timer.title)
                        && oldItem.timer.description.equals(newItem.timer.description)
                        && oldItem.steps.size() == newItem.steps.size();
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public TimerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timer, parent, false);
        return new TimerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimerViewHolder holder, int position) {
        TimerWithSteps timer = getItem(position);
        holder.bind(timer, listener);
    }

    public static class TimerViewHolder extends RecyclerView.ViewHolder {
        private final TextView textTitle;
        private final TextView textDescription;
        private final TextView textStepsCount;
        private final MaterialButton buttonStart;
        private final MaterialButton buttonOptions;

        public TimerViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_title);
            textDescription = itemView.findViewById(R.id.text_description);
            textStepsCount = itemView.findViewById(R.id.text_steps_count);
            buttonStart = itemView.findViewById(R.id.button_start);
            buttonOptions = itemView.findViewById(R.id.button_options);
        }

        public void bind(TimerWithSteps timer, OnTimerClickListener listener) {
            textTitle.setText(timer.timer.title);
            textDescription.setText(timer.timer.description);
            textStepsCount.setText(itemView.getContext().getResources().getQuantityString(
                    R.plurals.timer_steps_count,
                    timer.steps.size(),
                    timer.steps.size()
            ));

            buttonStart.setOnClickListener(v -> listener.onStartClick(timer));
            buttonOptions.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(v.getContext(), v);
                popup.getMenuInflater().inflate(R.menu.menu_timer_item, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_edit) {
                        listener.onEditClick(timer);
                        return true;
                    } else if (itemId == R.id.action_duplicate) {
                        listener.onDuplicateClick(timer);
                        return true;
                    } else if (itemId == R.id.action_delete) {
                        listener.onDeleteClick(timer);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
    }
}
