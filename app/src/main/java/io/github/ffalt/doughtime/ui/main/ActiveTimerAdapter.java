package io.github.ffalt.doughtime.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.service.TimerService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class ActiveTimerAdapter extends RecyclerView.Adapter<ActiveTimerAdapter.ViewHolder> {

    private final List<TimerService.ActiveTimer> timers = new ArrayList<>();
    private final OnActiveTimerClickListener listener;

    public interface OnActiveTimerClickListener {
        void onActiveTimerClick(TimerService.ActiveTimer timer);
    }

    public ActiveTimerAdapter(OnActiveTimerClickListener listener) {
        this.listener = listener;
    }

    public void submitList(Collection<TimerService.ActiveTimer> newTimers) {
        int oldSize = timers.size();
        timers.clear();
        timers.addAll(newTimers);
        int newSize = timers.size();

        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize);
        }
        if (newSize > 0) {
            notifyItemRangeInserted(0, newSize);
        }
    }

    public void notifyTimerUpdated(long timerId) {
        for (int i = 0; i < timers.size(); i++) {
            if (timers.get(i).timer.timer.id == timerId) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_active_timer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(timers.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return timers.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textTitle;
        private final TextView textStep;
        private final TextView textCountdown;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_active_timer_title);
            textStep = itemView.findViewById(R.id.text_active_step_title);
            textCountdown = itemView.findViewById(R.id.text_active_countdown);
        }

        public void bind(TimerService.ActiveTimer activeTimer, OnActiveTimerClickListener listener) {
            String title = activeTimer.timer.timer.title;
            if (!activeTimer.timerRunning && activeTimer.timeLeftInMillis > 0) {
                title = itemView.getContext().getString(R.string.active_timer_paused_title, title);
            }
            textTitle.setText(title);
            textStep.setText(activeTimer.timer.steps.get(activeTimer.currentStepIndex).displayTitle());

            long millis = activeTimer.timeLeftInMillis;
            int hours = (int) (millis / 3600000);
            int minutes = (int) (millis % 3600000 / 60000);
            int seconds = (int) (millis % 60000 / 1000);
            textCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
 
            itemView.setOnClickListener(v -> listener.onActiveTimerClick(activeTimer));
        }
    }
}
