package io.github.ffalt.doughtime.ui.timer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.entity.TimerStep;
import io.github.ffalt.doughtime.data.entity.TimerWithSteps;
import io.github.ffalt.doughtime.service.TimerService;
import io.github.ffalt.doughtime.ui.main.TimerViewModel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import com.google.android.material.appbar.MaterialToolbar;

public class TimerRunActivity extends AppCompatActivity implements TimerService.TimerListener {
    private static final int PREVIEW_DETAILS_LEFT_COLUMN_WIDTH = 24;
    private static final int PREVIEW_DETAILS_RIGHT_COLUMN_WIDTH = 14;

    private TimerService timerService;
    private boolean isBound = false;
    private long timerId;
    private TimerWithSteps timerWithSteps;

    private TextView textStepTitle;
    private TextView textCountdown;
    private TextView textStepDescription;
    private TextView textNextSteps;
    private TextView textPreviousStep;
    private LinearLayout layoutNextStepsItems;
    private LinearLayout layoutAlarmControls;
    private LinearLayout layoutActiveControls;
    private MaterialButton buttonPauseResume;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerService.LocalBinder binder = (TimerService.LocalBinder) service;
            timerService = binder.getService();
            isBound = true;
            timerService.addListener(TimerRunActivity.this);
            
            TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
            if (activeTimer == null) {
                loadTimerAndStart();
            } else {
                timerWithSteps = activeTimer.timer;
                updateUI();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timer_run);

        timerId = getIntent().getLongExtra("TIMER_ID", -1);

        textPreviousStep = findViewById(R.id.text_previous_step);
        textStepTitle = findViewById(R.id.text_current_step_title);
        textCountdown = findViewById(R.id.text_countdown);
        textStepDescription = findViewById(R.id.text_current_step_description);
        textNextSteps = findViewById(R.id.text_next_steps);
        layoutNextStepsItems = findViewById(R.id.layout_next_steps_items);
        layoutAlarmControls = findViewById(R.id.layout_alarm_controls);
        layoutActiveControls = findViewById(R.id.layout_active_controls);
        buttonPauseResume = findViewById(R.id.button_pause_resume);
        MaterialButton buttonStartNext = findViewById(R.id.button_start_next);
        MaterialButton buttonStopAll = findViewById(R.id.button_stop_all);
        MaterialButton buttonSkip = findViewById(R.id.button_skip);
        MaterialButton buttonStop = findViewById(R.id.button_stop);

        buttonPauseResume.setOnClickListener(v -> {
            TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
            if (activeTimer != null) {
                if (activeTimer.timerRunning) {
                    timerService.pauseTimer(timerId);
                } else {
                    timerService.resumeTimer(timerId);
                }
            }
        });

        buttonStop.setOnClickListener(v -> {
            timerService.stopTimer(timerId);
            finish();
        });

        buttonStartNext.setOnClickListener(v -> {
            TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
            if (activeTimer != null) {
                int nextStep = activeTimer.currentStepIndex + 1;
                if (nextStep < timerWithSteps.steps.size()) {
                    timerService.stopAlarmOnly(timerId);
                    timerService.startTimer(timerWithSteps, nextStep);
                    layoutAlarmControls.setVisibility(View.GONE);
                    layoutActiveControls.setVisibility(View.VISIBLE);
                    updateUI();
                } else {
                    timerService.stopTimer(timerId);
                    finish();
                }
            }
        });

        buttonStopAll.setOnClickListener(v -> {
            timerService.stopAllTimers();
            finish();
        });

        findViewById(R.id.button_reset).setOnClickListener(v -> {
            TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
            if (activeTimer != null) {
                timerService.startTimer(timerWithSteps, activeTimer.currentStepIndex);
                updateUI();
            }
        });

        buttonSkip.setOnClickListener(v -> {
            TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
            if (activeTimer != null) {
                int nextStep = activeTimer.currentStepIndex + 1;
                if (nextStep < timerWithSteps.steps.size()) {
                    timerService.startTimer(timerWithSteps, nextStep);
                    updateUI();
                } else {
                    timerService.stopTimer(timerId);
                    finish();
                }
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        startTimerService();
    }

    private void startTimerService() {
        Intent intent = new Intent(this, TimerService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void loadTimerAndStart() {
        TimerViewModel viewModel = new ViewModelProvider(this).get(TimerViewModel.class);
        viewModel.getAllTimers().observe(this, timers -> {
            for (TimerWithSteps t : timers) {
                if (t.timer.id == timerId) {
                    timerWithSteps = t;
                    timerService.startTimer(t, 0);
                    updateUI();
                    break;
                }
            }
        });
    }

    private void updateUI() {
        if (timerWithSteps == null || timerService == null) {
            return;
        }

        TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
        if (activeTimer == null) {
            return;
        }

        int currentIndex = activeTimer.currentStepIndex;
        TimerStep currentStep = timerWithSteps.steps.get(currentIndex);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(timerWithSteps.timer.title);

        textStepTitle.setText(getString(
                R.string.active_timer_step,
                currentIndex + 1,
                currentStep.title
        ));
        textStepDescription.setText(currentStep.description);

        if (currentIndex > 0) {
            textPreviousStep.setVisibility(View.VISIBLE);
            textPreviousStep.setText(getString(
                    R.string.label_previous_step,
                    timerWithSteps.steps.get(currentIndex - 1).title
            ));
        } else {
            textPreviousStep.setVisibility(View.GONE);
        }

        textNextSteps.setVisibility(View.VISIBLE);
        textNextSteps.setText(getString(R.string.label_next_steps));
        renderNextStepItems(buildNextStepItemPreviews(
                timerWithSteps.steps,
                currentIndex,
                activeTimer.timeLeftInMillis,
                System.currentTimeMillis(),
                getString(R.string.label_current_step),
                getString(R.string.label_duration),
                getString(R.string.label_ends_at),
                getString(R.string.label_final_step)
        ));

        long timeLeft = activeTimer.timeLeftInMillis;
        updateCountdown(timeLeft);
        
        if (timeLeft == 0 && !activeTimer.timerRunning) {
            layoutActiveControls.setVisibility(View.GONE);
            layoutAlarmControls.setVisibility(View.VISIBLE);
        } else {
            layoutActiveControls.setVisibility(View.VISIBLE);
            layoutAlarmControls.setVisibility(View.GONE);
            buttonPauseResume.setText(activeTimer.timerRunning ? R.string.action_pause : R.string.action_resume);
        }
    }

    private void updateCountdown(long millisUntilFinished) {
        int hours = (int) (millisUntilFinished / 3600000);
        int minutes = (int) (millisUntilFinished % 3600000 / 60000);
        int seconds = (int) (millisUntilFinished % 60000 / 1000);
        textCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

    private static List<NextStepPreviewItem> buildNextStepItemPreviews(
            List<TimerStep> steps,
            int currentStepIndex,
            long currentStepTimeLeftInMillis,
            long nowInMillis,
            String currentLabel,
            String durationLabel,
            String endsAtLabel,
            String finalStepLabel
    ) {
        List<NextStepPreviewItem> itemPreviews = new ArrayList<>();
        if (steps.isEmpty() || currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            return itemPreviews;
        }

        TimerStep currentStep = steps.get(currentStepIndex);
        long currentStepEndTimeInMillis = nowInMillis + Math.max(currentStepTimeLeftInMillis, 0);
        itemPreviews.add(buildPreviewItem(
                currentLabel + ": " + currentStep.title,
                durationLabel,
                formatDuration(currentStep.durationSeconds),
                endsAtLabel,
                currentStepEndTimeInMillis
        ));

        if (currentStepIndex + 1 >= steps.size()) {
            itemPreviews.add(NextStepPreviewItem.titleOnly(finalStepLabel));
            return itemPreviews;
        }

        long stepStartTimeInMillis = currentStepEndTimeInMillis;
        for (int i = currentStepIndex + 1; i < steps.size(); i++) {
            TimerStep nextStep = steps.get(i);
            long stepEndTimeInMillis = stepStartTimeInMillis + Math.max(nextStep.durationSeconds, 0) * 1000;
            itemPreviews.add(buildPreviewItem(
                    nextStep.title,
                    durationLabel,
                    formatDuration(nextStep.durationSeconds),
                    endsAtLabel,
                    stepEndTimeInMillis
            ));

            stepStartTimeInMillis = stepEndTimeInMillis;
        }
        return itemPreviews;
    }

    private void renderNextStepItems(List<NextStepPreviewItem> itemPreviews) {
        layoutNextStepsItems.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (NextStepPreviewItem itemPreview : itemPreviews) {
            View itemView = inflater.inflate(
                    R.layout.item_next_step_preview,
                    layoutNextStepsItems,
                    false
            );

            TextView titleView = itemView.findViewById(R.id.text_next_step_title);
            LinearLayout detailsLayout = itemView.findViewById(R.id.layout_next_step_details);
            TextView durationView = itemView.findViewById(R.id.text_next_step_duration);
            TextView endsAtView = itemView.findViewById(R.id.text_next_step_ends_at);

            titleView.setText(itemPreview.titleText);
            if (itemPreview.hasDetails()) {
                detailsLayout.setVisibility(View.VISIBLE);
                durationView.setText(itemPreview.leftDetailsText);
                endsAtView.setText(itemPreview.rightDetailsText);
            } else {
                detailsLayout.setVisibility(View.GONE);
            }

            layoutNextStepsItems.addView(itemView);
        }
    }

    private static NextStepPreviewItem buildPreviewItem(
            String titleText,
            String durationLabel,
            String durationText,
            String endsAtLabel,
            long endTimeInMillis
    ) {
        return NextStepPreviewItem.withDetails(
                titleText,
                durationLabel + " " + durationText,
                endsAtLabel + " " + formatClockTime(endTimeInMillis)
        );
    }

    private static final class NextStepPreviewItem {
        private final String titleText;
        private final String leftDetailsText;
        private final String rightDetailsText;

        private NextStepPreviewItem(String titleText, String leftDetailsText, String rightDetailsText) {
            this.titleText = titleText;
            this.leftDetailsText = leftDetailsText;
            this.rightDetailsText = rightDetailsText;
        }

        private static NextStepPreviewItem withDetails(
                String titleText,
                String leftDetailsText,
                String rightDetailsText
        ) {
            return new NextStepPreviewItem(titleText, leftDetailsText, rightDetailsText);
        }

        private static NextStepPreviewItem titleOnly(String titleText) {
            return new NextStepPreviewItem(titleText, "", "");
        }

        private boolean hasDetails() {
            return !leftDetailsText.isEmpty() || !rightDetailsText.isEmpty();
        }
    }

    static String formatDuration(long durationSeconds) {
        long safeDurationSeconds = Math.max(durationSeconds, 0);
        long totalMinutes = safeDurationSeconds / 60;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
    }

    static String formatClockTime(long timeInMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
        );
    }

    @Override
    public void onTick(long tickTimerId, long millisUntilFinished) {
        if (this.timerId == tickTimerId) {
            updateCountdown(millisUntilFinished);
        }
    }

    @Override
    public void onFinish(long finishTimerId) {
        if (this.timerId == finishTimerId) {
            if (timerService != null && timerService.getActiveTimer(finishTimerId) == null) {
                finish();
            } else {
                updateUI();
            }
        }
    }

    @Override
    public void onStatusChanged(long changedTimerId) {
        if (this.timerId == changedTimerId) {
            updateUI();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            timerService.removeListener(this);
            unbindService(connection);
            isBound = false;
        }
    }
}
